package com.bucks.blutendance.call

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.bucks.blutendance.InCallActivity

/**
 * System-bound [InCallService] that receives call events from the Telecom framework.
 *
 * Critical for a real dialer replacement:
 *  • Uses [startForeground] so the notification stays visible and the service is not killed.
 *  • Acquires a [PowerManager.WakeLock] to turn the screen on for incoming calls.
 *  • Launches [InCallActivity] with the right flags to appear over the lock-screen.
 */
class DialerCallService : InCallService() {

    companion object {
        private const val TAG = "DialerCallService"
    }

    private lateinit var notifManager: CallNotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notifManager = CallNotificationManager(this)
        CallManager.bindService(this)
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        releaseWakeLock()
        CallManager.unbindService()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState?.let { CallManager.onAudioStateChanged(it) }
    }

    /* ================================================================== */
    /*  Call added                                                         */
    /* ================================================================== */

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.addCall(call)
        Log.d(TAG, "onCallAdded – state=${call.state}")

        val phoneNumber = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val callerInfo = ContactResolver.getDisplayName(this, phoneNumber)

        val isIncoming = call.state == Call.STATE_RINGING

        // ── Build appropriate notification and go foreground ─────
        val notification = if (isIncoming) {
            notifManager.getIncomingCallNotification(callerInfo)
        } else {
            notifManager.getOngoingCallNotification(callerInfo)
        }

        val notifId = if (isIncoming)
            CallNotificationManager.INCOMING_NOTIFICATION_ID
        else
            CallNotificationManager.NOTIFICATION_ID

        // startForeground keeps the service alive and the notification visible
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notifId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(notifId, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            // Fallback: just post the notification normally
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(notifId, notification)
        }

        // ── Wake the screen for incoming calls ───────────────────
        if (isIncoming) {
            wakeScreen()
        }

        // ── Update notification when call state changes ──────────
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.d(TAG, "Call state changed to $state for $callerInfo")
                val nm = getSystemService(NotificationManager::class.java)
                when (state) {
                    Call.STATE_ACTIVE -> {
                        releaseWakeLock()
                        nm.cancel(CallNotificationManager.INCOMING_NOTIFICATION_ID)
                        val ongoing = notifManager.getOngoingCallNotification(callerInfo)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                startForeground(
                                    CallNotificationManager.NOTIFICATION_ID,
                                    ongoing,
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                                )
                            } else {
                                startForeground(CallNotificationManager.NOTIFICATION_ID, ongoing)
                            }
                        } catch (e: Exception) {
                            nm.notify(CallNotificationManager.NOTIFICATION_ID, ongoing)
                        }

                        // ── Start recording if enabled ───────────
                        if (RecordingPrefs.isRecordingEnabled(this@DialerCallService)) {
                            CallRecordingManager.startRecording(this@DialerCallService, callerInfo)
                        }
                    }
                    Call.STATE_DISCONNECTED -> {
                        // ── Stop recording ───────────────────────
                        CallRecordingManager.stopRecording()

                        releaseWakeLock()
                        nm.cancel(CallNotificationManager.NOTIFICATION_ID)
                        nm.cancel(CallNotificationManager.INCOMING_NOTIFICATION_ID)
                        if (CallManager.liveCallCount == 0) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        }
                    }
                }
            }
        })

        // ── Launch the in-call UI ────────────────────────────────
        // For incoming calls: only launch full-screen if device is idle/locked.
        // When user is in another app, the heads-up notification is enough.
        // For outgoing calls: always launch (user explicitly placed the call).
        if (isIncoming) {
            if (shouldShowFullScreen()) {
                launchInCallActivity()
            }
            // else: heads-up notification from fullScreenIntent handles it
        } else {
            launchInCallActivity()
        }
    }

    /* ================================================================== */
    /*  Call removed                                                        */
    /* ================================================================== */

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.removeCall(call)
        Log.d(TAG, "onCallRemoved – remaining=${CallManager.liveCallCount}")

        if (CallManager.liveCallCount == 0) {
            releaseWakeLock()
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(CallNotificationManager.NOTIFICATION_ID)
            nm.cancel(CallNotificationManager.INCOMING_NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    /**
     * Returns true when the device is locked, screen-off, or on the home screen.
     * In those cases we should launch the full-screen InCallActivity.
     * When false, the user is actively using another app – just show the
     * heads-up notification and let them tap it to open the call screen.
     */
    @Suppress("DEPRECATION")
    private fun shouldShowFullScreen(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return true   // screen is off

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardLocked) return true // on lock screen

        // Screen is on & unlocked → user might be in another app.
        // Don't launch full-screen; let the heads-up notification handle it.
        return false
    }

    private fun launchInCallActivity() {
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            )
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "com.bucks.blutendance:IncomingCall"
            ).apply {
                acquire(60_000L) // keep screen on for max 60 s
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (_: Exception) { }
    }
}
