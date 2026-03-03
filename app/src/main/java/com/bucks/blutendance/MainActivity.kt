package com.bucks.blutendance

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bucks.blutendance.ui.screens.ContactsScreen
import com.bucks.blutendance.ui.screens.DialerScreen
import com.bucks.blutendance.ui.theme.FirstappTheme

/* ------------------------------------------------------------------ */
/*  Bottom-nav destinations                                            */
/* ------------------------------------------------------------------ */

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    data object Dialer   : BottomNavItem("dialer",   Icons.Filled.Dialpad,  "Dialer")
    data object Contacts : BottomNavItem("contacts", Icons.Filled.Contacts, "Contacts")
}

/* ------------------------------------------------------------------ */
/*  Activity                                                           */
/* ------------------------------------------------------------------ */

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_DEFAULT_DIALER = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestDefaultDialer()
        setContent {
            FirstappTheme {
                MainScreen()
            }
        }
    }

    /**
     * Ask the user to set this app as the default phone / dialer app
     * so it fully replaces the Google Phone app.
     */
    @Suppress("DEPRECATION")
    private fun requestDefaultDialer() {
        // Already default? Nothing to do.
        val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
        if (tm.defaultDialerPackage == packageName) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : use RoleManager
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER),
                    REQUEST_DEFAULT_DIALER
                )
            }
        } else {
            // Android 6-9 : TelecomManager intent
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            startActivityForResult(intent, REQUEST_DEFAULT_DIALER)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_DIALER) {
            val tm = getSystemService(TELECOM_SERVICE) as TelecomManager
            if (tm.defaultDialerPackage == packageName) {
                Toast.makeText(this, "Set as default dialer!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Main screen with bottom navigation                                 */
/* ------------------------------------------------------------------ */

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val tabs = listOf(BottomNavItem.Dialer, BottomNavItem.Contacts)

    // ── Request permissions on first launch ──────────────────────
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* results handled per-screen */ }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()

        fun addIfMissing(perm: String) {
            if (ContextCompat.checkSelfPermission(context, perm)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(perm)
        }

        addIfMissing(Manifest.permission.CALL_PHONE)
        addIfMissing(Manifest.permission.READ_CONTACTS)
        addIfMissing(Manifest.permission.READ_CALL_LOG)
        addIfMissing(Manifest.permission.READ_PHONE_STATE)
        addIfMissing(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addIfMissing(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }

        // Default dialer prompt is handled in MainActivity.requestDefaultDialer()
    }

    // ── Scaffold ─────────────────────────────────────────────────
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val backstackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backstackEntry?.destination?.route

                tabs.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Dialer.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Dialer.route)   { DialerScreen() }
            composable(BottomNavItem.Contacts.route) { ContactsScreen() }
        }
    }
}
