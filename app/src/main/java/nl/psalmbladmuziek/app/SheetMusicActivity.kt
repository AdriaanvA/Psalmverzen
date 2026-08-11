package nl.psalmbladmuziek.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.OnBackPressedCallback
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

class SheetMusicActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var psalmPickerTextView: TextView
    private lateinit var verseTitleTextView: TextView
    private lateinit var textScaleTextView: TextView
    private lateinit var controlsPanel: LinearLayout
    private lateinit var lyricsContainer: LinearLayout
    private lateinit var lyricsTextView: TextView
    private lateinit var verseMatrixGrid: GridLayout
    private lateinit var previousVerseButton: Button
    private lateinit var nextVerseButton: Button
    private lateinit var playMelodyButton: ImageButton
    private lateinit var pdfExporter: SheetPdfExporter
    private lateinit var gestureController: SheetGestureController
    private val melodyPlayer = LiveMelodyPlayer()
    private lateinit var fileName: String
    private var currentLyricTextScale = DEFAULT_TEXT_SCALE
    private var currentScoreVerticalScale = DEFAULT_NOTE_SCALE
    private var currentTransposition = 0
    private var lastPlaybackTempo = AppSettings.DEFAULT_PLAYBACK_TEMPO
    private var lastPlaybackTimbre = AppSettings.DEFAULT_PLAYBACK_TIMBRE
    private var showNotes = true
    private var exportingPdf = false
    private var stackedVerseFileNames: LinkedHashSet<String>? = null

    private var isFullscreen = false
    private val fullscreenBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitFullscreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_sheet_music)
        supportActionBar?.hide()
        val statusBarBackground = findViewById<View>(R.id.statusBarBackground)
        val navigationBarBackground = findViewById<View>(R.id.navigationBarBackground)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sheetMusicRoot)) { root, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            statusBarBackground.layoutParams = statusBarBackground.layoutParams.apply {
                height = statusBars.top
            }
            navigationBarBackground.layoutParams = navigationBarBackground.layoutParams.apply {
                height = navigationBars.bottom
            }
            root.setPadding(0, 0, 0, 0)
            insets
        }

        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: DEFAULT_FILE_NAME
        lastPlaybackTempo = AppSettings.playbackTempo(this)
        lastPlaybackTimbre = AppSettings.playbackTimbre(this)
        currentLyricTextScale = roundScaleToGrid(AppSettings.textScale(this, DEFAULT_TEXT_SCALE), DEFAULT_TEXT_SCALE, TEXT_SCALE_STEP, MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        currentScoreVerticalScale = roundScaleToGrid(AppSettings.noteScale(this, DEFAULT_NOTE_SCALE), DEFAULT_NOTE_SCALE, NOTE_SCALE_STEP, MIN_NOTE_SCALE, MAX_NOTE_SCALE)
        showNotes = !isTextOnlyAsset() && !AppSettings.textOnly(this)
        applyKeepScreenOn()
        HymnRepository.ensureLoaded(this)
        webView = findViewById(R.id.webView)
        psalmPickerTextView = findViewById(R.id.psalmPickerTextView)
        verseTitleTextView = findViewById(R.id.verseTitleTextView)
        textScaleTextView = findViewById(R.id.textScaleTextView)
        controlsPanel = findViewById(R.id.controlsPanel)
        lyricsContainer = findViewById(R.id.lyricsContainer)
        lyricsTextView = findViewById(R.id.lyricsTextView)
        verseMatrixGrid = findViewById(R.id.verseMatrixGrid)
        previousVerseButton = findViewById(R.id.prevVerseButton)
        nextVerseButton = findViewById(R.id.nextVerseButton)
        playMelodyButton = findViewById(R.id.playMelodyButton)
        pdfExporter = SheetPdfExporter(
            activity = this,
            webView = webView,
            isTextOnlyAsset = ::isTextOnlyAsset,
            titleProvider = ::buildPdfTitle,
            setExportingPdf = { exportingPdf = it },
            shouldRestoreScore = { showNotes && !isTextOnlyAsset() },
            preferSinglePdfPage = ::preferSinglePdfPage
        )
        gestureController = SheetGestureController(
            context = this,
            onPreviousVerse = { openAdjacentVerse(previous = true) },
            onNextVerse = { openAdjacentVerse(previous = false) },
            onTextDoubleTap = { toggleFullscreen() },
            onPinch = { scaleFactor -> applyPinchScaleFactor(scaleFactor) },
            onPinchEnd = { savePinchScale() }
        )

        setupButtons()
        setupWebView()
        applySheetTheme()
        onBackPressedDispatcher.addCallback(this, fullscreenBackCallback)
    }

    private fun setupButtons() {
        findViewById<View>(R.id.backToIndexButton).setOnClickListener { finish() }
        findViewById<View>(R.id.sharePdfButton).setOnClickListener { pdfExporter.shareCurrentVerseAsPdf() }
        playMelodyButton.setOnClickListener { toggleMelodyPlayback() }
        val optionsButton = findViewById<View>(R.id.optionsButton)
        optionsButton.setOnClickListener { showAppSettingsDialog { onSettingsChanged() } }
        psalmPickerTextView.setOnClickListener { showPsalmMenu() }
        verseTitleTextView.setOnClickListener { openCurrentVerseOverview() }
        verseTitleTextView.setOnLongClickListener {
            showVerseMultiSelectDialog()
            true
        }
        previousVerseButton.setOnClickListener { openAdjacentVerse(previous = true) }
        nextVerseButton.setOnClickListener { openAdjacentVerse(previous = false) }
        findViewById<Button>(R.id.textDownButton).setOnClickListener { adjustLyricTextScale(-TEXT_SCALE_STEP) }
        findViewById<Button>(R.id.textUpButton).setOnClickListener { adjustLyricTextScale(TEXT_SCALE_STEP) }
        updateTopBarActionButtons()
        updatePlayButtonIcon()
        updateControls()
    }

    /**
     * Volledig scherm: verbergt de app-bovenbalk voor meer verticale ruimte.
     * In LANDSCAPE verdwijnt ook de statusbalk (de camera zit dan opzij); in PORTRAIT
     * blijft de statusbalk staan (anders schuift de bovenste notenbalk onder de camera).
     * Aan/uit via dubbeltik of het Android terug-gebaar.
     */
    private fun toggleFullscreen() {
        if (isFullscreen) exitFullscreen() else enterFullscreen()
    }

    private fun enterFullscreen() {
        if (isFullscreen) return
        isFullscreen = true
        findViewById<View>(R.id.topBar).visibility = View.GONE
        applyFullscreenSystemBars()
        fullscreenBackCallback.isEnabled = true
    }

    private fun exitFullscreen() {
        if (!isFullscreen) return
        isFullscreen = false
        findViewById<View>(R.id.topBar).visibility = View.VISIBLE
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
        fullscreenBackCallback.isEnabled = false
    }

    /** Statusbalk alleen verbergen bij volledig scherm én landscape; anders tonen. */
    private fun applyFullscreenSystemBars() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen && landscape) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Statusbalk opnieuw toepassen (landscape verbergt, portrait toont).
        applyFullscreenSystemBars()
        updateTopBarActionButtons()
        updateHeaderDropdownIndicators()
        // De bladmuziek herberekenen zodat die op de nieuwe breedte past.
        if (showNotes && !isTextOnlyAsset()) {
            webView.evaluateJavascript("updateScore();", null)
        }
    }

    private fun onSettingsChanged() {
        val newTempo = AppSettings.playbackTempo(this)
        val tempoChanged = newTempo != lastPlaybackTempo
        lastPlaybackTempo = newTempo
        val newTimbre = AppSettings.playbackTimbre(this)
        val timbreChanged = newTimbre != lastPlaybackTimbre
        lastPlaybackTimbre = newTimbre

        showNotes = !isTextOnlyAsset() && !AppSettings.textOnly(this)
        applyKeepScreenOn()
        applySheetTheme()
        updateTopBarActionButtons()
        updateLyricsText()
        updateContentMode()
        if ((tempoChanged || timbreChanged) && melodyPlayer.isPlaying) {
            startMelodyPlayback()
        }
        if (showNotes) {
            webView.evaluateJavascript("updateScore();", null)
        }
    }

    /** Bladmuziek donker als de instelling aan staat én het donkere thema actief is. */
    private fun isSheetDark(): Boolean =
        AppSettings.darkSheet(this) &&
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun applySheetTheme() {
        val dark = isSheetDark()
        webView.setBackgroundColor(ContextCompat.getColor(this, if (dark) R.color.sheet_dark_bg else R.color.white))
    }

    private fun applyKeepScreenOn() {
        if (AppSettings.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun displayTextScalePercent(scale: Double): String = displayRelativePercent(scale, DEFAULT_TEXT_SCALE)

    private fun displayNoteScalePercent(scale: Double): String = displayRelativePercent(scale, DEFAULT_NOTE_SCALE)

    private fun displayRelativePercent(scale: Double, defaultScale: Double): String = "${((scale / defaultScale) * 100).toInt()}%"

    private fun adjustLyricTextScale(delta: Double) {
        currentLyricTextScale = snapScale(currentLyricTextScale, delta, DEFAULT_TEXT_SCALE, TEXT_SCALE_STEP, MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        AppSettings.setTextScale(this, currentLyricTextScale)
        updateTextScaleLabel()
        applyTextOnlyTextSize()
        refreshScore()
    }

    /** Zet de schaal op een vast raster (standaard ± n·stap) zodat 100% altijd exact bereikbaar is. */
    private fun snapScale(current: Double, delta: Double, default: Double, step: Double, min: Double, max: Double): Double {
        val steps = Math.round((current - default) / step).toInt() + if (delta > 0) 1 else -1
        return (default + steps * step).coerceIn(min, max)
    }

    /** Rondt een (mogelijk oude, niet-uitgelijnde) opgeslagen schaal af naar het raster. */
    private fun roundScaleToGrid(value: Double, default: Double, step: Double, min: Double, max: Double): Double {
        val steps = Math.round((value - default) / step).toInt()
        return (default + steps * step).coerceIn(min, max)
    }

    private fun applyTextOnlyTextSize() {
        val sp = TEXT_ONLY_BASE_SP * (currentLyricTextScale / DEFAULT_TEXT_SCALE).toFloat()
        lyricsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        // Regelafstand schaalt mee met de zoom, zodat versregels (ook als ze ombreken)
        // duidelijk van elkaar te onderscheiden blijven.
        lyricsTextView.setLineSpacing(0f, 1.4f)
    }

    private fun adjustScoreVerticalScale(delta: Double) {
        currentScoreVerticalScale = snapScale(currentScoreVerticalScale, delta, DEFAULT_NOTE_SCALE, NOTE_SCALE_STEP, MIN_NOTE_SCALE, MAX_NOTE_SCALE)
        AppSettings.setNoteScale(this, currentScoreVerticalScale)
        refreshScore()
    }

    private fun refreshScore() {
        webView.evaluateJavascript("updateScore();", null)
    }

    private fun buildPdfTitle(): String {
        val verse = HymnRepository.verseByFileName(fileName)
        if (verse == null) return getString(R.string.app_name)
        if (shouldRenderStackedScoreInNotes(verse)) {
            val selectedNumbers = selectedVersesForCurrentSong(verse).map { it.verse }
            val versePart = selectedNumbers.joinToString("-")
            return "${verse.type} ${verse.number} verzen $versePart"
        }
        return "${verse.type} ${verse.number} vers ${verse.verse}"
    }

    private fun showVerseMenu() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val verses = HymnRepository.versesFor(currentVerse.type, currentVerse.number)
        if (verses.isEmpty()) return

        val currentIndex = verses.indexOfFirst { it.fileName == currentVerse.fileName }.coerceAtLeast(0)
        AnchoredChoicePopup.show(
            context = this,
            anchor = verseTitleTextView,
            labels = verses.map { "vers ${it.verse}" },
            currentIndex = currentIndex,
            minWidthDp = 128
        ) { position ->
            val verse = verses[position]
            if (verse.fileName != currentVerse.fileName) {
                stackedVerseFileNames = null
                openVerse(verse)
            }
        }
    }

    private fun openCurrentVerseOverview() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        startActivity(
            Intent(this, VerseListActivity::class.java)
                .putExtra(VerseListActivity.EXTRA_TYPE, currentVerse.type)
                .putExtra(VerseListActivity.EXTRA_NUMBER, currentVerse.number)
                .putExtra(VerseListActivity.EXTRA_FOCUS_VERSE, currentVerse.verse)
        )
    }

    /**
     * Long-press op verslabel: kies meerdere verzen binnen dezelfde psalm/gezang.
     * In tekstweergave worden gekozen verzen onder elkaar getoond.
     */
    private fun showVerseMultiSelectDialog() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val verses = HymnRepository.versesFor(currentVerse.type, currentVerse.number)
        if (verses.size <= 1) return

        val labels = verses.map { "Vers ${it.verse}" }.toTypedArray()
        val initialSelection = selectedVersesForCurrentSong(currentVerse).map { it.fileName }.toSet()
        val checked = BooleanArray(verses.size) { idx -> initialSelection.contains(verses[idx].fileName) }
        val allSelectedInitially = checked.all { it }

        AlertDialog.Builder(this)
            .setTitle("Kies verzen")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNeutralButton(if (allSelectedInitially) "Alles uit" else "Alles") { _, _ ->
                if (allSelectedInitially) {
                    resetToSingleVerseDefault()
                } else {
                    stackedVerseFileNames = LinkedHashSet(verses.map { it.fileName })
                    updateControls()
                    if (showNotes && !isTextOnlyAsset()) {
                        webView.evaluateJavascript("renderScore();", null)
                    }
                }
            }
            .setNegativeButton("Annuleren", null)
            .setPositiveButton("Toon") { _, _ ->
                val selectedFiles = verses.indices
                    .filter { checked[it] }
                    .map { verses[it].fileName }
                when {
                    selectedFiles.isEmpty() || selectedFiles.size == 1 -> {
                        stackedVerseFileNames = null
                        selectedFiles.firstOrNull()?.let { selected ->
                            if (selected != fileName) {
                                HymnRepository.verseByFileName(selected)?.let { openVerse(it) }
                                return@setPositiveButton
                            }
                        }
                    }
                    else -> stackedVerseFileNames = LinkedHashSet(selectedFiles)
                }
                updateControls()
                if (showNotes && !isTextOnlyAsset()) {
                    webView.evaluateJavascript("renderScore();", null)
                }
            }
            .show()
    }

    /** Keuzelijst voor psalm/gezang: houdt de normale nummering aan en scrollt naar de huidige. */
    private fun showPsalmMenu() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val numbers = HymnRepository.availableNumbers(currentVerse.type)
        if (numbers.isEmpty()) return

        AnchoredChoicePopup.show(
            context = this,
            anchor = psalmPickerTextView,
            labels = numbers.map { "${currentVerse.type} $it" },
            currentIndex = numbers.indexOf(currentVerse.number).coerceAtLeast(0),
            minWidthDp = 176
        ) { position ->
            val number = numbers[position]
            if (number != currentVerse.number) openNumber(currentVerse.type, number)
        }
    }

    private fun openNumber(type: String, number: Int) {
        val first = HymnRepository.versesFor(type, number).firstOrNull() ?: return
        stackedVerseFileNames = null
        openVerse(first)
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.settings.setSupportZoom(false)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = false
        webView.addJavascriptInterface(SheetMusicBridge(), "Android")
        gestureController.attach(webView, findViewById(R.id.lyricsScrollView))
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                // Alleen de ingebouwde asset-pagina toestaan; externe navigatie blokkeren.
                return !request.url?.toString().orEmpty().startsWith("file:///android_asset/")
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (isTextOnlyAsset()) {
                    showNotes = false
                    updateControls()
                } else {
                    view.evaluateJavascript("renderScore();", null)
                }
            }
        }
        webView.loadUrl("file:///android_asset/score_renderer.html")
    }

    private fun applyPinchScaleFactor(scaleFactor: Double) {
        currentLyricTextScale = (currentLyricTextScale * scaleFactor).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        currentScoreVerticalScale = (currentScoreVerticalScale * scaleFactor).coerceIn(MIN_NOTE_SCALE, MAX_NOTE_SCALE)
        applyPinchScale()
    }

    private fun savePinchScale() {
        AppSettings.setTextScale(this, currentLyricTextScale)
        AppSettings.setNoteScale(this, currentScoreVerticalScale)
        updateTextScaleLabel()
    }

    private fun applyPinchScale() {
        updateTextScaleLabel()
        if (showNotes && !isTextOnlyAsset()) {
            webView.evaluateJavascript("updateScore();", null)
        } else {
            applyTextOnlyTextSize()
        }
    }

    private fun openAdjacentVerse(previous: Boolean) {
        val adjacentVerse = if (previous) {
            HymnRepository.previousVerse(fileName)
        } else {
            HymnRepository.nextVerse(fileName)
        } ?: return

        openVerse(adjacentVerse)
    }

    private fun openVerse(verse: Verse) {
        if (melodyPlayer.isPlaying) {
            stopMelodyPlayback()
        }
        val oldVerse = HymnRepository.verseByFileName(fileName)
        val selectedFilesBeforeNavigate = stackedVerseFileNames
        if (selectedFilesBeforeNavigate != null &&
            selectedFilesBeforeNavigate.size > 1 &&
            !selectedFilesBeforeNavigate.contains(verse.fileName)
        ) {
            // Bij swipen buiten de actieve multi-select vervalt de selectie.
            stackedVerseFileNames = null
        }
        fileName = verse.fileName
        if (oldVerse == null || oldVerse.type != verse.type || oldVerse.number != verse.number) {
            stackedVerseFileNames = null
        }
        showNotes = !isTextOnlyAsset() && !AppSettings.textOnly(this)
        updateControls()
        updateLyricsText()
        if (!isTextOnlyAsset()) {
            webView.evaluateJavascript("renderScore();", null)
        }
    }

    private fun transposeBy(semitones: Int) {
        currentTransposition = (currentTransposition + semitones).coerceIn(-12, 12)
        updateControls()
        webView.evaluateJavascript("updateScore();", null)
    }

    private fun updateControls() {
        updateTitle()
        updateTextScaleLabel()
        updatePlayButtonIcon()
        updateLyricsText()
        updateContentMode()
        updateVerseMatrix()
        previousVerseButton.isEnabled = HymnRepository.previousVerse(fileName) != null
        nextVerseButton.isEnabled = HymnRepository.nextVerse(fileName) != null
    }

    private fun updateVerseMatrix() {
        verseMatrixGrid.removeAllViews()
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val verses = HymnRepository.versesFor(currentVerse.type, currentVerse.number)
        if (verses.isEmpty()) return

        verseMatrixGrid.columnCount = minOf(8, verses.size.coerceAtLeast(1))
        verses.forEach { verse ->
            val isCurrent = verse.fileName == fileName
            val button = Button(this).apply {
                text = verse.verse.toString()
                textSize = 13f
                isAllCaps = false
                minWidth = 0
                minHeight = 0
                setPadding(0, 0, 0, 0)
                isEnabled = !isCurrent
                if (isCurrent) {
                    setTextColor(Color.WHITE)
                    setBackgroundResource(R.drawable.bg_verse_current)
                } else {
                    setTextColor(androidx.core.content.ContextCompat.getColor(this@SheetMusicActivity, R.color.app_text_secondary))
                    setBackgroundColor(Color.TRANSPARENT)
                }
                setOnClickListener { openVerse(verse) }
            }
            verseMatrixGrid.addView(
                button,
                GridLayout.LayoutParams().apply {
                    width = dpToPx(36)
                    height = dpToPx(32)
                    setMargins(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3))
                }
            )
        }
    }

    private fun updateContentMode() {
        val verse = HymnRepository.verseByFileName(fileName)
        val notesVisible = showNotes && !isTextOnlyAsset()
        webView.visibility = if (notesVisible) View.VISIBLE else View.GONE
        lyricsContainer.visibility = if (notesVisible) View.GONE else View.VISIBLE
        controlsPanel.visibility = if (notesVisible) View.GONE else View.VISIBLE
    }

    private fun updateLyricsText() {
        lyricsTextView.text = buildStyledLyrics()
        applyTextOnlyTextSize()
    }

    /**
     * Bouwt de alleen-tekst weergave rechtstreeks uit het scoremodel (JSON tokens),
        * zodat apostroffen correct blijven en kwartnoot-lettergrepen cursief worden
        * getoond, net als in de notenweergave.
     */
    private fun buildStyledLyrics(): CharSequence {
        val verse = HymnRepository.verseByFileName(fileName) ?: return fallbackLyrics()
        if (isStackedVerseMode(verse) && !showNotes) {
            val selected = selectedVersesForCurrentSong(verse)
            if (selected.isNotEmpty()) {
                val all = SpannableStringBuilder()
                selected.forEachIndexed { index, selectedVerse ->
                    all.append(buildStyledLyricsForVerse(selectedVerse))
                    if (index < selected.lastIndex) all.append("\n\n")
                }
                return all
            }
        }

        return buildStyledLyricsForVerse(verse)
    }

    private fun buildStyledLyricsForVerse(verse: Verse): CharSequence {
        val sb = SpannableStringBuilder()
        sb.append("Vers ${verse.verse}\n")
        try {
            val model = JSONObject(ScoreBundleRenderer.readScoreModel(this, verse))
            val lines = model.getJSONArray("lines")
            for (li in 0 until lines.length()) {
                val slots = lines.getJSONObject(li).getJSONArray("slots")
                var wroteOnLine = false
                for (si in 0 until slots.length()) {
                    val slot = slots.getJSONObject(si)
                    if (slot.optBoolean("rest")) continue
                    val text = slot.optString("text")
                    if (text.isEmpty()) {
                        // In alleen-tekstweergave melisma zichtbaar maken, maar
                        // reeksen beperken tot één streepje.
                        if (wroteOnLine && (sb.isEmpty() || sb[sb.length - 1] != '-')) {
                            sb.append("-")
                        }
                        continue
                    }
                    val syllabic = slot.optString("syllabic", "single")
                    val startsNewWord = syllabic == "single" || syllabic == "begin"
                    if (startsNewWord && wroteOnLine) sb.append(" ")
                    val notes = slot.optJSONArray("notes")
                    val isQuarter = notes != null && notes.length() == 1 &&
                        notes.getJSONObject(0).optString("type") == "quarter"
                    val start = sb.length
                    sb.append(text)
                    if (isQuarter) {
                        sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    wroteOnLine = true
                }
                if (li < lines.length() - 1) sb.append("\n")
            }
        } catch (e: Exception) {
            return "Vers ${verse.verse}\n" +
                ScoreBundleRenderer.readVerseText(this, verse).ifBlank { verse.firstLine }
        }
        return sb
    }

    private fun isStackedVerseMode(currentVerse: Verse): Boolean {
        val files = stackedVerseFileNames ?: return false
        if (files.size <= 1) return false
        val available = HymnRepository.versesFor(currentVerse.type, currentVerse.number).map { it.fileName }.toSet()
        return files.any { available.contains(it) }
    }

    private fun selectedVersesForCurrentSong(currentVerse: Verse): List<Verse> {
        val verses = HymnRepository.versesFor(currentVerse.type, currentVerse.number)
        val files = stackedVerseFileNames ?: return listOf(currentVerse)
        val selected = verses.filter { files.contains(it.fileName) }
        return if (selected.isEmpty()) listOf(currentVerse) else selected
    }

    private fun fallbackLyrics(): CharSequence = buildString {
        val verse = HymnRepository.verseByFileName(fileName)
        if (verse == null) {
            append(ContentStorage.readBundledAsset(this@SheetMusicActivity, fileName).trim())
            return@buildString
        }
        append("Vers ${verse.verse}\n")
        append(ScoreBundleRenderer.readVerseText(this@SheetMusicActivity, verse).ifBlank { verse.firstLine })
    }

    private fun isTextOnlyAsset(): Boolean = fileName.endsWith(".txt", ignoreCase = true)

    private fun updateTextScaleLabel() {
        textScaleTextView.text = displayTextScalePercent(currentLyricTextScale)
    }

    private fun updateTitle() {
        val verse = HymnRepository.verseByFileName(fileName)
        val activityTitle = if (verse == null) {
            getString(R.string.app_name)
        } else {
            "${verse.type} ${verse.number}:${verse.verse}"
        }
        title = activityTitle
        psalmPickerTextView.text = when {
            verse == null -> getString(R.string.app_name)
            verse.type == "Gezang" -> HymnRepository.hymnAbbreviation(verse.number) ?: "${verse.type} ${verse.number}"
            else -> "${verse.type} ${verse.number}"
        }
        verseTitleTextView.text = when {
            verse == null -> "vers -"
            isStackedVerseMode(verse) -> {
                val picked = selectedVersesForCurrentSong(verse).map { it.verse }
                if (picked.size > 4) "verzen ${picked.first()}-${picked.last()}" else "verzen ${picked.joinToString(",")}" 
            }
            else -> "vers ${verse.verse}"
        }
        updateHeaderDropdownIndicators()
    }

    private fun updateHeaderDropdownIndicators() {
        val showDropdown = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            resources.configuration.screenWidthDp >= 600
        val icon = if (showDropdown) R.drawable.ic_arrow_drop_down else 0
        listOf(psalmPickerTextView, verseTitleTextView).forEach { view ->
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)
            view.compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
            view.compoundDrawablePadding = 0
        }
    }

    private fun updateTopBarActionButtons() {
        val shareButton = findViewById<View>(R.id.sharePdfButton)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            shareButton.visibility = View.VISIBLE
            playMelodyButton.visibility = View.VISIBLE
            return
        }
        if (AppSettings.topBarActionIcon(this) == AppSettings.TOPBAR_ACTION_PLAY) {
            shareButton.visibility = View.GONE
            playMelodyButton.visibility = View.VISIBLE
        } else {
            shareButton.visibility = View.VISIBLE
            playMelodyButton.visibility = View.GONE
        }
    }

    private fun toggleMelodyPlayback() {
        if (melodyPlayer.isPlaying) {
            stopMelodyPlayback()
        } else {
            startMelodyPlayback()
        }
    }

    private fun startMelodyPlayback() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val verses = if (isStackedVerseMode(currentVerse)) {
            selectedVersesForCurrentSong(currentVerse)
        } else {
            listOf(currentVerse)
        }
        val events = MelodyPlaybackModel.buildEvents(
            context = this,
            verses = verses,
            tempoPercent = AppSettings.playbackTempo(this),
            transposeSemitones = currentTransposition
        )
        if (events.isEmpty()) return
        melodyPlayer.play(events, currentTimbre()) { _ ->
            runOnUiThread { updatePlayButtonIcon() }
        }
        updatePlayButtonIcon()
    }

    private fun currentTimbre(): MelodyTimbre = when (AppSettings.playbackTimbre(this)) {
        AppSettings.TIMBRE_HOLPIJP -> MelodyTimbre.HOLPIJP
        AppSettings.TIMBRE_FLUIT -> MelodyTimbre.FLUIT
        AppSettings.TIMBRE_STRINGS -> MelodyTimbre.STRINGS
        AppSettings.TIMBRE_VOL16 -> MelodyTimbre.VOL16
        else -> MelodyTimbre.PRESTANT
    }

    private fun stopMelodyPlayback() {
        melodyPlayer.stop { _ ->
            runOnUiThread { updatePlayButtonIcon() }
        }
        updatePlayButtonIcon()
    }

    private fun updatePlayButtonIcon() {
        playMelodyButton.setImageResource(if (melodyPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun resetToSingleVerseDefault() {
        val currentVerse = HymnRepository.verseByFileName(fileName)
        stackedVerseFileNames = null
        if (currentVerse == null) {
            updateControls()
            if (showNotes && !isTextOnlyAsset()) {
                webView.evaluateJavascript("renderScore();", null)
            }
            return
        }
        val firstVerse = HymnRepository.versesFor(currentVerse.type, currentVerse.number).firstOrNull()
        if (firstVerse != null && firstVerse.fileName != fileName) {
            openVerse(firstVerse)
        } else {
            updateControls()
            if (showNotes && !isTextOnlyAsset()) {
                webView.evaluateJavascript("renderScore();", null)
            }
        }
    }

    inner class SheetMusicBridge {
        @JavascriptInterface
        fun getScoreModel(): String {
            return try {
                val verse = HymnRepository.verseByFileName(fileName) ?: return "{}"
                val model = if (shouldRenderStackedScoreInNotes(verse)) {
                    buildMergedScoreModel(selectedVersesForCurrentSong(verse))
                } else {
                    JSONObject(ScoreBundleRenderer.readScoreModel(this@SheetMusicActivity, verse))
                }
                if (currentTransposition != 0) {
                    ScoreTransposer.transpose(model, currentTransposition)
                }
                model.toString()
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "onbekende fout").toString()
            }
        }

        @JavascriptInterface
        fun getVerseMatrixItems(): String {
            val currentVerse = HymnRepository.verseByFileName(fileName) ?: return "[]"
            val verses = HymnRepository.versesFor(currentVerse.type, currentVerse.number)
            return JSONArray(verses.map { verse ->
                JSONObject()
                    .put("verse", verse.verse)
                    .put("fileName", verse.fileName)
                    .put("current", verse.fileName == fileName)
            }).toString()
        }

        @JavascriptInterface
        fun openVerseFile(nextFileName: String) {
            runOnUiThread {
                HymnRepository.verseByFileName(nextFileName)?.let { openVerse(it) }
            }
        }

        @JavascriptInterface
        fun hasPreviousVerse(): Boolean = HymnRepository.previousVerse(fileName) != null

        @JavascriptInterface
        fun hasNextVerse(): Boolean = HymnRepository.nextVerse(fileName) != null

        @JavascriptInterface
        fun openPreviousVerse() {
            runOnUiThread { openAdjacentVerse(previous = true) }
        }

        @JavascriptInterface
        fun openNextVerse() {
            runOnUiThread { openAdjacentVerse(previous = false) }
        }

        @JavascriptInterface
        fun transposeBy(semitones: Int) {
            runOnUiThread { this@SheetMusicActivity.transposeBy(semitones) }
        }

        @JavascriptInterface
        fun getTranspositionLabel(): String = currentTransposition.toString()

        @JavascriptInterface
        fun adjustTextScale(delta: Double) {
            runOnUiThread { adjustLyricTextScale(delta) }
        }

        @JavascriptInterface
        fun adjustNoteScale(delta: Double) {
            runOnUiThread { adjustScoreVerticalScale(delta) }
        }

        @JavascriptInterface
        fun getTextScaleLabel(): String = displayTextScalePercent(currentLyricTextScale)

        @JavascriptInterface
        fun getNoteScaleLabel(): String = displayNoteScalePercent(currentScoreVerticalScale)

        @JavascriptInterface
        fun getLyricTextScale(): Double = currentLyricTextScale

        @JavascriptInterface
        fun getScoreVerticalScale(): Double = currentScoreVerticalScale

        @JavascriptInterface
        fun getLargeText(): Boolean = AppSettings.largeText(this@SheetMusicActivity)

        @JavascriptInterface
        fun getAllowLineWrap(): Boolean = AppSettings.allowLineWrap(this@SheetMusicActivity)

        @JavascriptInterface
        fun getSheetDark(): Boolean = !exportingPdf && this@SheetMusicActivity.isSheetDark()

        @JavascriptInterface
        fun getShowRests(): Boolean = AppSettings.showRests(this@SheetMusicActivity)

        @JavascriptInterface
        fun getShowLyrics(): Boolean = exportingPdf || AppSettings.showLyrics(this@SheetMusicActivity)

        @JavascriptInterface
        fun getTextAlign(): Int = AppSettings.textAlign(this@SheetMusicActivity)

        @JavascriptInterface
        fun getCombineLines(): Boolean = AppSettings.combineLines(this@SheetMusicActivity)

        @JavascriptInterface
        fun toggleFullscreen() {
            runOnUiThread { this@SheetMusicActivity.toggleFullscreen() }
        }
    }

    private fun shouldRenderStackedScoreInNotes(currentVerse: Verse): Boolean {
        if (!showNotes || isTextOnlyAsset()) return false
        if (!AppSettings.showLyrics(this)) return false
        return isStackedVerseMode(currentVerse)
    }

    private fun preferSinglePdfPage(): Boolean {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return true
        if (!shouldRenderStackedScoreInNotes(currentVerse)) return true
        return selectedVersesForCurrentSong(currentVerse).size <= 2
    }

    private fun buildMergedScoreModel(verses: List<Verse>): JSONObject {
        if (verses.isEmpty()) return JSONObject()

        val firstModel = JSONObject(ScoreBundleRenderer.readScoreModel(this, verses.first()))
        val mergedLines = JSONArray()

        verses.forEach { selectedVerse ->
            val verseModel = JSONObject(ScoreBundleRenderer.readScoreModel(this, selectedVerse))
            val verseLines = verseModel.optJSONArray("lines") ?: JSONArray()
            for (index in 0 until verseLines.length()) {
                val copiedLine = JSONObject(verseLines.getJSONObject(index).toString())
                if (index == 0) {
                    copiedLine.put("verseStart", true)
                    copiedLine.put("verseNumber", selectedVerse.verse)
                }
                if (index == verseLines.length() - 1) {
                    copiedLine.put("verseEnd", true)
                }
                mergedLines.put(copiedLine)
            }
        }

        firstModel.put("multiVerse", verses.size > 1)
        firstModel.put("lines", mergedLines)
        return firstModel
    }

    companion object {
        const val EXTRA_FILE_NAME = "nl.psalmbladmuziek.app.extra.FILE_NAME"
        private const val DEFAULT_FILE_NAME = "Psalm001_v1.json"
        private const val DEFAULT_TEXT_SCALE = 1.75
        private const val DEFAULT_NOTE_SCALE = 0.8
        private const val TEXT_ONLY_BASE_SP = 15f
        private const val MIN_TEXT_SCALE = 0.75
        private const val MAX_TEXT_SCALE = 2.4
        private const val TEXT_SCALE_STEP = 0.2
        private const val MIN_NOTE_SCALE = 0.5
        private const val MAX_NOTE_SCALE = 1.2
        private const val NOTE_SCALE_STEP = 0.1
    }

    override fun onStop() {
        super.onStop()
        if (melodyPlayer.isPlaying) {
            stopMelodyPlayback()
        }
    }

    override fun onDestroy() {
        melodyPlayer.stop()
        super.onDestroy()
    }

}