package com.example.focusguard

import android.content.Context

object Prefs {
    const val DEFAULT_WARNING = "Are you sure you want to open this app? Is it really the best use of your time right now?"

    private const val FILE = "focus_guard"
    private const val KEY_WARNING = "warning"
    private const val KEY_TRIGGERS = "triggers"
    private const val KEY_REDIRECT = "redirect"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Exactly what the user typed (may be empty). */
    fun getRawWarning(c: Context): String = sp(c).getString(KEY_WARNING, "") ?: ""

    /** The message to display: user's text, or the default if empty/blank. */
    fun getWarning(c: Context): String {
        val raw = getRawWarning(c).trim()
        return if (raw.isEmpty()) DEFAULT_WARNING else raw
    }

    fun setWarning(c: Context, text: String) {
        sp(c).edit().putString(KEY_WARNING, text).apply()
    }

    fun getTriggers(c: Context): Set<String> =
        HashSet(sp(c).getStringSet(KEY_TRIGGERS, emptySet()) ?: emptySet<String>())

    fun setTriggers(c: Context, packages: Set<String>) {
        sp(c).edit().putStringSet(KEY_TRIGGERS, HashSet(packages)).apply()
    }

    fun getRedirect(c: Context): String? = sp(c).getString(KEY_REDIRECT, null)

    fun setRedirect(c: Context, pkg: String?) {
        val e = sp(c).edit()
        if (pkg == null) e.remove(KEY_REDIRECT) else e.putString(KEY_REDIRECT, pkg)
        e.apply()
    }
}
