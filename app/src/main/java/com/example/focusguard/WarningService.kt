package com.example.focusguard

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches for windows of other apps coming to the foreground. When one of the user's
 * "trigger apps" is started, the full-screen warning (WarningActivity) is shown on top of it.
 * Also keeps track of how long trigger apps are used (see Usage).
 */
class WarningService : AccessibilityService() {

    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> Usage.stop(applicationContext)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> resumeUsage()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!Prefs.isEnabled(this)) return
        val pkg = event.packageName?.toString() ?: return

        // Our own windows, system UI, system dialogs and keyboards don't count as "switching apps".
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android" || isKeyboard(pkg)) return

        val now = SystemClock.elapsedRealtime()
        if (pkg != Guard.foregroundPkg) {
            Usage.stop(applicationContext) // user left whatever app was being timed
            Guard.onSwitch(pkg, now)
        }

        if (pkg !in Prefs.getTriggers(this)) return

        val graceMs = Prefs.getRewarnMinutes(this) * 60_000L
        if (Guard.shouldWarn(pkg, now, graceMs)) {
            Usage.stop(applicationContext)
            Guard.warningFor = pkg
            showWarning(pkg)
        } else if (Guard.isAllowed(pkg)) {
            Usage.start(applicationContext, pkg)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterScreenReceiver()
        Usage.stop(applicationContext)
        Guard.reset()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        super.onDestroy()
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
        receiverRegistered = false
    }

    /** Screen came back on / was unlocked: keep timing the trigger app the user is still in. */
    private fun resumeUsage() {
        if (!Prefs.isEnabled(this)) return
        val pkg = Guard.foregroundPkg ?: return
        val km = getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) return
        if (pkg in Prefs.getTriggers(this) && Guard.isAllowed(pkg)) {
            Usage.start(applicationContext, pkg)
        }
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
