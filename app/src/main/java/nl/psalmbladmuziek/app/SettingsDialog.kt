package nl.psalmbladmuziek.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.net.toUri
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** Bestemming van de 'Steun de app'-knop (tip jar). */
private const val SUPPORT_URL = "https://adriaanva.github.io/Psalmverzen/"

private const val PSALM_RENDITIONS_ABOUT = """De Psalmberijming van 1773, ook wel de Oude Berijming, is de bekendste Nederlandse psalmberijming en wordt nog altijd in veel reformatorische en gereformeerde kerken gebruikt. Zij kwam tot stand uit eerdere berijmingen van onder anderen Johannes Eusebius Voet, Hendrik Ghijsen en het dichtgenootschap Laus Deo, Salus Populo, en werd in 1773 op last van de Staten-Generaal ingevoerd.

De psalmberijming van Petrus Datheen (1566) was de eerste volledige Nederlandse gereformeerde psalmberijming. Datheen bewerkte de Franse psalmberijming van Clément Marot en Théodore de Bèze naar het Nederlands en gebruikte daarbij de Geneefse psalmmelodieën. De berijming speelde een grote rol tijdens de Reformatie en bleef tot ver in de achttiende eeuw de gangbare Nederlandse gereformeerde psalmberijming, totdat zij grotendeels werd vervangen door die van 1773.

De psalmberijming van Jacobus Revius (1640) ontstond als een verbetering van de berijming van Datheen. Revius wilde Datheens psalmen verbeteren in zin en rijm: dichter bij de Bijbeltekst en tegelijk taalkundig en dichterlijk beter. Daarbij sloot hij bewust aan bij de Statenvertaling van 1637, die kort daarvoor was verschenen.

De psalmberijming van Philips van Marnix van Sint Aldegonde (1591) is een Nederlandse psalmberijming. Marnix werkte rechtstreeks vanuit de Hebreeuwse grondtekst, zoals ook de oorspronkelijke titel vermeldt: Het boeck der Psalmen. Wt de Hebreische sprake in Nederduytsch dichte. Hij streefde daarmee naar een nauwkeurige weergave van de Hebreeuwse psalmtekst. De Statenvertaling was toen nog niet verschenen; die verscheen pas in 1637."""

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

    fun toggleRowWithInfo(label: String, initial: Boolean, onToggle: (Boolean) -> Unit, onInfo: (() -> Unit)?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = if (onInfo == null) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }
        val sw = SwitchCompat(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onToggle(checked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(tv)
        if (onInfo != null) {
            val info = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setImageResource(R.drawable.ic_info)
                setColorFilter(ContextCompat.getColor(this@showAppSettingsDialog, R.color.app_text_secondary))
                contentDescription = "Informatie over Schriftliederen"
                setOnClickListener { onInfo() }
            }
            row.addView(info)
            row.addView(Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
        row.addView(sw)
        root.addView(row)
    }

    fun toggleRow(label: String, initial: Boolean, onToggle: (Boolean) -> Unit) {
        toggleRowWithInfo(label, initial, onToggle, null)
    }

    fun sectionHeader(title: String) {
        val tv = android.util.TypedValue()
        val activeColor = if (theme.resolveAttribute(android.R.attr.colorControlActivated, tv, true)) tv.data
            else ContextCompat.getColor(this, R.color.settings_blue_bright)
        root.addView(TextView(this).apply {
            text = title
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(activeColor)
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

    fun chooserRow(label: String, value: () -> String, onInfo: (() -> Unit)? = null, onClick: (TextView) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = if (onInfo == null) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        })
        if (onInfo != null) {
            row.addView(ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setImageResource(R.drawable.ic_info)
                setColorFilter(ContextCompat.getColor(this@showAppSettingsDialog, R.color.app_text_secondary))
                contentDescription = "Informatie over de Psalmberijmingen"
                setOnClickListener { onInfo() }
            })
            row.addView(Space(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
        val tv = android.util.TypedValue()
        val activeColor = if (theme.resolveAttribute(android.R.attr.colorControlActivated, tv, true)) tv.data
            else ContextCompat.getColor(this, R.color.settings_blue_bright)
        val valueView = TextView(this).apply {
            text = value()
            textSize = 16f
            setTextColor(activeColor)
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
    chooserRow("Raster grootte", { getString(R.string.grid_columns, AppSettings.psalmGridColumns(this)) }) { valueView ->
        showPsalmGridColumnsChooser {
            valueView.text = getString(R.string.grid_columns, AppSettings.psalmGridColumns(this)); onChanged()
        }
    }
    toggleRow("Scherm aan laten", AppSettings.keepScreenOn(this)) {
        AppSettings.setKeepScreenOn(this, it); onChanged()
    }
    chooserRow("Psalmberijming", { psalmVersionShortName(AppSettings.psalmVersion(this)) }, onInfo = {
        AlertDialog.Builder(this)
            .setTitle("Psalmberijmingen")
            .setMessage(PSALM_RENDITIONS_ABOUT)
            .setPositiveButton("Sluiten", null)
            .show()
    }) { valueView ->
        showPsalmVersionChooser {
            valueView.text = psalmVersionShortName(AppSettings.psalmVersion(this)); onChanged()
        }
    }
    toggleRowWithInfo(
        "Schriftliederen",
        AppSettings.showSchriftliederen(this),
        { AppSettings.setShowSchriftliederen(this, it); onChanged() },
        {
            AlertDialog.Builder(this)
                .setTitle("Schriftliederen")
                .setMessage(SCHRIFTLIEDEREN_ABOUT)
                .setPositiveButton("Sluiten", null)
                .show()
        }
    )

    // --- Noten en muziek ---
    sectionHeader("Noten en muziek")
    chooserRow("Ritme", { rhythmModeName(AppSettings.rhythmMode(this)) }) { valueView ->
        showRhythmModeChooser {
            valueView.text = rhythmModeName(AppSettings.rhythmMode(this)); onChanged()
        }
    }
    chooserRow("Bovenbalk knop", { topBarActionName(AppSettings.topBarActionIcon(this)) }) { valueView ->
        showTopBarActionChooser {
            valueView.text = topBarActionName(AppSettings.topBarActionIcon(this)); onChanged()
        }
    }
    chooserRow("Afspeelsnelheid", { "${AppSettings.playbackTempo(this)}" }) { valueView ->
        showTempoChooser {
            valueView.text = "${AppSettings.playbackTempo(this)}"; onChanged()
        }
    }
    chooserRow("Kleur huidige noot", { AppSettings.PLAYBACK_HIGHLIGHT_COLORS[AppSettings.playbackHighlightColor(this)].name }) { valueView ->
        showPlaybackHighlightColorChooser {
            valueView.text = AppSettings.PLAYBACK_HIGHLIGHT_COLORS[AppSettings.playbackHighlightColor(this)].name; onChanged()
        }
    }
    chooserRow("Registratie", { registrationName(AppSettings.playbackRegistration(this)) }) { valueView ->
        showRegistrationChooser {
            valueView.text = registrationName(AppSettings.playbackRegistration(this)); onChanged()
        }
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
    toggleRow("Rusttekens weergeven", AppSettings.showRests(this)) {
        AppSettings.setShowRests(this, it); onChanged()
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

    // --- Over ---
    sectionHeader("Over")
    linkRow("Informatie") {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, SUPPORT_URL.toUri()))
        } catch (_: Exception) {}
    }

    val packageInfo = try {
        packageManager.getPackageInfo(packageName, 0)
    } catch (_: Exception) { null }
    val versionName = packageInfo?.versionName.orEmpty()
    val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val versionLabel = if (versionName.isNotEmpty() && isDebuggable && packageInfo != null) {
        val timestamp = SimpleDateFormat("dd-MM HH:mm", Locale.getDefault()).format(Date(packageInfo.lastUpdateTime))
        "v$versionName $timestamp"
    } else if (versionName.isNotEmpty()) {
        "v$versionName"
    } else {
        ""
    }

    val scroll = android.widget.ScrollView(this).apply { addView(root) }
    val dialog = AlertDialog.Builder(this)
        .setTitle("Instellingen")
        .setView(scroll)
        .setNeutralButton(versionLabel, null)
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
            setTextColor(ContextCompat.getColor(this@showAppSettingsDialog, R.color.app_text_secondary))
            setOnClickListener { /* alleen versielabel */ }
        }
        // KLAAR knop uses theme's colorAccent (settings_blue_bright) which is already applied
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isAllCaps = false
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

private fun psalmVersionName(version: Int): String = when (version) {
    AppSettings.PSALM_VERSION_DATHEEN -> "Datheen — 1566"
    AppSettings.PSALM_VERSION_REVIUS -> "Revius — 1640"
    AppSettings.PSALM_VERSION_MARNIX -> "Marnix — 1591"
    else -> "1773 — Oude berijming"
}

private fun psalmVersionShortName(version: Int): String = when (version) {
    AppSettings.PSALM_VERSION_DATHEEN -> "Datheen"
    AppSettings.PSALM_VERSION_REVIUS -> "Revius"
    AppSettings.PSALM_VERSION_MARNIX -> "Marnix"
    else -> "1773"
}

private fun rhythmModeName(mode: Int): String = when (mode) {
    AppSettings.RHYTHM_ISOMETRIC -> "Iso-ritmisch"
    else -> "Ritmisch"
}

private fun topBarActionName(value: Int): String = when (value) {
    AppSettings.TOPBAR_ACTION_PLAY -> "Afspelen"
    else -> "Delen"
}

private fun playbackVoicingName(value: Int): String = when (value) {
    AppSettings.PLAYBACK_VOICING_CHORDS -> "Akkoorden"
    else -> "Discant"
}

private data class RegistrationOption(val label: String, val mask: Int, val weight: Int = 1) {
    override fun toString(): String = label
}

private val registrationOptions = arrayOf(
    RegistrationOption("Bourdon 16'", AppSettings.REGISTER_BOURDON16),
    RegistrationOption("Prestant 8'", AppSettings.REGISTER_PRESTANT8),
    RegistrationOption("Holpijp 8'", AppSettings.REGISTER_HOLPIJP8),
    RegistrationOption("Roerfluit 8'", AppSettings.REGISTER_ROERFLUIT8),
    RegistrationOption("Gedekt 8'", AppSettings.REGISTER_GEDEKT8),
    RegistrationOption("Octaaf 4'", AppSettings.REGISTER_OCTAAF4),
    RegistrationOption("Fluit 4'", AppSettings.REGISTER_FLUIT4),
    RegistrationOption("Quintfluit 3'", AppSettings.REGISTER_QUINTFLUIT),
    RegistrationOption("Tremulant", AppSettings.REGISTER_TREMULANT, weight = 0),
    RegistrationOption("Fluit solo", AppSettings.REGISTER_FLUIT_SOLO),
    RegistrationOption("Orchestral strings", AppSettings.REGISTER_ORCHESTRAL_STRINGS, weight = 2)
)

private fun registrationName(value: Int): String {
    val selected = registrationOptions.filter { value and it.mask != 0 }
    return when (selected.size) {
        0 -> "Geen register"
        1 -> selected.first().label
        else -> "Multi"
    }
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

private fun AppCompatActivity.showPsalmVersionChooser(onChanged: () -> Unit) {
    val options = arrayOf("1773 — Oude berijming", "Datheen — 1566", "Revius — 1640", "Marnix — 1591")
    AlertDialog.Builder(this)
        .setTitle("Psalmberijming")
        .setSingleChoiceItems(options, AppSettings.psalmVersion(this)) { dialog, which ->
            AppSettings.setPsalmVersion(this, which)
            VerseSearchIndex.clear()
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun AppCompatActivity.showRhythmModeChooser(onChanged: () -> Unit) {
    val options = arrayOf("Ritmisch", "Iso-ritmisch")
    AlertDialog.Builder(this)
        .setTitle("Ritme")
        .setSingleChoiceItems(options, AppSettings.rhythmMode(this)) { dialog, which ->
            AppSettings.setRhythmMode(this, which)
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun AppCompatActivity.showPlaybackVoicingChooser(onChanged: () -> Unit) {
    val options = arrayOf("Discant", "Akkoorden")
    AlertDialog.Builder(this)
        .setTitle("Afspeelwijze")
        .setSingleChoiceItems(options, AppSettings.playbackVoicing(this)) { dialog, which ->
            AppSettings.setPlaybackVoicing(this, which)
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun AppCompatActivity.showTopBarActionChooser(onChanged: () -> Unit) {
    val options = arrayOf("Delen", "Afspelen")
    AlertDialog.Builder(this)
        .setTitle("Bovenbalk knop")
        .setSingleChoiceItems(options, AppSettings.topBarActionIcon(this)) { dialog, which ->
            AppSettings.setTopBarActionIcon(this, which)
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun AppCompatActivity.showTempoChooser(onChanged: () -> Unit) {
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), 0)
    }
    val valueLabel = TextView(this).apply {
        textSize = 18f
        text = getString(R.string.tempo_value, AppSettings.playbackTempo(this@showTempoChooser))
        setPadding(0, 0, 0, dp(8))
    }
    val seekBar = SeekBar(this).apply {
        max = AppSettings.MAX_PLAYBACK_TEMPO - AppSettings.MIN_PLAYBACK_TEMPO
        progress = AppSettings.playbackTempo(this@showTempoChooser) - AppSettings.MIN_PLAYBACK_TEMPO
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val tempo = AppSettings.MIN_PLAYBACK_TEMPO + progress
                valueLabel.text = getString(R.string.tempo_value, tempo)
                if (fromUser) {
                    AppSettings.setPlaybackTempo(this@showTempoChooser, tempo)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onChanged()
            }
        })
    }

    root.addView(valueLabel)
    root.addView(seekBar)

    AlertDialog.Builder(this)
        .setTitle("Afspeelsnelheid")
        .setView(root)
        .setPositiveButton("Klaar", null)
        .show()
}

private fun AppCompatActivity.showPlaybackHighlightColorChooser(onChanged: () -> Unit) {
    val colors = AppSettings.PLAYBACK_HIGHLIGHT_COLORS
    val adapter = object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_list_item_single_choice,
        colors.map { it.name }
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.setTextColor(android.graphics.Color.parseColor(colors[position].hex))
            return view
        }
    }
    AlertDialog.Builder(this)
        .setTitle("Kleur tijdens afspelen")
        .setSingleChoiceItems(adapter, AppSettings.playbackHighlightColor(this)) { dialog, which ->
            AppSettings.setPlaybackHighlightColor(this, which)
            dialog.dismiss()
            onChanged()
        }
        .show()
}

private fun AppCompatActivity.showPsalmGridColumnsChooser(onChanged: () -> Unit) {
    val density = resources.displayMetrics.density
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
    }
    val valueView = TextView(this).apply {
        textSize = 16f
        text = getString(R.string.grid_columns, AppSettings.psalmGridColumns(this@showPsalmGridColumnsChooser))
    }
    val seekBar = SeekBar(this).apply {
        max = AppSettings.MAX_PSALM_GRID_COLUMNS - AppSettings.MIN_PSALM_GRID_COLUMNS
        progress = AppSettings.psalmGridColumns(this@showPsalmGridColumnsChooser) - AppSettings.MIN_PSALM_GRID_COLUMNS
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val columns = AppSettings.MIN_PSALM_GRID_COLUMNS + progress
                valueView.text = getString(R.string.grid_columns, columns)
                if (fromUser) {
                    AppSettings.setPsalmGridColumns(this@showPsalmGridColumnsChooser, columns)
                    onChanged()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }
    root.addView(valueView)
    root.addView(seekBar)
    AlertDialog.Builder(this)
        .setTitle("Raster grootte")
        .setView(root)
        .setPositiveButton("Gereed", null)
        .show()
}

private fun AppCompatActivity.showRegistrationChooser(onChanged: () -> Unit) {
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()

    var selectedMask = AppSettings.playbackRegistration(this)
    fun selectedWeight(): Int = registrationOptions.sumOf { if (selectedMask and it.mask != 0) it.weight else 0 }
    fun isPossible(option: RegistrationOption): Boolean = selectedMask and option.mask != 0 || selectedWeight() + option.weight <= AppSettings.MAX_PLAYBACK_REGISTRATION_WEIGHT

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(6), 0, 0)
    }

    val listView = ListView(this).apply {
        choiceMode = ListView.CHOICE_MODE_MULTIPLE
        divider = null
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            min((resources.displayMetrics.heightPixels * 0.62).toInt(), dp(430))
        )
    }
    val adapter = object : ArrayAdapter<RegistrationOption>(this, android.R.layout.simple_list_item_multiple_choice, registrationOptions) {
        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = isPossible(getItem(position)!!)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            view.alpha = if (isEnabled(position)) 1.0f else 0.38f
            return view
        }
    }
    listView.adapter = adapter
    registrationOptions.forEachIndexed { index, option ->
        listView.setItemChecked(index, selectedMask and option.mask != 0)
    }
    listView.setOnItemClickListener { _, _, which, _ ->
        val option = registrationOptions[which]
        selectedMask = if (selectedMask and option.mask != 0) {
            selectedMask and option.mask.inv()
        } else {
            selectedMask or option.mask
        }
        registrationOptions.forEachIndexed { index, registrationOption ->
            listView.setItemChecked(index, selectedMask and registrationOption.mask != 0)
        }
        AppSettings.setPlaybackRegistration(this, selectedMask)
        adapter.notifyDataSetChanged()
        onChanged()
    }
    root.addView(listView)

    AlertDialog.Builder(this)
        .setTitle("Registratie")
        .setView(root)
        .setPositiveButton("Sluiten", null)
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
