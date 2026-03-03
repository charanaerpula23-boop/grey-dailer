package com.bucks.blutendance.call

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages call recording using [MediaRecorder].
 *
 * Recordings are saved in the app-internal files directory
 * (`context.filesDir / "recordings"`) so they are NOT visible in
 * the device file manager or other apps.
 *
 * Uses VOICE_COMMUNICATION audio source which captures the call audio
 * on most devices (both uplink and downlink) when the app is the
 * active InCallService.
 */
object CallRecordingManager {

    private const val TAG = "CallRecording"
    private const val RECORDINGS_DIR = "recordings"

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    var isRecording: Boolean = false
        private set

    /**
     * Start recording. Must be called when a call becomes ACTIVE
     * and recording is enabled in settings.
     */
    fun startRecording(context: Context, callerInfo: String) {
        if (isRecording) return

        try {
            val dir = getRecordingsDir(context)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = callerInfo.replace(Regex("[^a-zA-Z0-9+_-]"), "_")
            val file = File(dir, "call_${safeName}_$timestamp.m4a")

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                // Try VOICE_CALL first (captures both sides on some devices),
                // fall back to MIC if not allowed (non-system app).
                val source = try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    MediaRecorder.AudioSource.VOICE_CALL
                } catch (_: Exception) {
                    // Reset recorder after failed setAudioSource
                    reset()
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    MediaRecorder.AudioSource.MIC
                }
                Log.d(TAG, "Using audio source: $source")
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mr
            currentFile = file
            isRecording = true
            Log.d(TAG, "Recording started → ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
        }
    }

    /**
     * Stop recording and finalise the file.
     */
    fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.apply {
                stop()
                release()
            }
            Log.d(TAG, "Recording stopped → ${currentFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            cleanup()
        }
    }

    /**
     * Returns all saved recording files sorted newest-first.
     */
    fun getRecordings(context: Context): List<File> {
        val dir = getRecordingsDir(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Delete a specific recording file.
     */
    fun deleteRecording(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete ${file.name}", e)
            false
        }
    }

    /**
     * Delete all recordings.
     */
    fun deleteAllRecordings(context: Context) {
        getRecordingsDir(context).listFiles()?.forEach { it.delete() }
    }

    /**
     * Total size of all recordings in bytes.
     */
    fun totalSizeBytes(context: Context): Long {
        return getRecordings(context).sumOf { it.length() }
    }

    /* ── Internal ─────────────────────────────────────────────── */

    private fun getRecordingsDir(context: Context): File {
        val dir = File(context.filesDir, RECORDINGS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cleanup() {
        recorder = null
        currentFile = null
        isRecording = false
    }
}
