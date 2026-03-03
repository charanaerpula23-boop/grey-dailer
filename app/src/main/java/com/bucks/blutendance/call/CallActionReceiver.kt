package com.bucks.blutendance.call

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bucks.blutendance.InCallActivity

/**
 * Handles notification action buttons (Answer, Decline, Hang Up).
 * After answering, also brings the in-call UI to front.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_ANSWER" -> {
                CallManager.answer()
                // Launch the in-call screen after answering
                val launch = Intent(context, InCallActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                }
                context.startActivity(launch)
            }
            "ACTION_DECLINE" -> {
                CallManager.hangup()
                cancelNotifications(context)
            }
            "ACTION_HANGUP" -> {
                CallManager.hangup()
                cancelNotifications(context)
            }
        }
    }

    private fun cancelNotifications(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(CallNotificationManager.NOTIFICATION_ID)
        nm.cancel(CallNotificationManager.INCOMING_NOTIFICATION_ID)
    }
}
