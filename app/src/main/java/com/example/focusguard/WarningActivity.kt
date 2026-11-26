package com.example.focusguard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen warning shown over a trigger app. */
class WarningActivity : Activity() {

    companion object {
        const val EXTRA_TRIGGER = "trigger"
        private const val OPEN_DELAY_MS = 5000L
    }

    private var trigger = ""
    private var redirect: String? = null
    private var decided = false
    private var timer: CountDownTimer? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trigger = intent.getStringExtra(EXTRA_TRIGGER) ?: run {
            finish()
            return
        }

        // Redirect only counts if it's set, launchable, and not the app being intercepted.
        redirect = Prefs.getRedirect(this)?.takeIf {
            it != trigger && packageManager.getLaunchIntentForPackage(it) != null
        }
        val closeLabel = redirect?.let { "Open ${Guard.appLabel(this, it)}" } ?: "Close"

        val waitView = TextView(this).apply {
            text = "WAIT!"
            textSize = 56f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }

        val messageView = TextView(this).apply {
            text = Prefs.getWarning(this@WarningActivity)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }

        val usageView = TextView(this).apply {
            text = usageText()
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(40))
        }

        val openButton = Button(this).apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            isEnabled = false
            text = "Open anyway (${OPEN_DELAY_MS / 1000})"
            setOnClickListener { openAnyway() }
        }
        val closeButton = Button(this).apply {
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            text = closeLabel
            setOnClickListener { closeOrRedirect() }
        }

        // Both buttons: same width (weight 1) and same fixed height.
        fun buttonParams() = LinearLayout.LayoutParams(0, dp(64), 1f).apply {
            setMargins(dp(6), 0, dp(6), 0)
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(openButton, buttonParams())
        buttons.addView(closeButton, buttonParams())

        val fullWidth = {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(waitView, fullWidth())
        root.addView(messageView, fullWidth())
        root.addView(usageView, fullWidth())
        root.addView(buttons, fullWidth())

        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)
        // Keep content clear of status bar / navigation bar / cutout (edge-to-edge on Android 15+).
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            insets
        }
        setContentView(scroll)

        // "Open anyway" stays disabled for the first few seconds so the message gets read.
        timer = object : CountDownTimer(OPEN_DELAY_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                openButton.text = "Open anyway (${(millisUntilFinished + 999) / 1000})"
            }

            override fun onFinish() {
                openButton.text = "Open anyway"
                openButton.isEnabled = true
            }
        }.start()
    }

    /** "You used this app for 1h 2m 3s today." with the time in bold. */
    private fun usageText(): CharSequence {
        val totalSec = Usage.todayMs(applicationContext, trigger) / 1000
        val time = "${totalSec / 3600}h ${(totalSec % 3600) / 60}m ${totalSec % 60}s"
        val prefix = "You used this app for "
        val text = SpannableString("$prefix$time today.")
        text.setSpan(
            StyleSpan(Typeface.BOLD),
            prefix.length,
            prefix.length + time.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return text
    }

    private fun openAnyway() {
        decided = true
        Guard.allow(trigger)
        Usage.start(applicationContext, trigger)
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
        // User left (Home, Recents, screen off...) without choosing: drop the warning.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        if (!decided && !isChangingConfigurations) Guard.clearWarning(trigger)
    }
}
