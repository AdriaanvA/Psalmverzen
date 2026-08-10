package nl.psalmbladmuziek.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Eenvoudige app-instellingen (SharedPreferences).
 */
object AppSettings {
    private const val PREFS = "psalmenapp_settings"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_NOTE_SCALE = "note_scale"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TEXT_ONLY = "text_only"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_LARGE_TEXT = "large_text"
    private const val KEY_SHOW_SCHRIFTLIEDEREN = "show_schriftliederen"
    private const val KEY_ALLOW_LINE_WRAP = "allow_line_wrap"
    private const val KEY_DARK_SHEET = "dark_sheet"
    private const val KEY_SHOW_RESTS = "show_rests"
    private const val KEY_TEXT_ALIGN = "text_align"
    private const val KEY_COMBINE_LINES = "combine_lines"
    private const val KEY_PSALM_VERSION = "psalm_version"
    private const val KEY_RHYTHM_MODE = "rhythm_mode"
    /** Themamodus: 0 = systeem volgen, 1 = altijd licht, 2 = altijd donker. */
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /** Tekstuitlijning in de bladmuziek: 0 = links, 1 = centrisch, 2 = vullend. */
    const val ALIGN_LEFT = 0
    const val ALIGN_CENTER = 1
    const val ALIGN_FILL = 2

    /** Weergave: 0 = beide (noten + tekst), 1 = alleen tekst, 2 = alleen noten. */
    const val DISPLAY_BOTH = 0
    const val DISPLAY_TEXT = 1
    const val DISPLAY_NOTES = 2
    private const val KEY_DISPLAY_MODE = "display_mode"

    /** Psalmberijming: 0 = 1773 (huidig), 1 = Datheen, 2 = Revius. */
    const val PSALM_VERSION_1773 = 0
    const val PSALM_VERSION_DATHEEN = 1
    const val PSALM_VERSION_REVIUS = 2

    /** Ritme: 0 = ritmisch, 1 = iso-ritmisch (kwartnoten als halve noten). */
    const val RHYTHM_RHYTHMIC = 0
    const val RHYTHM_ISOMETRIC = 1

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Onthouden tekst-schaal (blijft bewaard na herstart). */
    fun textScale(context: Context, default: Double): Double =
        prefs(context).getFloat(KEY_TEXT_SCALE, default.toFloat()).toDouble()

    fun setTextScale(context: Context, value: Double) {
        prefs(context).edit().putFloat(KEY_TEXT_SCALE, value.toFloat()).apply()
    }

    /** Onthouden noten-schaal (blijft bewaard na herstart). */
    fun noteScale(context: Context, default: Double): Double =
        prefs(context).getFloat(KEY_NOTE_SCALE, default.toFloat()).toDouble()

    fun setNoteScale(context: Context, value: Double) {
        prefs(context).edit().putFloat(KEY_NOTE_SCALE, value.toFloat()).apply()
    }

    /** Themavoorkeur (0=systeem, 1=licht, 2=donker). Bladmuziek zelf blijft altijd licht.
     *  Standaard = licht. */
    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, value).apply()
    }

    /** Standaard 'alleen tekst' tonen (geen notenbalk) bij het openen van een vers. */
    fun textOnly(context: Context): Boolean =
        displayMode(context) == DISPLAY_TEXT

    /** Weergave: beide (noten + tekst), alleen tekst, of alleen noten.
     *  Migreert de oude 'alleen tekst'-schakelaar naar de nieuwe drie-standen keuze. */
    fun displayMode(context: Context): Int {
        val p = prefs(context)
        val default = if (p.getBoolean(KEY_TEXT_ONLY, false)) DISPLAY_TEXT else DISPLAY_BOTH
        return p.getInt(KEY_DISPLAY_MODE, default)
    }

    fun setDisplayMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_DISPLAY_MODE, value).apply()
    }

    /** Lettergrepen onder de noten tonen (weergave 'beide'); bij 'noten' verborgen. */
    fun showLyrics(context: Context): Boolean =
        displayMode(context) != DISPLAY_NOTES

    /** Scherm aan laten tijdens het lezen van een vers (geen automatische time-out). */
    fun keepScreenOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, false)

    fun setKeepScreenOn(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()
    }

    /** Grote letters: minimale woordafstand in de bladmuziek, zodat de tekst groter oogt. */
    fun largeText(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LARGE_TEXT, false)

    fun setLargeText(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_LARGE_TEXT, value).apply()
    }

    /** Schriftliederen (Gezang 13 en verder) tonen in de lijst met enige gezangen. */
    fun showSchriftliederen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_SCHRIFTLIEDEREN, false)

    fun setShowSchriftliederen(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_SCHRIFTLIEDEREN, value).apply()
    }

    /** Regelafbreking toestaan: lange notenregel over 2 rijen (2e rij rechts uitgelijnd),
     *  zodat de tekst groter blijft i.p.v. sterk terug te schalen. */
    fun allowLineWrap(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_LINE_WRAP, false)

    fun setAllowLineWrap(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_LINE_WRAP, value).apply()
    }

    /** Bladmuziek ook donker maken wanneer het donkere thema actief is.
     *  Standaard uit: het blad blijft normaal altijd licht ('papier'). */
    fun darkSheet(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_SHEET, false)

    fun setDarkSheet(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_SHEET, value).apply()
    }

    /** Rusttekens (rusten) in de bladmuziek tonen. Standaard uit voor maximale regelbreedte.
     *  Bij achtste-nootmuziek worden rusten alsnog getoond door de renderer. */
    fun showRests(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_RESTS, false)

    fun setShowRests(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_RESTS, value).apply()
    }

    /** Tekstuitlijning in de bladmuziek (0=links, 1=centrisch, 2=vullend). Standaard centrisch. */
    fun textAlign(context: Context): Int =
        prefs(context).getInt(KEY_TEXT_ALIGN, ALIGN_CENTER)

    fun setTextAlign(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_TEXT_ALIGN, value).apply()
    }

    /** Twee opeenvolgende (korte) tekstregels op één notenregel combineren als ze passen
     *  (handig in landscape / op tablets). Standaard uit. */
    fun combineLines(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMBINE_LINES, false)

    fun setCombineLines(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_COMBINE_LINES, value).apply()
    }

    /** Psalmberijming voor de psalmen. Gezangen blijven ongewijzigd. */
    fun psalmVersion(context: Context): Int =
        prefs(context).getInt(KEY_PSALM_VERSION, PSALM_VERSION_1773)

    fun setPsalmVersion(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_PSALM_VERSION, value).apply()
    }

    /** Ritmische of iso-ritmische notatie. */
    fun rhythmMode(context: Context): Int =
        prefs(context).getInt(KEY_RHYTHM_MODE, RHYTHM_RHYTHMIC)

    fun setRhythmMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_RHYTHM_MODE, value).apply()
    }

    /** Past de opgeslagen themavoorkeur toe (recreëert actieve schermen indien nodig). */
    fun applyThemeMode(context: Context) {
        val mode = when (themeMode(context)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
