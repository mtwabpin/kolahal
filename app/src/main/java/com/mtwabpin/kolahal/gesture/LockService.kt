package com.mtwabpin.kolahal.gesture

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * Accessibility service that only locks the screen, for the double-tap gesture and the `lock` command.
 * It listens to no events and reads no window content.
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance?.get() === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: WeakReference<LockService>? = null

        /** Returns true when the screen was locked, false when the service isn't enabled. */
        fun lock(): Boolean = instance?.get()?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, LockService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
