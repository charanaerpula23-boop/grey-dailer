package com.bucks.blutendance.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.PhoneAccount
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bucks.blutendance.SuperUserActivity
import com.bucks.blutendance.call.ContactResolver
import com.bucks.blutendance.call.SuperUserPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var isDialPadExpanded by remember { mutableStateOf(false) }
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

    val normalizedInput = remember(phoneNumber) {
        phoneNumber.replace(Regex("[^0-9+]"), "")
    }

    val hasExactContactMatch = remember(normalizedInput, contacts) {
        if (normalizedInput.isBlank()) false
        else contacts.any { contact ->
            val normalizedContact = contact.number.replace(Regex("[^0-9+]"), "")
            normalizedContact == normalizedInput ||
                normalizedContact.endsWith(normalizedInput) ||
                normalizedInput.endsWith(normalizedContact)
        }
    }

    val defaultSimLabel = remember { getDefaultSimLabel(context) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val screenBg = if (isDark) Color(0xFF000000) else Color(0xFFF5F5F5)
    val panelBg = if (isDark) Color(0xFF3A3A3A) else Color(0xFFFFFFFF)
    val matchCardBg = if (isDark) Color(0xFF212121) else Color(0xFFFFFFFF)
    val primaryText = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1F1F1F)
    val secondaryText = if (isDark) Color(0xFFBDBDBD) else Color(0xFF666666)
    val subtleText = if (isDark) Color(0xFF8A8A8A) else Color(0xFF8A8A8A)
    val handleColor = if (isDark) Color(0xFF8A8A8A) else Color(0xFFBBBBBB)
    val iconTint = if (isDark) Color(0xFFDDDDDD) else Color(0xFF444444)

    fun handleCallAction() {
        if (phoneNumber.isEmpty()) return
        if (SuperUserPrefs.isSuperUserCode(context, phoneNumber)) {
            context.startActivity(Intent(context, SuperUserActivity::class.java))
            phoneNumber = ""
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeCall(context, phoneNumber)
        } else {
            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        top = if (isDialPadExpanded) 16.dp else 18.dp,
                        end = 4.dp,
                        bottom = if (isDialPadExpanded) 4.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phone",
                    fontSize = if (isDialPadExpanded) 28.sp else 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { /* overflow menu placeholder */ }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = secondaryText
                    )
                }
            }

            AnimatedVisibility(
                visible = matchingContacts.isNotEmpty() && phoneNumber.isNotEmpty(),
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = matchCardBg
                ) {
                    LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                        items(matchingContacts) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        phoneNumber = contact.number.replace(Regex("[^0-9+]"), "")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        contact.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        contact.number,
                                        fontSize = 12.sp,
                                        color = secondaryText,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    Icons.Filled.Call,
                                    "Call",
                                    tint = Color(0xFF34A853),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                                placeCall(context, contact.number)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            if (recentCalls.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recent calls", color = subtleText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = if (isDialPadExpanded) 420.dp else 90.dp)
                ) {
                    items(recentCalls) { entry ->
                        RecentCallRow(entry) {
                            phoneNumber = entry.number.replace(Regex("[^0-9+]"), "")
                            isDialPadExpanded = true
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isDialPadExpanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                onClick = { isDialPadExpanded = true },
                shape = CircleShape,
                color = Color(0xFF34A853),
                modifier = Modifier.size(56.dp),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Dialpad, "Open dial pad", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = isDialPadExpanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = panelBg,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(handleColor)
                    )

                    Text(
                        text = phoneNumber.ifEmpty { " " },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryText,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp)
                    )

                    if (normalizedInput.length >= 3 && !hasExactContactMatch) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = {
                                    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, normalizedInput)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    tint = Color(0xFF34A853),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Create contact", color = primaryText)
                            }

                            Spacer(Modifier.width(8.dp))

                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, normalizedInput)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.PersonAddAlt,
                                    contentDescription = null,
                                    tint = Color(0xFF34A853),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Add to contact", color = primaryText)
                            }
                        }
                    }

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
                                .padding(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { (digit, letters) ->
                                DialerKey(digit, letters) { phoneNumber += digit }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isDialPadExpanded = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.Dialpad,
                                "Close dial pad",
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Surface(
                            onClick = { handleCallAction() },
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = Color(0xFF34A853),
                            shadowElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Call, "Call", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }

                        IconButton(
                            onClick = { phoneNumber = phoneNumber.dropLast(1) },
                            enabled = phoneNumber.isNotEmpty(),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = if (phoneNumber.isNotEmpty()) iconTint else secondaryText.copy(alpha = 0.55f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (!defaultSimLabel.isNullOrEmpty()) {
                        Text(
                            text = defaultSimLabel,
                            fontSize = 11.sp,
                            color = secondaryText
                        )
                    }
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
    val dateFmt = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val subtleColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF888888)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, "Call type", tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name ?: entry.number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (entry.type == CallLog.Calls.MISSED_TYPE) Color(0xFFF44336) else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.name != null) {
                    Text(
                        entry.number,
                        fontSize = 13.sp,
                        color = subtleColor,
                        maxLines = 1
                    )
                }
            }
        }
        // Date / time on right
        Column(horizontalAlignment = Alignment.End) {
            val now = System.currentTimeMillis()
            val dayDiff = (now - entry.date) / (1000 * 60 * 60 * 24)
            val dateStr = when {
                dayDiff < 1 -> timeFmt.format(Date(entry.date))
                dayDiff < 2 -> "Yesterday"
                else -> dateFmt.format(Date(entry.date))
            }
            Text(
                dateStr,
                fontSize = 13.sp,
                color = subtleColor
            )
        }
        Spacer(Modifier.width(10.dp))
        // Info icon
        Icon(
            Icons.Filled.Info,
            "Details",
            tint = subtleColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/* ── Dial-pad key ──────────────────────────────────────────────── */

@Composable
private fun DialerKey(digit: String, letters: String, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val digitColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1F1F1F)
    val lettersColor = if (isDark) Color(0xFFAFAFAF) else Color(0xFF6F6F6F)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(80.dp)
            .height(56.dp)
            .clickable { onClick() }
    ) {
        Text(digit, fontSize = 32.sp, fontWeight = FontWeight.Light, color = digitColor)
        if (letters.isNotEmpty()) {
            Text(letters, fontSize = 9.sp, color = lettersColor, letterSpacing = 1.5.sp)
        }
    }
}

/* ── Place call ────────────────────────────────────────────────── */

private fun getDefaultSimLabel(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
    return try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = tm.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL) ?: return null
        val account = tm.getPhoneAccount(handle) ?: return null
        account.label?.toString()
    } catch (_: Exception) {
        null
    }
}

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
