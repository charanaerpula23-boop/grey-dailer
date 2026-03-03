package com.bucks.blutendance.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.bucks.blutendance.InCallActivity
import com.bucks.blutendance.R

/**
 * Creates notification channels and builds notifications for incoming
 * and ongoing calls, including action buttons (Answer / Decline / Hang Up).
 *
 * Key design points for a real dialer replacement:
 *  • Incoming-call notification uses IMPORTANCE_MAX with full-screen intent
 *    so Android shows it heads-up / over lock-screen.
 *  • The notification is posted as a foreground-service notification
 *    (via DialerCallService.startForeground()) so the OS cannot kill it.
 *  • Uses CATEGORY_CALL + Person style for proper call-notification treatment.
 *  • Vibration pattern is set on the channel for devices where the system
 *    ringer is active.
 */
class CallNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "active_call_channel"
        const val INCOMING_CHANNEL_ID = "incoming_call_channel"
        const val NOTIFICATION_ID = 1001
        const val INCOMING_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannels()
    }

    /* ================================================================== */
    /*  Channels                                                          */
    /* ================================================================== */

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            // ── Active / ongoing call channel ────────────────────
            val activeChannel = NotificationChannel(
                CHANNEL_ID,
                "Active Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while a call is in progress"
                setShowBadge(false)
            }

            // ── Incoming call channel ────────────────────────────
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val incomingChannel = NotificationChannel(
                INCOMING_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown for incoming calls"
                setSound(ringtoneUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }

            // Delete old channels if they exist (name might have changed)
            manager.deleteNotificationChannel("call_channel")

            manager.createNotificationChannel(activeChannel)
            manager.createNotificationChannel(incomingChannel)
        }
    }

    /* ================================================================== */
    /*  Incoming call notification                                         */
    /* ================================================================== */

    fun getIncomingCallNotification(callerName: String): Notification {
        // Full-screen intent → shows InCallActivity over lock-screen / other apps
        val fullScreenIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, InCallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap notification → also opens InCallActivity
        val contentIntent = PendingIntent.getActivity(
            context, 3,
            Intent(context, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val answerPI = actionPendingIntent("ACTION_ANSWER", 0)
        val declinePI = actionPendingIntent("ACTION_DECLINE", 1)

        // Use CallStyle on Android 12+ for native green/red action buttons
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return NotificationCompat.Builder(context, INCOMING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        caller, declinePI, answerPI
                    )
                )
                .setColor(0xFF4CAF50.toInt())   // green accent for the notification
                .addPerson(caller)
                .build()
        }

        // Fallback for Android < 12: manually add colored action icons
        return NotificationCompat.Builder(context, INCOMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setContentIntent(contentIntent)
            .setLargeIcon(null as android.graphics.Bitmap?)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_call_answer),
                    "Answer", answerPI
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_call_decline),
                    "Decline", declinePI
                ).build()
            )
            .addPerson(caller)
            .build()
    }

    /* ================================================================== */
    /*  Ongoing call notification                                          */
    /* ================================================================== */

    fun getOngoingCallNotification(callerName: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupPI = actionPendingIntent("ACTION_HANGUP", 2)

        // Use CallStyle on Android 12+ for native red hang-up button
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = Person.Builder().setName(callerName).setImportant(true).build()
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setUsesChronometer(true)
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(caller, hangupPI)
                )
                .setColor(0xFF4CAF50.toInt())
                .addPerson(caller)
                .build()
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Ongoing Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setUsesChronometer(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_call_decline),
                    "Hang Up", hangupPI
                ).build()
            )
            .build()
    }

    /* ================================================================== */
    /*  Helpers                                                            */
    /* ================================================================== */

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
