package nl.psalmbladmuziek.app

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** Bestemming van de 'Steun de app'-knop (tip jar). */
private const val SUPPORT_URL = "https://github.com/hidinker/Psalmverzen"

/**
 * Eén gedeeld instellingen-menu voor zowel het overzicht (MainActivity) als de
 * versweergave (SheetMusicActivity). [onChanged] wordt aangeroepen na elke wijziging
 * zodat het actieve scherm zich direct kan bijwerken (bijv. alleen-tekst/scherm-aan).
 */
fun AppCompatActivity.showAppSettingsDialog(onChanged: () -> Unit = {}) {
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
    }

    fun toggleRow(label: String, initial: Boolean, onToggle: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sw = SwitchCompat(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onToggle(checked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(tv)
        row.addView(sw)
        root.addView(row)
    }

    fun sectionHeader(title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFF607D8B.toInt())
            setPadding(0, dp(16), 0, dp(2))
        })
    }

    fun linkRow(label: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.setOnClickListener { onClick() }
        root.addView(row)
    }

    fun chooserRow(label: String, value: () -> String, onClick: (TextView) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val valueView = TextView(this).apply {
            text = value()
            textSize = 16f
            setTextColor(0xFF607D8B.toInt())
        }
        row.addView(valueView)
        row.setOnClickListener { onClick(valueView) }
        root.addView(row)
    }

    // --- Algemeen ---
    sectionHeader("Algemeen")
    chooserRow("Weergave", { displayModeName(AppSettings.displayMode(this)) }) { valueView ->
        showDisplayModeChooser {
            valueView.text = displayModeName(AppSettings.displayMode(this)); onChanged()
        }
    }
    toggleRow("Scherm aan laten", AppSettings.keepScreenOn(this)) {
        AppSettings.setKeepScreenOn(this, it); onChanged()
    }
    toggleRow("Schriftliederen", AppSettings.showSchriftliederen(this)) {
        AppSettings.setShowSchriftliederen(this, it); onChanged()
    }

    // --- Schermgrootte ---
    sectionHeader("Schermgrootte")
    toggleRow("Grote letters", AppSettings.largeText(this)) {
        AppSettings.setLargeText(this, it); onChanged()
    }
    toggleRow("Regelafbreking toestaan", AppSettings.allowLineWrap(this)) {
        AppSettings.setAllowLineWrap(this, it); onChanged()
    }
    toggleRow("Twee zinnen op één regel", AppSettings.combineLines(this)) {
        AppSettings.setCombineLines(this, it); onChanged()
    }
    toggleRow("Rusttekens uit", !AppSettings.showRests(this)) {
        AppSettings.setShowRests(this, !it); onChanged()
    }
    chooserRow("Tekst uitlijning", { textAlignName(AppSettings.textAlign(this)) }) { valueView ->
        showTextAlignChooser {
            valueView.text = textAlignName(AppSettings.textAlign(this)); onChanged()
        }
    }

    // --- Donker thema ---
    sectionHeader("Donker thema")
    toggleRow("Bladmuziek donker", AppSettings.darkSheet(this)) {
        AppSettings.setDarkSheet(this, it); onChanged()
    }
    chooserRow("Thema", { themeModeName(AppSettings.themeMode(this)) }) { valueView ->
        showThemeChooser {
            valueView.text = themeModeName(AppSettings.themeMode(this)); onChanged()
        }
    }

    // --- Steun ---
    sectionHeader("Steun")
    linkRow("\u2764 Steun de app") {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)))
        } catch (_: Exception) {}
    }

    val versionName = try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: Exception) { null } ?: ""

    val scroll = android.widget.ScrollView(this).apply { addView(root) }
    val dialog = AlertDialog.Builder(this)
        .setTitle("Instellingen")
        .setView(scroll)
        .setNeutralButton(if (versionName.isNotEmpty()) "v$versionName" else "", null)
        .setPositiveButton("Klaar", null)
        .create()
    // Menu heel licht doorschijnend (~5%) en de achtergrond nauwelijks verduisteren,
    // zodat je het effect van een wijziging subtiel achter het menu ziet.
    dialog.setOnShowListener {
        dialog.window?.let { w ->
            w.setDimAmount(0.15f)
            w.decorView.alpha = 0.95f
        }
        // Versienummer als rustig label linksonder (naast Klaar); geen actie bij tik.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            isAllCaps = false
            setTextColor(0xFF9E9E9E.toInt())
            setOnClickListener { /* alleen versielabel */ }
        }
    }
    dialog.show()
}

private fun themeModeName(mode: Int): String = when (mode) {
    AppSettings.THEME_LIGHT -> "Licht"
    AppSettings.THEME_DARK -> "Donker"
    else -> "Systeem"
}

private fun displayModeName(mode: Int): String = when (mode) {
    AppSettings.DISPLAY_TEXT -> "Tekst"
    AppSettings.DISPLAY_NOTES -> "Noten"
    else -> "Beide"
}

private fun AppCompatActivity.showDisplayModeChooser(onChanged: () -> Unit) {
    val options = arrayOf("Beide", "Tekst", "Noten")
    AlertDialog.Builder(this)
        .setTitle("Weergave")
        .setSingleChoiceItems(options, AppSettings.displayMode(this)) { dialog, which ->
            AppSettings.setDisplayMode(this, which)
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun AppCompatActivity.showThemeChooser(onChanged: () -> Unit) {
    val options = arrayOf("Systeem volgen", "Licht", "Donker")
    AlertDialog.Builder(this)
        .setTitle("Thema (bladmuziek blijft licht)")
        .setSingleChoiceItems(options, AppSettings.themeMode(this)) { dialog, which ->
            AppSettings.setThemeMode(this, which)
            AppSettings.applyThemeMode(this)
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun textAlignName(align: Int): String = when (align) {
    AppSettings.ALIGN_LEFT -> "Links"
    AppSettings.ALIGN_FILL -> "Vullend"
    else -> "Centrisch"
}

private fun AppCompatActivity.showTextAlignChooser(onChanged: () -> Unit) {
    val options = arrayOf("Links", "Centrisch", "Vullend")
    AlertDialog.Builder(this)
        .setTitle("Tekst uitlijning")
        .setSingleChoiceItems(options, AppSettings.textAlign(this)) { dialog, which ->
            AppSettings.setTextAlign(this, which)
            dialog.dismiss()
            onChanged()
        }
        .show()
}
