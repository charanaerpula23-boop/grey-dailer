package com.bucks.blutendance

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bucks.blutendance.call.CallRecordingManager
import com.bucks.blutendance.call.RecordingPrefs
import com.bucks.blutendance.ui.theme.FirstappTheme
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hidden "Super User" settings screen accessible by dialing 0909090909.
 * Contains call recording toggle and a list of saved recordings
 * (stored inside the app's internal directory).
 */
class SuperUserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirstappTheme {
                SuperUserScreen(onBack = { finish() })
            }
        }
    }
}

/* ── Colours ─────────────────────────────────────────────────────── */
private val AccentBlue = Color(0xFF1A73E8)
private val RecordRed  = Color(0xFFF44336)
private val BgGrey     = Color(0xFFF8F9FA)
private val CardBg     = Color.White
private val TextDark   = Color(0xFF202124)
private val TextLight  = Color(0xFF5F6368)

@Composable
private fun SuperUserScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var recordingEnabled by remember { mutableStateOf(RecordingPrefs.isRecordingEnabled(context)) }
    var recordings by remember { mutableStateOf(CallRecordingManager.getRecordings(context)) }
    var showDeleteAll by remember { mutableStateOf(false) }

    // Currently playing file
    var playingFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playProgress by remember { mutableFloatStateOf(0f) }

    // Cleanup player on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    // Progress tracker
    LaunchedEffect(playingFile) {
        while (playingFile != null && mediaPlayer != null) {
            try {
                val mp = mediaPlayer ?: break
                if (mp.isPlaying) {
                    playProgress = mp.currentPosition.toFloat() / mp.duration.toFloat()
                }
            } catch (_: Exception) { break }
            delay(200)
        }
    }

    fun playFile(file: File) {
        mediaPlayer?.release()
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                playingFile = null
                playProgress = 0f
            }
        }
        mediaPlayer = mp
        playingFile = file
    }

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        playingFile = null
        playProgress = 0f
    }

    fun deleteFile(file: File) {
        if (playingFile == file) stopPlayback()
        CallRecordingManager.deleteRecording(file)
        recordings = CallRecordingManager.getRecordings(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgGrey)
            .systemBarsPadding()
    ) {
        // ── Top bar ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextDark)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Settings, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Super User Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }

        HorizontalDivider(color = Color(0xFFE0E0E0))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // ── Call Recording Toggle ────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconColor by animateColorAsState(
                            targetValue = if (recordingEnabled) RecordRed else TextLight,
                            animationSpec = tween(300),
                            label = "recIcon"
                        )
                        Icon(
                            Icons.Filled.FiberManualRecord, null,
                            tint = iconColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Call Recording",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            Text(
                                if (recordingEnabled) "All calls will be recorded"
                                else "Recording is off",
                                fontSize = 13.sp,
                                color = TextLight
                            )
                        }
                        Switch(
                            checked = recordingEnabled,
                            onCheckedChange = {
                                recordingEnabled = it
                                RecordingPrefs.setRecordingEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentBlue,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFDADCE0)
                            )
                        )
                    }
                }
            }

            // ── Storage info ─────────────────────────────────────
            item {
                val totalSize = CallRecordingManager.totalSizeBytes(context)
                val sizeText = formatFileSize(totalSize)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Recordings Storage", fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold, color = TextDark)
                            Text(
                                "${recordings.size} recordings · $sizeText",
                                fontSize = 13.sp, color = TextLight
                            )
                            Text(
                                "Stored in app-internal storage only",
                                fontSize = 11.sp, color = TextLight
                            )
                        }
                        if (recordings.isNotEmpty()) {
                            IconButton(onClick = { showDeleteAll = true }) {
                                Icon(Icons.Filled.DeleteSweep, "Delete All",
                                    tint = RecordRed, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // ── Recordings section header ────────────────────────
            if (recordings.isNotEmpty()) {
                item {
                    Text(
                        "Saved Recordings",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextLight,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
            }

            // ── Recordings list ──────────────────────────────────
            if (recordings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recordings yet",
                            fontSize = 15.sp,
                            color = TextLight
                        )
                    }
                }
            }

            items(recordings, key = { it.absolutePath }) { file ->
                RecordingItem(
                    file = file,
                    isPlaying = playingFile == file,
                    progress = if (playingFile == file) playProgress else 0f,
                    onPlay = {
                        if (playingFile == file) stopPlayback()
                        else playFile(file)
                    },
                    onDelete = { deleteFile(file) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Delete all confirmation ──────────────────────────────────
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Delete All Recordings?") },
            text = { Text("This will permanently delete all ${recordings.size} recordings. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    stopPlayback()
                    CallRecordingManager.deleteAllRecordings(context)
                    recordings = CallRecordingManager.getRecordings(context)
                    showDeleteAll = false
                }) {
                    Text("Delete All", color = RecordRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ================================================================== */
/*  Single recording row                                               */
/* ================================================================== */

@Composable
private fun RecordingItem(
    file: File,
    isPlaying: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault()) }
    val displayName = remember(file) {
        // Extract caller info from filename: call_<name>_<timestamp>.m4a
        val base = file.nameWithoutExtension
        val parts = base.removePrefix("call_").split("_")
        if (parts.size >= 3) {
            // Rejoin everything except the last 2 parts (date and time)
            parts.dropLast(2).joinToString(" ").replace("_", " ")
        } else base
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/pause button
                val btnBg by animateColorAsState(
                    targetValue = if (isPlaying) AccentBlue else Color(0xFFF1F3F4),
                    animationSpec = tween(200),
                    label = "playBg"
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(btnBg)
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Play",
                        tint = if (isPlaying) Color.White else TextDark,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark,
                        maxLines = 1
                    )
                    Text(
                        dateFormat.format(Date(file.lastModified())),
                        fontSize = 12.sp,
                        color = TextLight
                    )
                    Text(
                        formatFileSize(file.length()),
                        fontSize = 11.sp,
                        color = TextLight
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete", tint = RecordRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp))
                }
            }

            // ── Progress bar ─────────────────────────────────────
            if (isPlaying) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE8EAED))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentBlue)
                    )
                }
            }
        }
    }
}

/* ================================================================== */
/*  Utility                                                            */
/* ================================================================== */

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024       -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else                -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
