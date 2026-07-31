package io.github.teslanav.app.services

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

/** Generic accessibility service tracking whichever app package is currently in the foreground. */
class ForegroundAppAccessibilityService : android.accessibilityservice.AccessibilityService() {
    companion object {
        @Volatile
        var currentForegroundPackage: String? = null
            private set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        currentForegroundPackage = event.packageName?.toString()
    }

    override fun onInterrupt() {
        currentForegroundPackage = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }
}
