package com.bucks.blutendance.call

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages call recording using the low-level [AudioRecord] API, writing
 * raw PCM data into a standard WAV file.
 *
 * Why AudioRecord instead of MediaRecorder?
 *  – MediaRecorder's AAC encoder is often silenced or produces empty
 *    frames during an active phone call on many OEMs.
 *  – AudioRecord reads raw samples from the audio HAL, which is more
 *    likely to succeed.
 *
 * Audio source selection:
 *  1. If the Accessibility Service is enabled → try VOICE_CALL first
 *     (many ROMs allow it for accessibility-privileged apps).
 *  2. Then VOICE_COMMUNICATION.
 *  3. Then MIC (always works, picks up your voice + loudspeaker bleed).
 *
 * Recordings are saved in the app-internal files directory
 * (`context.filesDir / "recordings"`).
 */
object CallRecordingManager {

    private const val TAG = "CallRecording"
    private const val RECORDINGS_DIR = "recordings"

    // WAV parameters
    private const val SAMPLE_RATE = 16000          // 16 kHz – good for voice
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentFile: File? = null

    var isRecording: Boolean = false
        private set

    /** Which audio source was used for the current recording (for diagnostics). */
    var activeSource: Int = -1
        private set

    /** Human-readable label for the active source. */
    fun activeSourceName(): String = when (activeSource) {
        MediaRecorder.AudioSource.VOICE_CALL          -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.MIC                 -> "MIC"
        else -> "NONE"
    }

    /* ================================================================ */
    /*  Start / Stop                                                     */
    /* ================================================================ */

    fun startRecording(context: Context, callerInfo: String) {
        if (isRecording) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted – skipping")
            return
        }

        val dir = getRecordingsDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = callerInfo.replace(Regex("[^a-zA-Z0-9+_-]"), "_")
        val file = File(dir, "call_${safeName}_$timestamp.wav")

        // Build the ordered list of audio sources to try.
        val sources = buildSourceList()

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        for (source in sources) {
            try {
                @Suppress("MissingPermission")
                val ar = AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
                if (ar.state != AudioRecord.STATE_INITIALIZED) {
                    ar.release()
                    Log.w(TAG, "AudioRecord init failed for source $source")
                    continue
                }

                ar.startRecording()
                if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    ar.stop()
                    ar.release()
                    Log.w(TAG, "AudioRecord could not start for source $source")
                    continue
                }

                // Success – kick off the PCM → WAV writer on a background thread
                audioRecord = ar
                currentFile = file
                activeSource = source
                isRecording = true

                recordingThread = Thread({
                    writeWav(ar, file, bufferSize)
                }, "CallRecorder").apply { start() }

                Log.d(TAG, "Recording started (source=${activeSourceName()}) → ${file.name}")
                return

            } catch (e: Exception) {
                Log.w(TAG, "Source $source threw: ${e.message}")
            }
        }

        Log.e(TAG, "All audio sources failed – recording not started")
        cleanup()
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false // signal thread to stop

        try {
            recordingThread?.join(3000)
        } catch (_: InterruptedException) { }

        try {
            audioRecord?.apply { stop(); release() }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        Log.d(TAG, "Recording stopped → ${currentFile?.name}")
        cleanup()
    }

    /* ================================================================ */
    /*  Query / Delete / Save                                            */
    /* ================================================================ */

    fun getRecordings(context: Context): List<File> {
        val dir = getRecordingsDir(context)
        return dir.listFiles()
            ?.filter { it.isFile && (it.extension == "wav" || it.extension == "m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean = try { file.delete() }
    catch (e: Exception) { Log.e(TAG, "Delete failed: ${file.name}", e); false }

    fun deleteAllRecordings(context: Context) {
        getRecordingsDir(context).listFiles()?.forEach { it.delete() }
    }

    fun totalSizeBytes(context: Context): Long =
        getRecordings(context).sumOf { it.length() }

    /**
     * Copy a recording to the device's public Music/CallRecordings folder.
     */
    fun saveToDevice(context: Context, file: File): Boolean {
        val mimeType = if (file.extension == "wav") "audio/wav" else "audio/mp4"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/CallRecordings")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return false

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                Log.d(TAG, "Saved to device: ${file.name}")
                true
            } else {
                @Suppress("DEPRECATION")
                val musicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC)
                val destDir = File(musicDir, "CallRecordings")
                if (!destDir.exists()) destDir.mkdirs()
                val dest = File(destDir, file.name)
                file.inputStream().use { inp ->
                    dest.outputStream().use { out -> inp.copyTo(out) }
                }
                Log.d(TAG, "Saved to device: ${dest.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save ${file.name}", e)
            false
        }
    }

    /* ================================================================ */
    /*  Internal helpers                                                  */
    /* ================================================================ */

    /**
     * Decide which audio sources to attempt:
     *  - If the accessibility service is running, try VOICE_CALL first
     *    (elevated privilege on many ROMs).
     *  - Always include VOICE_COMMUNICATION and MIC as fallbacks.
     */
    private fun buildSourceList(): List<Int> {
        val list = mutableListOf<Int>()
        if (CallRecordAccessibilityService.isRunning) {
            list += MediaRecorder.AudioSource.VOICE_CALL
        }
        list += MediaRecorder.AudioSource.VOICE_COMMUNICATION
        list += MediaRecorder.AudioSource.MIC
        return list
    }

    /**
     * Continuously read PCM from [AudioRecord] and write to a WAV [file].
     * Runs on a background thread; exits when [isRecording] becomes false.
     */
    private fun writeWav(ar: AudioRecord, file: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var totalDataBytes = 0L

        try {
            FileOutputStream(file).use { fos ->
                // Write a placeholder WAV header (44 bytes) — we'll patch sizes later
                fos.write(ByteArray(44))

                while (isRecording) {
                    val read = ar.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        fos.write(buffer, 0, read)
                        totalDataBytes += read
                    }
                }
            }

            // Patch the WAV header with the correct sizes
            patchWavHeader(file, totalDataBytes)

        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV data", e)
        }
    }

    /**
     * Write a standard 44-byte RIFF/WAV header at position 0 of [file].
     */
    private fun patchWavHeader(file: File, dataSize: Long) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalFileSize = dataSize + 36  // 44 - 8

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeIntLE(totalFileSize.toInt())
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeIntLE(16)                     // sub-chunk size
            raf.writeShortLE(1)                    // PCM format
            raf.writeShortLE(channels)
            raf.writeIntLE(SAMPLE_RATE)
            raf.writeIntLE(byteRate)
            raf.writeShortLE(blockAlign)
            raf.writeShortLE(bitsPerSample)
            raf.writeBytes("data")
            raf.writeIntLE(dataSize.toInt())
        }
    }

    /** Write a 32-bit int in little-endian order. */
    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    /** Write a 16-bit short in little-endian order. */
    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun getRecordingsDir(context: Context): File {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cleanup() {
        audioRecord = null
        recordingThread = null
        currentFile = null
        activeSource = -1
    }
}
