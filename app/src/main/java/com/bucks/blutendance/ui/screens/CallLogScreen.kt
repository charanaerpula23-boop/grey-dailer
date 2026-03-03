package com.bucks.blutendance.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bucks.blutendance.call.ContactResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/*  Data                                                               */
/* ------------------------------------------------------------------ */

data class CallLogEntry(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
)

/* ------------------------------------------------------------------ */
/*  Screen                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun CallLogScreen() {
    val context = LocalContext.current
    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) callLogs = loadCallLog(context)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            callLogs = loadCallLog(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Recent Calls",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        when {
            !hasPermission -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Call log permission required")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }) { Text("Grant Permission") }
                    }
                }
            }

            callLogs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No recent calls",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            else -> {
                LazyColumn {
                    items(callLogs) { entry ->
                        CallLogItem(entry = entry, onClick = {
                            placeCallFromLog(context, entry.number)
                        })
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Single call-log row                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun CallLogItem(entry: CallLogEntry, onClick: () -> Unit) {
    val icon = when (entry.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade
        CallLog.Calls.MISSED_TYPE   -> Icons.Filled.CallMissed
        CallLog.Calls.REJECTED_TYPE -> Icons.Filled.CallMissed
        else                        -> Icons.Filled.Call
    }

    val tint = when (entry.type) {
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Color(0xFFF44336)
        CallLog.Calls.INCOMING_TYPE                             -> Color(0xFF4CAF50)
        CallLog.Calls.OUTGOING_TYPE                             -> Color(0xFF2196F3)
        else                                                    -> MaterialTheme.colorScheme.onBackground
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = "Call type", tint = tint, modifier = Modifier.size(24.dp))

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name ?: entry.number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (entry.type == CallLog.Calls.MISSED_TYPE) Color(0xFFF44336)
                else MaterialTheme.colorScheme.onBackground
            )
            if (entry.name != null) {
                Text(
                    text = entry.number,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = dateFormat.format(Date(entry.date)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            if (entry.duration > 0) {
                Text(
                    text = formatDuration(entry.duration),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@SuppressLint("MissingPermission")
private fun loadCallLog(context: Context): List<CallLogEntry> {
    val entries = mutableListOf<CallLogEntry>()
    try {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null, null,
            CallLog.Calls.DATE + " DESC"
        )

        cursor?.use {
            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            var count = 0
            while (it.moveToNext() && count < 100) {
                val number = it.getString(numIdx) ?: "Unknown"
                val cachedName = it.getString(nameIdx)
                // If the system has no cached name, look it up from contacts
                val resolvedName = cachedName ?: ContactResolver.resolveContactName(context, number)
                entries.add(
                    CallLogEntry(
                        number = number,
                        name = resolvedName,
                        type = it.getInt(typeIdx),
                        date = it.getLong(dateIdx),
                        duration = it.getLong(durIdx)
                    )
                )
                count++
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return entries
}

@SuppressLint("MissingPermission")
private fun placeCallFromLog(context: Context, number: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val uri = Uri.fromParts("tel", number, null)
    try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(uri, Bundle())
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Cannot place call: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
