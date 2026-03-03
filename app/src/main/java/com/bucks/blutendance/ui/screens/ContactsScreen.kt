package com.bucks.blutendance.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ */
/*  Data                                                               */
/* ------------------------------------------------------------------ */

data class Contact(val id: Long, val name: String, val phoneNumber: String)

/* ── Avatar colour palette (consistent per-initial) ──────────────── */
private val avatarColors = listOf(
    Color(0xFF1A73E8), Color(0xFF34A853), Color(0xFFEA4335),
    Color(0xFFFBBC05), Color(0xFF9C27B0), Color(0xFF00BCD4),
    Color(0xFFFF5722), Color(0xFF607D8B), Color(0xFF3F51B5),
    Color(0xFF009688), Color(0xFFE91E63), Color(0xFF795548)
)

private fun avatarColor(name: String): Color {
    val ch = name.firstOrNull()?.uppercaseChar() ?: 'A'
    return avatarColors[ch.code % avatarColors.size]
}

/* ================================================================== */
/*  Screen                                                             */
/* ================================================================== */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) contacts = loadContacts(context)
    }

    val createContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasPermission) contacts = loadContacts(context)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            contacts = loadContacts(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val filtered = remember(contacts, searchQuery) {
        if (searchQuery.isEmpty()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery)
        }
    }

    // Group contacts by first letter for section headers
    val grouped = remember(filtered) {
        filtered.groupBy { contact ->
            val ch = contact.name.firstOrNull()?.uppercaseChar() ?: '#'
            if (ch.isLetter()) ch.toString() else "#"
        }.toSortedMap()
    }

    val sectionKeys = remember(grouped) { grouped.keys.toList() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Theme-adaptive colours
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val screenBg = if (isDark) Color(0xFF000000) else Color(0xFFF5F5F5)
    val primaryText = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1F1F1F)
    val secondaryText = if (isDark) Color(0xFFBDBDBD) else Color(0xFF666666)
    val searchBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)
    val sectionHeaderColor = if (isDark) Color(0xFF34A853) else Color(0xFF1A73E8)
    val scrubberText = if (isDark) Color(0xFF9E9E9E) else Color(0xFF888888)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──────────────────────────────────────────
            if (!isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 18.dp, end = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contacts",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Filled.Search, "Search", tint = secondaryText)
                    }
                    IconButton(onClick = { /* overflow placeholder */ }) {
                        Icon(Icons.Filled.MoreVert, "More", tint = secondaryText)
                    }
                }
            } else {
                // ── Search bar ──────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search contacts\u2026") },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = secondaryText) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, "Clear", tint = secondaryText)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = searchBg,
                            unfocusedContainerColor = searchBg,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.Filled.Close, "Cancel", tint = secondaryText)
                    }
                }
            }

            // ── Contact count ───────────────────────────────────
            Text(
                text = "${filtered.size} contacts",
                fontSize = 13.sp,
                color = secondaryText,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
            )

            if (!hasPermission) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Contacts permission required", color = primaryText)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }) { Text("Grant Permission") }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    // ── Main list with sticky headers ───────────
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        grouped.forEach { (letter, contactsInSection) ->
                            stickyHeader(key = "header_$letter") {
                                Text(
                                    text = letter,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sectionHeaderColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(screenBg)
                                        .padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }

                            items(contactsInSection, key = { "${it.id}-${it.phoneNumber}" }) { contact ->
                                ContactItem(
                                    contact = contact,
                                    primaryText = primaryText,
                                    secondaryText = secondaryText,
                                    onClick = { selectedContact = contact }
                                )
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }

                    // ── Alphabet fast scroller on right side ────
                    if (sectionKeys.size > 5 && !isSearchActive) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 2.dp, top = 8.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val alphabet = ('A'..'Z').map { it.toString() } + listOf("#")
                            alphabet.forEach { letter ->
                                Text(
                                    text = letter,
                                    fontSize = 10.sp,
                                    fontWeight = if (letter in sectionKeys) FontWeight.Bold else FontWeight.Normal,
                                    color = if (letter in sectionKeys) scrubberText else scrubberText.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .clickable {
                                            if (letter in sectionKeys) {
                                                var targetIdx = 0
                                                for ((key, items) in grouped) {
                                                    if (key == letter) break
                                                    targetIdx += 1 + items.size
                                                }
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(targetIdx)
                                                }
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── FAB: Create new contact ─────────────────────────────
        if (hasPermission) {
            FloatingActionButton(
                onClick = {
                    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                    }
                    createContactLauncher.launch(intent)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
                containerColor = Color(0xFF34A853),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Create Contact")
            }
        }

        // ── Contact detail bottom sheet ─────────────────────────
        AnimatedVisibility(
            visible = selectedContact != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedContact?.let { contact ->
                ContactDetailSheet(
                    contact = contact,
                    isDark = isDark,
                    onDismiss = { selectedContact = null },
                    onCall = {
                        placeCallFromContacts(context, contact.phoneNumber)
                        selectedContact = null
                    },
                    onMessage = {
                        val smsUri = Uri.parse("smsto:${contact.phoneNumber}")
                        context.startActivity(Intent(Intent.ACTION_SENDTO, smsUri))
                        selectedContact = null
                    },
                    onEdit = {
                        val intent = Intent(Intent.ACTION_EDIT).apply {
                            data = Uri.withAppendedPath(
                                ContactsContract.Contacts.CONTENT_URI,
                                contact.id.toString()
                            )
                        }
                        context.startActivity(intent)
                        selectedContact = null
                    },
                    onShare = {
                        val shareText = "${contact.name}\n${contact.phoneNumber}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share contact"))
                        selectedContact = null
                    }
                )
            }
        }
    }
}

/* ================================================================== */
/*  Single contact row                                                 */
/* ================================================================== */

@Composable
private fun ContactItem(
    contact: Contact,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = avatarColor(contact.name)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = contact.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.phoneNumber,
                fontSize = 13.sp,
                color = secondaryText,
                maxLines = 1
            )
        }
    }
}

