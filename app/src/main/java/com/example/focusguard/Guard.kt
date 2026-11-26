package com.example.focusguard

import android.content.Context
import android.content.pm.PackageManager

/**
 * In-memory state shared by WarningService and WarningActivity (same process, main thread only).
 *
 * Android gives a normal app no way to tell a fresh app start from a switch back to an app that is
 * already in Recents. So after the user picks "Open anyway", that app is treated as "in use":
 * it won't be warned about again unless the user has been out of it for REWARN_AFTER_MS.
 */
object Guard {
    /** How long you can be away from an allowed trigger app before the next open warns again. */
    const val REWARN_AFTER_MS = 10 * 60 * 1000L

    /** Trigger apps the user chose "Open anyway" for. Value = when they left it (null = still using it). */
    private val allowed = HashMap<String, Long?>()

    /** Last real app seen in the foreground (keyboards, system UI and our own windows excluded). */
    var foregroundPkg: String? = null
        private set

    /** App we've shown (or are showing / just handled) a warning for; blocks duplicate warnings. */
    var warningFor: String? = null

    /** Called when a different app comes to the foreground. */
    fun onSwitch(to: String, now: Long) {
        val from = foregroundPkg
        if (from != null && allowed.containsKey(from)) allowed[from] = now
        foregroundPkg = to
        warningFor = null
    }

    /** Called for every foreground event of a trigger app. True = show the warning. */
    fun shouldWarn(pkg: String, now: Long): Boolean {
        if (warningFor == pkg) return false
        if (!allowed.containsKey(pkg)) return true
        val left = allowed[pkg] ?: return false // never left it since "Open anyway"
        if (now - left > REWARN_AFTER_MS) {
            allowed.remove(pkg)
            return true
        }
        allowed[pkg] = null // back within the grace period: same session
        return false
    }

    fun allow(pkg: String) {
        allowed[pkg] = null
        warningFor = null
    }

    fun clearWarning(pkg: String) {
        if (warningFor == pkg) warningFor = null
    }

    fun reset() {
        allowed.clear()
        foregroundPkg = null
        warningFor = null
    }

    @Suppress("DEPRECATION")
    fun appLabel(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
