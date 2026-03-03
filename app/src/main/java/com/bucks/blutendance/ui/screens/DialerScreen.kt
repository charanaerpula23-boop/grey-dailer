package com.bucks.blutendance.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bucks.blutendance.SuperUserActivity
import com.bucks.blutendance.call.ContactResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SUPER_USER_CODE = "0909090909"

/* ── T9 digit → letters mapping for name matching ──────────────── */
private val t9Map = mapOf(
    '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
    '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
)

private fun nameToT9(name: String): String = buildString {
    for (ch in name.lowercase()) {
        val digit = t9Map.entries.find { ch in it.value }?.key
        if (digit != null) append(digit)
    }
}

private data class DialerContact(
    val name: String,
    val number: String,
    val t9: String
)

/* ── Call log entry ────────────────────────────────────────────── */
private data class RecentCall(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
)

/* ── Loaders ───────────────────────────────────────────────────── */

private fun loadDialerContacts(context: Context): List<DialerContact> {
    val result = mutableListOf<DialerContact>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED
    ) return result

    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )
    cursor?.use {
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val seen = mutableSetOf<String>()
        while (it.moveToNext()) {
            val name = it.getString(nameIdx) ?: "Unknown"
            val number = it.getString(numIdx) ?: continue
            if (seen.add("$name-$number")) {
                result.add(DialerContact(name, number, nameToT9(name)))
            }
        }
    }
    return result
}

@SuppressLint("MissingPermission")
private fun loadRecentCalls(context: Context): List<RecentCall> {
    val entries = mutableListOf<RecentCall>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
        != PackageManager.PERMISSION_GRANTED
    ) return entries

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
            while (it.moveToNext() && count < 50) {
                val number = it.getString(numIdx) ?: "Unknown"
                val cachedName = it.getString(nameIdx)
                val resolvedName = cachedName ?: ContactResolver.resolveContactName(context, number)
                entries.add(
                    RecentCall(number, resolvedName, it.getInt(typeIdx), it.getLong(dateIdx), it.getLong(durIdx))
                )
                count++
            }
        }
    } catch (_: Exception) { }
    return entries
}

private fun fmtDuration(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

/* ================================================================== */
/*  Dialer Screen (with integrated call history)                       */
/* ================================================================== */

@Composable
fun DialerScreen() {
    var phoneNumber by remember { mutableStateOf("") }
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<DialerContact>>(emptyList()) }
    var recentCalls by remember { mutableStateOf<List<RecentCall>>(emptyList()) }

    LaunchedEffect(Unit) {
        contacts = loadDialerContacts(context)
        recentCalls = loadRecentCalls(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && phoneNumber.isNotEmpty()) placeCall(context, phoneNumber)
    }

    val matchingContacts = remember(phoneNumber, contacts) {
        if (phoneNumber.length < 2) emptyList()
        else {
            val digitsOnly = phoneNumber.filter { it.isDigit() }
            contacts.filter { c ->
                val norm = c.number.replace(Regex("[^0-9+]"), "")
                norm.contains(digitsOnly) || c.t9.contains(digitsOnly)
            }.take(5)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Number display at top ───────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = phoneNumber.ifEmpty { " " },
                fontSize = if (phoneNumber.length > 12) 26.sp else 34.sp,
                fontWeight = FontWeight.Light,
                color = if (phoneNumber.isEmpty())
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (phoneNumber.isNotEmpty()) {
                IconButton(
                    onClick = { phoneNumber = phoneNumber.dropLast(1) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── Create / Add to contact actions ─────────────
        AnimatedVisibility(
            visible = phoneNumber.length >= 3 && matchingContacts.isEmpty(),
            enter = fadeIn(), exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    onClick = {
                        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PersonAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create contact", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    onClick = {
                        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PersonAddAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to contact", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Matching contacts ───────────────────────────
        AnimatedVisibility(
            visible = matchingContacts.isNotEmpty(),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                tonalElevation = 1.dp
            ) {
                LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                    items(matchingContacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    phoneNumber = contact.number.replace(Regex("[^0-9+]"), "")
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(Modifier.size(36.dp), CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(contact.name.firstOrNull()?.uppercase() ?: "?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(contact.number, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), maxLines = 1)
                            }
                            Icon(
                                Icons.Filled.Call, "Call", tint = Color(0xFF4CAF50),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                                            placeCall(context, contact.number)
                                    }
                            )
                        }
                        if (contact != matchingContacts.last()) {
                            HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // ── Recent calls list (always visible between header and keypad) ─
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = "Recent Calls",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (recentCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recent calls", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            } else {
                LazyColumn {
                    items(recentCalls) { entry ->
                        RecentCallRow(entry) {
                            phoneNumber = entry.number.replace(Regex("[^0-9+]"), "")
                        }
                    }
                }
            }
        }

        // ── Keypad (always visible at bottom above call button) ─
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val keys = listOf(
                listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                listOf("*" to "", "0" to "+", "#" to "")
            )
            keys.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        DialerKey(digit, letters) { phoneNumber += digit }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Bottom action row: Call button only ────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Call button
            Surface(
                onClick = {
                    if (phoneNumber.isNotEmpty()) {
                        if (phoneNumber.replace(" ", "").replace("-", "") == SUPER_USER_CODE) {
                            context.startActivity(Intent(context, SuperUserActivity::class.java))
                            phoneNumber = ""
                            return@Surface
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            placeCall(context, phoneNumber)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    }
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color(0xFF4CAF50),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Call, "Call", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/* ── Recent call row ───────────────────────────────────────────── */

@Composable
private fun RecentCallRow(entry: RecentCall, onClick: () -> Unit) {
    val icon = when (entry.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.Filled.CallMade
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.Filled.CallMissed
        else -> Icons.Filled.Call
    }
    val tint = when (entry.type) {
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Color(0xFFF44336)
        CallLog.Calls.INCOMING_TYPE -> Color(0xFF4CAF50)
        CallLog.Calls.OUTGOING_TYPE -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.onBackground
    }
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, "Call type", tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name ?: entry.number,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (entry.type == CallLog.Calls.MISSED_TYPE) Color(0xFFF44336) else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                if (entry.name != null) {
                    Text(entry.number, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                }
                Text(dateFmt.format(Date(entry.date)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                if (entry.duration > 0) {
                    Text(" · ${fmtDuration(entry.duration)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            }
        }
        // Call icon
        Icon(
            Icons.Filled.Call, "Call back", tint = Color(0xFF4CAF50),
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        placeCall(context, entry.number)
                    }
                }
        )
    }
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

/* ── Dial-pad key ──────────────────────────────────────────────── */

@Composable
private fun DialerKey(digit: String, letters: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() }
    ) {
        Text(digit, fontSize = 28.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
        if (letters.isNotEmpty()) {
            Text(letters, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), letterSpacing = 2.sp)
        }
    }
}

/* ── Place call ────────────────────────────────────────────────── */

@SuppressLint("MissingPermission")
private fun placeCall(context: Context, number: String) {
    val uri = Uri.fromParts("tel", number, null)
    try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(uri, Bundle())
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot place call: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
