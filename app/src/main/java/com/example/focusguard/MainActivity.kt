package com.example.focusguard

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private data class AppInfo(val label: String, val pkg: String)

    private lateinit var statusText: TextView
    private lateinit var triggersText: TextView
    private lateinit var redirectText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        fun header(text: String) = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(6))
        }

        fun add(v: android.view.View) = root.addView(
            v,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // --- Title (own title instead of the system action bar) ---
        add(TextView(this).apply {
            text = "Focus Guard"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        // --- Service status ---
        statusText = TextView(this)
        add(statusText)
        add(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        // --- Warning message ---
        add(header("Warning message"))
        val warningInput = EditText(this).apply {
            hint = Prefs.DEFAULT_WARNING
            setText(Prefs.getRawWarning(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
        }
        warningInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                Prefs.setWarning(this@MainActivity, s?.toString() ?: "")
            }
        })
        add(warningInput)

        // --- Trigger apps ---
        add(header("Trigger apps"))
        triggersText = TextView(this)
        add(triggersText)
        add(Button(this).apply {
            text = "Choose trigger apps"
            setOnClickListener { pickTriggers() }
        })

        // --- Redirect app ---
        add(header("Redirect app"))
        redirectText = TextView(this)
        add(redirectText)
        add(Button(this).apply {
            text = "Choose redirect app"
            setOnClickListener { pickRedirect() }
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        // Keep content clear of the status bar / navigation bar / cutout (edge-to-edge on Android 15+).
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        statusText.text = if (isServiceEnabled()) {
            "Service status: ON"
        } else {
            "Service status: OFF — enable \"Focus Guard\" in Accessibility settings " +
                "(under Installed/Downloaded apps)."
        }

        val apps = loadApps()
        fun labelFor(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

        val triggers = Prefs.getTriggers(this)
        triggersText.text = if (triggers.isEmpty()) {
            "None selected"
        } else {
            triggers.map { labelFor(it) }.sortedBy { it.lowercase() }.joinToString("\n")
        }

        val redirect = Prefs.getRedirect(this)
        redirectText.text = if (redirect == null) {
            "None (trigger app is just closed)"
        } else {
            labelFor(redirect)
        }
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, WarningService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    @Suppress("DEPRECATION")
    private fun loadApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun pickTriggers() {
        val apps = loadApps()
        val selected = HashSet(Prefs.getTriggers(this))
        val checked = BooleanArray(apps.size) { apps[it].pkg in selected }

        AlertDialog.Builder(this)
            .setTitle("Trigger apps")
            .setMultiChoiceItems(
                apps.map { it.label }.toTypedArray(), checked
            ) { _, which, isChecked ->
                if (isChecked) selected.add(apps[which].pkg) else selected.remove(apps[which].pkg)
            }
            .setPositiveButton("Save") { _, _ ->
                Prefs.setTriggers(this, selected)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickRedirect() {
        val apps = loadApps()
        val current = Prefs.getRedirect(this)
        var index = apps.indexOfFirst { it.pkg == current } // -1 = nothing selected

        AlertDialog.Builder(this)
            .setTitle("Redirect app")
            .setSingleChoiceItems(apps.map { it.label }.toTypedArray(), index) { _, which ->
                index = which
            }
            .setPositiveButton("Save") { _, _ ->
                if (index >= 0) {
                    Prefs.setRedirect(this, apps[index].pkg)
                    refresh()
                }
            }
            .setNeutralButton("None") { _, _ ->
                Prefs.setRedirect(this, null)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
