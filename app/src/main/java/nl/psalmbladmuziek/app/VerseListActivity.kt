package nl.psalmbladmuziek.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AbsListView
import android.widget.GridView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VerseListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_verse_list)

        bindStatusBarBackground(findViewById(R.id.verseListRoot), findViewById(R.id.statusBarBackground))

        val type = intent.getStringExtra(EXTRA_TYPE) ?: "Psalm"
        val number = intent.getIntExtra(EXTRA_NUMBER, 1)
        val focusVerse = intent.getIntExtra(EXTRA_FOCUS_VERSE, -1)
        val returnToSheet = intent.getBooleanExtra(EXTRA_RETURN_TO_SHEET, false)

        val verses = HymnRepository.versesFor(type, number)
        val displayLinesByVerse = ScoreBundleRenderer.readVerseLines(this, type, number)
        val title = verses.firstOrNull { it.title.isNotBlank() }?.title ?: "$type $number"
        findViewById<TextView>(R.id.titleTextView).text = title

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        val about = ScoreBundleRenderer.readAbout(this, type, number)
        val aboutButton = findViewById<View>(R.id.aboutButton)
        if (about != null) {
            aboutButton.visibility = View.VISIBLE
            aboutButton.contentDescription = "Over dit lied"
            aboutButton.setOnClickListener { showAboutDialog(title, about) }
        } else {
            aboutButton.visibility = View.GONE
        }

        val listView = findViewById<ListView>(R.id.verseListView)
        val gridView = findViewById<GridView>(R.id.verseGridView)
        attachNumberSwipeNavigation(type, number, listView, gridView, findViewById(R.id.verseListRoot))
        if (type == "Psalm" && verses.size > VERSE_GRID_THRESHOLD) {
            // Veel verzen (bv. Psalm 78/119): compact nummerraster i.p.v. een lange lijst,
            // zodat er minder gescrold hoeft te worden om een vers te kiezen.
            listView.visibility = View.GONE
            gridView.visibility = View.VISIBLE
            gridView.numColumns = AppSettings.psalmGridColumns(this)
            gridView.verticalSpacing = 0
            gridView.horizontalSpacing = 0
            gridView.stretchMode = GridView.STRETCH_COLUMN_WIDTH
            gridView.adapter = object : ArrayAdapter<String>(
                this,
                R.layout.item_number_grid,
                verses.map { it.verse.toString() }
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val itemView = super.getView(position, convertView, parent)
                    val columns = AppSettings.psalmGridColumns(this@VerseListActivity)
                    val tileWidth = ((parent.width - parent.paddingLeft - parent.paddingRight) / columns.toFloat()).toInt()
                    if (tileWidth > 0) {
                        itemView.layoutParams = (itemView.layoutParams ?: AbsListView.LayoutParams(-1, -2)).apply {
                            height = (tileWidth * 1.05f).toInt().coerceIn(dpToPx(48), dpToPx(104))
                        }
                    }
                    return itemView
                }
            }
            gridView.setOnItemClickListener { _, _, position, _ -> openVerse(verses[position], returnToSheet) }
            gridView.post {
                gridView.requestLayout()
                gridView.invalidateViews()
            }
            focusVerseInGrid(gridView, verses, focusVerse)
        } else {
            listView.adapter = object : ArrayAdapter<Verse>(this, R.layout.item_verse_row, verses) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val itemView = convertView ?: layoutInflater.inflate(R.layout.item_verse_row, parent, false)
                    val verse = getItem(position) ?: return itemView
                    itemView.findViewById<TextView>(R.id.verseNumberTextView).text = verse.verse.toString()
                    val firstDisplayLine = displayLinesByVerse[verse.verse]
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: verse.firstLine
                    itemView.findViewById<TextView>(R.id.verseFirstLineTextView).text =
                        sanitizeSelectionLine(firstDisplayLine)
                    return itemView
                }
            }
            listView.setOnItemClickListener { _, _, position, _ -> openVerse(verses[position], returnToSheet) }
            focusVerseInList(listView, verses, focusVerse)
        }
    }

    private fun focusVerseInList(listView: ListView, verses: List<Verse>, focusVerse: Int) {
        if (focusVerse <= 0) return
        val targetPosition = verses.indexOfFirst { it.verse == focusVerse }
        if (targetPosition < 0) return
        listView.post {
            val itemHalfHeight = dpToPx(28)
            val centerOffset = (listView.height / 2) - itemHalfHeight
            listView.setSelectionFromTop(targetPosition, centerOffset)
        }
    }

    private fun focusVerseInGrid(gridView: GridView, verses: List<Verse>, focusVerse: Int) {
        if (focusVerse <= 0) return
        val targetPosition = verses.indexOfFirst { it.verse == focusVerse }
        if (targetPosition < 0) return
        gridView.post {
            val cols = gridView.numColumns.coerceAtLeast(1)
            val row = targetPosition / cols
            val cellHeight = dpToPx(52)
            val visibleRows = (gridView.height / cellHeight).coerceAtLeast(1)
            val startRow = (row - visibleRows / 2).coerceAtLeast(0)
            gridView.setSelection(startRow * cols)
        }
    }

    private fun openVerse(verse: Verse, returnToSheet: Boolean) {
        val sheetIntent = Intent(this, SheetMusicActivity::class.java)
            .putExtra(SheetMusicActivity.EXTRA_FILE_NAME, verse.fileName)
        if (returnToSheet) {
            sheetIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(sheetIntent)
        if (!returnToSheet) {
            // Versoverzicht meteen sluiten: 'terug' vanuit de bladmuziek keert dan
            // direct terug naar het psalm-/gezangoverzicht i.p.v. dit tussenscherm.
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachNumberSwipeNavigation(type: String, number: Int, vararg views: View) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f && kotlin.math.abs(dx) > 150f && kotlin.math.abs(vx) > 250f) {
                    openAdjacentNumber(type, number, previous = dx > 0)
                    return true
                }
                return false
            }
        })
        val listener = View.OnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
        views.forEach { it.setOnTouchListener(listener) }
    }

    private fun openAdjacentNumber(type: String, number: Int, previous: Boolean) {
        val numbers = HymnRepository.availableNumbers(type)
        val currentIndex = numbers.indexOf(number)
        if (currentIndex < 0) return
        val targetIndex = currentIndex + if (previous) -1 else 1
        val targetNumber = numbers.getOrNull(targetIndex) ?: return
        startActivity(
            Intent(this, VerseListActivity::class.java)
                .putExtra(EXTRA_TYPE, type)
                .putExtra(EXTRA_NUMBER, targetNumber)
        )
        overrideNumberTransition(previous)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun overrideNumberTransition(previous: Boolean) {
        if (previous) {
            overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right)
        } else {
            overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left)
        }
    }

    private fun showAboutDialog(title: String, about: String) {
        val markdownPattern = Regex("\\[([^]]+)]\\((https://statenvertaling\\.nl/tekst\\.php\\?bb=\\d+&hf=\\d+&ind=4#starth)\\)")
        val rawPattern = Regex("https://statenvertaling\\.nl/tekst\\.php\\?bb=\\d+&hf=\\d+&ind=4#starth")
        val markdownMatch = markdownPattern.find(about)
        val rawMatch = if (markdownMatch == null) rawPattern.find(about) else null
        val linkRange = markdownMatch?.range ?: rawMatch?.range
        val message = if (linkRange == null) {
            SpannableString(about)
        } else {
            val label = markdownMatch?.groupValues?.get(1) ?: "Bijbeltekst"
            val url = markdownMatch?.groupValues?.get(2) ?: rawMatch!!.value
            val linkedText = about.replaceRange(linkRange!!, label)
            val linkStart = linkRange.first
            SpannableString(linkedText).apply {
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }, linkStart, linkStart + label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Sluiten", null)
            .create()
        dialog.setOnShowListener {
            dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                android.text.method.LinkMovementMethod.getInstance()
        }
        dialog.show()
    }

    /** In de verskeuze verbergen we melisma-markering (underscores) voor rustiger lezen. */
    private fun sanitizeSelectionLine(line: String): String =
        line.replace("_", "").replace(Regex("\\s+"), " ").trim()

    companion object {
        const val EXTRA_TYPE = "nl.psalmbladmuziek.app.extra.TYPE"
        const val EXTRA_NUMBER = "nl.psalmbladmuziek.app.extra.NUMBER"
        const val EXTRA_FOCUS_VERSE = "nl.psalmbladmuziek.app.extra.FOCUS_VERSE"
        const val EXTRA_RETURN_TO_SHEET = "nl.psalmbladmuziek.app.extra.RETURN_TO_SHEET"
        // Boven dit aantal verzen tonen we een compact nummerraster i.p.v. de regel-lijst.
        private const val VERSE_GRID_THRESHOLD = 20
    }
}
