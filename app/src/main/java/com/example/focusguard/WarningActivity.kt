package com.example.focusguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Dialog-styled popup shown over a trigger app. */
class WarningActivity : Activity() {

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }

    private var trigger = ""
    private var redirect: String? = null
    private var decided = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trigger = intent.getStringExtra(EXTRA_TRIGGER) ?: run {
            finish()
            return
        }
        setFinishOnTouchOutside(false)

        // Redirect only counts if it's set, launchable, and not the app being intercepted.
        redirect = Prefs.getRedirect(this)?.takeIf {
            it != trigger && packageManager.getLaunchIntentForPackage(it) != null
        }
        val closeLabel = redirect?.let { "Open ${Guard.appLabel(this, it)}" } ?: "Close"

        val message = TextView(this).apply {
            text = Prefs.getWarning(this@WarningActivity)
            textSize = 18f
            setPadding(0, 0, 0, dp(20))
        }

        val openButton = Button(this).apply {
            text = "Open anyway"
            setOnClickListener { openAnyway() }
        }
        val closeButton = Button(this).apply {
            text = closeLabel
            setOnClickListener { closeOrRedirect() }
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(openButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(closeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        root.addView(message)
        root.addView(buttons)
        setContentView(root)
    }

    private fun openAnyway() {
        decided = true
        Guard.allow(trigger)
        finish() // trigger app is right underneath
    }

    private fun closeOrRedirect() {
        decided = true
        // Guard.warningFor stays set until the user switches apps, so late events
        // from the trigger app can't bring the warning straight back.
        val launch = redirect?.let { packageManager.getLaunchIntentForPackage(it) }
        try {
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            } else {
                goHome()
            }
        } catch (e: Exception) {
            goHome()
        }
        finish()
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // nothing else we can do
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        closeOrRedirect() // back = same as the Close / redirect button
    }

    override fun onStop() {
        super.onStop()
        // User left (Home, Recents, screen off...) without choosing: drop the popup.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!decided && !isChangingConfigurations) Guard.clearWarning(trigger)
    }
}
