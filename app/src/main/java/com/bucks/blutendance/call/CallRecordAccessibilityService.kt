package com.bucks.blutendance.call

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal Accessibility Service.
 *
 * When enabled by the user in Android Settings → Accessibility, this service
 * elevates the app's audio-capture privileges on many ROMs, allowing
 * [CallRecordingManager] to use [android.media.MediaRecorder.AudioSource.VOICE_CALL]
 * or VOICE_COMMUNICATION to capture both sides of a phone call.
 *
 * The service itself does **no** accessibility processing — it exists solely
 * to unlock the audio source restriction on devices that honour accessibility
 * service privileges for audio capture.
 */
class CallRecordAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallRecordA11y"

        /** Quick check if the service is currently running. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true

        // Minimal config — we don't actually need any events
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = 0                             // no events
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
            flags = 0
        }
        Log.d(TAG, "Accessibility service connected – audio capture elevated")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — we don't process any events
    }

    override fun onInterrupt() {
        // Nothing to interrupt
    }

    override fun onDestroy() {
        isRunning = false
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }
}