/* ================================================================== */
/*  Contact detail bottom sheet                                        */
/* ================================================================== */

@Composable
private fun ContactDetailSheet(
    contact: Contact,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit
) {
    val sheetBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1F1F1F)
    val textSecondary = if (isDark) Color(0xFFBDBDBD) else Color(0xFF666666)
    val actionColor = if (isDark) Color(0xFF8AB4F8) else Color(0xFF1A73E8)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = sheetBg,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDark) Color(0xFF555555) else Color(0xFFCCCCCC))
            )

            Spacer(Modifier.height(16.dp))

            // Avatar
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = avatarColor(contact.name)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                contact.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Text(
                contact.phoneNumber,
                fontSize = 14.sp,
                color = textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(20.dp))

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ContactAction(Icons.Filled.Call, "Call", actionColor, textSecondary, onCall)
                ContactAction(Icons.Filled.Message, "Message", actionColor, textSecondary, onMessage)
                ContactAction(Icons.Filled.Edit, "Edit", actionColor, textSecondary, onEdit)
                ContactAction(Icons.Filled.Share, "Share", actionColor, textSecondary, onShare)
            }

            Spacer(Modifier.height(12.dp))

            // Dismiss / close
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) Color(0xFF333333) else Color(0xFFEEEEEE),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "Close",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = textSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/* ── Action button in the detail sheet ───────────────────────────── */

@Composable
private fun ContactAction(
    icon: ImageVector,
    label: String,
    iconColor: Color,
    labelColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = labelColor)
    }
}

/* ================================================================== */
/*  Load contacts from the system Contacts provider                    */
/* ================================================================== */

private fun loadContacts(context: Context): List<Contact> {
    val result = mutableListOf<Contact>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )

    cursor?.use {
        val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        val seen = mutableSetOf<String>()
        while (it.moveToNext()) {
            val id = it.getLong(idIdx)
            val name = it.getString(nameIdx) ?: "Unknown"
            val number = it.getString(numIdx) ?: continue
            if (seen.add("$name-$number")) {
                result.add(Contact(id, name, number))
            }
        }
    }
    return result
}

/* ================================================================== */
/*  Place call helper                                                  */
/* ================================================================== */

@SuppressLint("MissingPermission")
private fun placeCallFromContacts(context: Context, number: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val uri = Uri.fromParts("tel", number, null)
    try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        tm.placeCall(uri, Bundle())
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot place call: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
