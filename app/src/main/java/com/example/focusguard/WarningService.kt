package com.example.focusguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches for windows of other apps coming to the foreground. When one of the user's
 * "trigger apps" is started, the warning popup (WarningActivity) is shown on top of it.
 */
class WarningService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Our own windows, system UI, system dialogs and keyboards don't count as "switching apps".
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android" || isKeyboard(pkg)) return

        val now = SystemClock.elapsedRealtime()
        if (pkg != Guard.foregroundPkg) Guard.onSwitch(pkg, now)

        if (pkg !in Prefs.getTriggers(this)) return

        if (Guard.shouldWarn(pkg, now)) {
            Guard.warningFor = pkg
            showWarning(pkg)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        Guard.reset()
        return super.onUnbind(intent)
    }

    private fun isKeyboard(pkg: String): Boolean {
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == pkg }
    }

    private fun showWarning(trigger: String) {
        val intent = Intent(this, WarningActivity::class.java)
            .putExtra(WarningActivity.EXTRA_TRIGGER, trigger)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent) // allowed from the background because this is an AccessibilityService
        } catch (e: Exception) {
            Guard.warningFor = null
        }
    }
}
