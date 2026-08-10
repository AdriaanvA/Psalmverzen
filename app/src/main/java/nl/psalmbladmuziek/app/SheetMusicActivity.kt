package nl.psalmbladmuziek.app

import android.graphics.Color
import android.graphics.Typeface
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.OnBackPressedCallback
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.FileProvider
import java.io.File

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
    private lateinit var fileName: String
    private var currentLyricTextScale = DEFAULT_TEXT_SCALE
    private var currentScoreVerticalScale = DEFAULT_NOTE_SCALE
    private var currentTransposition = 0
    private var showNotes = true
    private var exportingPdf = false
    private var pdfExportInProgress = false

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
        currentLyricTextScale = roundScaleToGrid(AppSettings.textScale(this, DEFAULT_TEXT_SCALE), DEFAULT_TEXT_SCALE, TEXT_SCALE_STEP, MIN_TEXT_SCALE, MAX_TEXT_SCALE)
        currentScoreVerticalScale = roundScaleToGrid(AppSettings.noteScale(this, DEFAULT_NOTE_SCALE), DEFAULT_NOTE_SCALE, NOTE_SCALE_STEP, MIN_NOTE_SCALE, MAX_NOTE_SCALE)
        showNotes = !isTextOnlyAsset() && !AppSettings.textOnly(this)
        applyKeepScreenOn()
        HymnRepository.refreshDownloadedContent(this)
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

        setupButtons()
        setupWebView()
        applySheetTheme()
        onBackPressedDispatcher.addCallback(this, fullscreenBackCallback)
    }

    private fun setupButtons() {
        findViewById<View>(R.id.backToIndexButton).setOnClickListener { finish() }
        findViewById<View>(R.id.sharePdfButton).setOnClickListener { shareCurrentVerseAsPdf() }
        val optionsButton = findViewById<View>(R.id.optionsButton)
        optionsButton.setOnClickListener { showAppSettingsDialog { onSettingsChanged() } }
        psalmPickerTextView.setOnClickListener { showPsalmMenu() }
        verseTitleTextView.setOnClickListener { showVerseMenu() }
        previousVerseButton.setOnClickListener { openAdjacentVerse(previous = true) }
        nextVerseButton.setOnClickListener { openAdjacentVerse(previous = false) }
        findViewById<Button>(R.id.textDownButton).setOnClickListener { adjustLyricTextScale(-TEXT_SCALE_STEP) }
        findViewById<Button>(R.id.textUpButton).setOnClickListener { adjustLyricTextScale(TEXT_SCALE_STEP) }
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
        updateHeaderDropdownIndicators()
        // De bladmuziek herberekenen zodat die op de nieuwe breedte past.
        if (showNotes && !isTextOnlyAsset()) {
            webView.evaluateJavascript("updateScore();", null)
        }
    }

    private fun onSettingsChanged() {
        showNotes = !isTextOnlyAsset() && !AppSettings.textOnly(this)
        applyKeepScreenOn()
        applySheetTheme()
        updateLyricsText()
        updateContentMode()
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
        webView.setBackgroundColor(if (dark) 0xFF121212.toInt() else Color.WHITE)
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

    private fun scaleChangeLabel(current: Double, delta: Double, min: Double, max: Double, formatter: (Double) -> String): String {
        val next = (current + delta).coerceIn(min, max)
        return "${formatter(current)} -> ${formatter(next)}"
    }

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

    private fun shareCurrentVerseAsPdf() {
        if (pdfExportInProgress) return
        if (isTextOnlyAsset()) {
            Toast.makeText(this, "Voor dit item is geen bladmuziek-PDF beschikbaar.", Toast.LENGTH_SHORT).show()
            return
        }
        pdfExportInProgress = true
        exportingPdf = true
        Toast.makeText(this, "PDF wordt gemaakt...", Toast.LENGTH_SHORT).show()
        webView.evaluateJavascript("window.__pdfExport = true; document.documentElement.classList.add('pdf-export'); updateScore();") {
            webView.postDelayed({
                webView.scrollTo(0, 0)
                writeCurrentWebViewToPdf()
            }, 250)
        }
    }

    private fun writeCurrentWebViewToPdf() {
        val sharedDir = File(cacheDir, "shared").apply { mkdirs() }
        val file = File(sharedDir, buildPdfFileName())
        if (file.exists()) file.delete()

        try {
            @Suppress("DEPRECATION")
            val picture = webView.capturePicture()
            if (picture.width <= 0 || picture.height <= 0) {
                throw IllegalStateException("PDF-bron is leeg.")
            }
            val pageWidth = 595
            val pageHeight = 842
            val margin = 28f
            val scale = (pageWidth - margin * 2) / picture.width.toFloat().coerceAtLeast(1f)
            val sourcePageHeight = (pageHeight - margin * 2) / scale
            val pageCount = kotlin.math.ceil(picture.height / sourcePageHeight).toInt().coerceAtLeast(1)
            val document = PdfDocument()

            for (pageIndex in 0 until pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.translate(margin, margin)
                page.canvas.scale(scale, scale)
                page.canvas.translate(0f, -pageIndex * sourcePageHeight)
                picture.draw(page.canvas)
                document.finishPage(page)
            }

            file.outputStream().use { document.writeTo(it) }
            document.close()
            finishPdfExport()
            sharePdfFile(file)
        } catch (e: Exception) {
            finishPdfExport()
            Toast.makeText(this, e.message ?: "PDF maken is mislukt.", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishPdfExport() {
        pdfExportInProgress = false
        exportingPdf = false
        if (showNotes && !isTextOnlyAsset()) {
            webView.evaluateJavascript("window.__pdfExport = false; document.documentElement.classList.remove('pdf-export'); updateScore();", null)
        }
    }

    private fun sharePdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, buildPdfTitle())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "PDF delen"))
    }

    private fun buildPdfTitle(): String {
        val verse = HymnRepository.verseByFileName(fileName)
        return if (verse == null) getString(R.string.app_name) else "${verse.type} ${verse.number} vers ${verse.verse}"
    }

    private fun buildPdfFileName(): String = buildPdfTitle()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "psalmverzen" } + ".pdf"

    private fun showVerseMenu() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val verses = HymnRepository.versesFor(currentVerse.type, currentVerse.number)
        if (verses.isEmpty()) return

        PopupMenu(this, verseTitleTextView).apply {
            itemsFromCurrent(verses, currentVerse) { it.fileName }.forEach { verse ->
                val item = menu.add("vers ${verse.verse}")
                if (verse.fileName == currentVerse.fileName) {
                    item.isCheckable = true
                    item.isChecked = true
                }
                item.setOnMenuItemClickListener {
                    if (verse.fileName != currentVerse.fileName) openVerse(verse)
                    true
                }
            }
            show()
        }
    }

    /** Keuzelijst voor psalm/gezang: begint bij de huidige en loopt oplopend door,
     *  zodat je snel naar een volgende/latere psalm of gezang kunt springen. */
    private fun showPsalmMenu() {
        val currentVerse = HymnRepository.verseByFileName(fileName) ?: return
        val numbers = HymnRepository.availableNumbers(currentVerse.type)
        if (numbers.isEmpty()) return

        PopupMenu(this, psalmPickerTextView).apply {
            itemsFromCurrent(numbers, currentVerse.number) { it }.forEach { number ->
                val item = menu.add("${currentVerse.type} $number")
                if (number == currentVerse.number) {
                    item.isCheckable = true
                    item.isChecked = true
                }
                item.setOnMenuItemClickListener {
                    if (number != currentVerse.number) openNumber(currentVerse.type, number)
                    true
                }
            }
            show()
        }
    }

    private fun <T, K> itemsFromCurrent(items: List<T>, current: T, keyOf: (T) -> K): List<T> {
        val currentKey = keyOf(current)
        val currentIndex = items.indexOfFirst { keyOf(it) == currentKey }
        if (currentIndex <= 0) return items
        return items.drop(currentIndex) + items.take(currentIndex)
    }

    private fun openNumber(type: String, number: Int) {
        val first = HymnRepository.versesFor(type, number).firstOrNull() ?: return
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
        setupGestures()
        webView.webViewClient = object : WebViewClient() {
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

    // --- Gebaren: horizontaal vegen = vorige/volgende vers; knijpen = noten+tekst samen zoomen ---
    private val swipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) > Math.abs(dy) * 1.5f && Math.abs(dx) > 150f && Math.abs(vx) > 250f) {
                    openAdjacentVerse(previous = dx > 0) // vegen naar rechts = vorige, naar links = volgende
                    return true
                }
                return false
            }
        })
    }

    // Dubbeltik = volledig scherm aan/uit. In de notenweergave wordt dit in JavaScript
    // afgehandeld (alleen op de notenbalk/tekst, niet op de knoppen). Deze native detector
    // is alleen voor de tekst-only weergave (op het tekstgebied).
    private val doubleTapDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleFullscreen()
                return true
            }
        })
    }

    private val pinchDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val f = detector.scaleFactor.toDouble()
                currentLyricTextScale = (currentLyricTextScale * f).coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
                currentScoreVerticalScale = (currentScoreVerticalScale * f).coerceIn(MIN_NOTE_SCALE, MAX_NOTE_SCALE)
                applyPinchScale()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                AppSettings.setTextScale(this@SheetMusicActivity, currentLyricTextScale)
                AppSettings.setNoteScale(this@SheetMusicActivity, currentScoreVerticalScale)
                updateTextScaleLabel()
            }
        })
    }

    private fun applyPinchScale() {
        updateTextScaleLabel()
        if (showNotes && !isTextOnlyAsset()) {
            webView.evaluateJavascript("updateScore();", null)
        } else {
            applyTextOnlyTextSize()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val touch = View.OnTouchListener { v, ev ->
            pinchDetector.onTouchEvent(ev)
            if (!pinchDetector.isInProgress) {
                swipeDetector.onTouchEvent(ev)
                // Dubbeltik-naar-fullscreen alleen op het tekstgebied (tekst-only weergave);
                // in de notenweergave doet JavaScript dit op de notenbalk/tekst.
                if (v.id == R.id.lyricsScrollView) {
                    doubleTapDetector.onTouchEvent(ev)
                }
            }
            false
        }
        webView.setOnTouchListener(touch)
        findViewById<View>(R.id.lyricsScrollView).setOnTouchListener(touch)
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
        fileName = verse.fileName
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

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateContentMode() {
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
     * niet via de MusicXML-omweg. Zo blijven apostroffen correct ('' i.p.v. &apos;)
     * en worden kwartnoot-lettergrepen cursief getoond, net als in de notenweergave.
     */
    private fun buildStyledLyrics(): CharSequence {
        val verse = HymnRepository.verseByFileName(fileName)
        val sb = SpannableStringBuilder()
        if (verse != null) {
            sb.append("Vers ${verse.verse}\n")
        }
        try {
            val model = JSONObject(ScoreBundleRenderer.readScoreModel(this, verse ?: return fallbackLyrics()))
            val lines = model.getJSONArray("lines")
            for (li in 0 until lines.length()) {
                val slots = lines.getJSONObject(li).getJSONArray("slots")
                var wroteOnLine = false
                for (si in 0 until slots.length()) {
                    val slot = slots.getJSONObject(si)
                    if (slot.optBoolean("rest")) continue
                    val text = slot.optString("text")
                    if (text.isEmpty()) continue
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
            return fallbackLyrics()
        }
        return sb
    }

    private fun fallbackLyrics(): CharSequence = buildString {
        val verse = HymnRepository.verseByFileName(fileName)
        if (verse != null) append("Vers ${verse.verse}\n")
        append(readDisplayText())
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
        psalmPickerTextView.text = if (verse == null) getString(R.string.app_name) else "${verse.type} ${verse.number}"
        verseTitleTextView.text = if (verse == null) "vers -" else "vers ${verse.verse}"
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

    private fun readAsset(assetFileName: String): String = stripInstrumentLabels(
        readRawMusicXml(assetFileName)
    )

    private fun readRawMusicXml(assetFileName: String): String {
        val verse = HymnRepository.verseByFileName(assetFileName)
        return if (verse == null) {
            ContentStorage.readMusicXml(this, assetFileName)
        } else {
            ScoreBundleRenderer.readMusicXml(this, verse)
        }
    }

    private fun readDisplayText(): String = if (isTextOnlyAsset()) {
        ContentStorage.readMusicXml(this, fileName).trim()
    } else {
        SheetMusicXmlUtils.extractLyrics(
            musicXml = readAsset(fileName),
            fallbackFirstLine = HymnRepository.verseByFileName(fileName)?.firstLine.orEmpty()
        )
    }

    private fun stripInstrumentLabels(musicXml: String): String =
        SheetMusicXmlUtils.stripInstrumentLabels(musicXml)

    private fun transposeKeyFifths(fifths: Int, semitones: Int): Int {
        val targetPitchClass = Math.floorMod(fifths * 7 + semitones, 12)
        return KEY_FIFTHS_BY_PITCH_CLASS[targetPitchClass]
    }

    private fun transposeScoreModel(model: JSONObject, semitones: Int) {
        val originalFifths = model.optInt("fifths", 0)
        val targetFifths = transposeKeyFifths(originalFifths, semitones)
        val preferFlats = targetFifths < 0
        model.put("fifths", targetFifths)

        val lines = model.optJSONArray("lines") ?: return
        for (i in 0 until lines.length()) {
            val slots = lines.getJSONObject(i).optJSONArray("slots") ?: continue
            for (j in 0 until slots.length()) {
                val notes = slots.getJSONObject(j).optJSONArray("notes") ?: continue
                for (k in 0 until notes.length()) {
                    val note = notes.getJSONObject(k)
                    if (note.optBoolean("rest")) continue
                    transposeModelNote(note, semitones, preferFlats)
                }
            }
        }
    }

    private fun transposeModelNote(note: JSONObject, semitones: Int, preferFlats: Boolean) {
        val step = note.optString("step").ifBlank { return }
        val alter = note.optInt("alter", 0)
        val octave = note.optInt("octave")
        val pitchClass = STEP_PITCH_CLASSES.getValue(step) + alter
        val transposedMidi = octave * 12 + pitchClass + semitones
        val transposedPitchClass = Math.floorMod(transposedMidi, 12)
        val transposedOctave = Math.floorDiv(transposedMidi, 12)
        val spelling = if (preferFlats) FLAT_SPELLINGS[transposedPitchClass] else SHARP_SPELLINGS[transposedPitchClass]
        note.put("step", spelling.step)
        note.put("alter", spelling.alter)
        note.put("octave", transposedOctave)
    }

    inner class SheetMusicBridge {
        @JavascriptInterface
        fun getScoreModel(): String {
            return try {
                val verse = HymnRepository.verseByFileName(fileName) ?: return "{}"
                val model = JSONObject(ScoreBundleRenderer.readScoreModel(this@SheetMusicActivity, verse))
                if (currentTransposition != 0) {
                    transposeScoreModel(model, currentTransposition)
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
        private val STEP_PITCH_CLASSES = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)
        private val KEY_FIFTHS_BY_PITCH_CLASS = intArrayOf(0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5)
        private val SHARP_SPELLINGS = arrayOf(
            PitchSpelling("C", 0), PitchSpelling("C", 1), PitchSpelling("D", 0), PitchSpelling("D", 1),
            PitchSpelling("E", 0), PitchSpelling("F", 0), PitchSpelling("F", 1), PitchSpelling("G", 0),
            PitchSpelling("G", 1), PitchSpelling("A", 0), PitchSpelling("B", -1), PitchSpelling("B", 0)
        )
        private val FLAT_SPELLINGS = arrayOf(
            PitchSpelling("C", 0), PitchSpelling("D", -1), PitchSpelling("D", 0), PitchSpelling("E", -1),
            PitchSpelling("E", 0), PitchSpelling("F", 0), PitchSpelling("F", 1), PitchSpelling("G", 0),
            PitchSpelling("A", -1), PitchSpelling("A", 0), PitchSpelling("B", -1), PitchSpelling("B", 0)
        )
    }

    private data class PitchSpelling(val step: String, val alter: Int)

}