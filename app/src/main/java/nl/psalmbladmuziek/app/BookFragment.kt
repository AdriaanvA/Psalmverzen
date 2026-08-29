package nl.psalmbladmuziek.app

import android.content.Intent
import androidx.core.net.toUri
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class BookFragment : Fragment() {

    private var rootView: View? = null
    var activeCategory: PsalmCategory? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_book, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
        populate(view)
    }

    override fun onResume() {
        super.onResume()
        val app = requireActivity().application as? PsalmenApplication
        if (app?.consumeBackgroundedFlag() == true) {
            activeCategory = null
        }
        rootView?.let { populate(it) }
    }

    /** Herbouwt de lijst; wordt ook aangeroepen na een instellingswijziging. */
    fun refresh() {
        rootView?.let { populate(it) }
    }

    private fun populate(view: View) {
        val bookType = arguments?.getString("BOOK_TYPE") ?: return
        val gridView = view.findViewById<GridView>(R.id.psalmGridView)
        val emptyTextView = view.findViewById<TextView>(R.id.emptyTextView)
        val progressBar = view.findViewById<View>(R.id.progressBar)

        if (!HymnRepository.isReady) {
            gridView.visibility = View.GONE
            emptyTextView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            return
        }

        progressBar.visibility = View.GONE
        if (bookType == HymnRepository.PSALMS_TITLE) {
            emptyTextView.visibility = View.GONE
            gridView.visibility = View.VISIBLE
            gridView.numColumns = AppSettings.psalmGridColumns(requireContext())
            gridView.verticalSpacing = 0
            gridView.horizontalSpacing = 0
            gridView.stretchMode = GridView.STRETCH_COLUMN_WIDTH

            val cat = activeCategory
            val psalmNumbers = if (cat != null) {
                (1..150).filter { it in cat.psalms }.map { it.toString() }
            } else {
                (1..150).map { it.toString() }
            }

            val bookNumberAccent = ContextCompat.getColor(requireContext(), R.color.psalm_book_number_accent)
            gridView.adapter = object : ArrayAdapter<String>(requireContext(), R.layout.item_number_grid, psalmNumbers) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val itemView = super.getView(position, convertView, parent)
                    val tileHeight = gridTileHeight(parent)
                    if (tileHeight > 0) {
                        itemView.layoutParams = (itemView.layoutParams ?: AbsListView.LayoutParams(-1, -2)).apply {
                            height = tileHeight
                        }
                    }
                    val n = psalmNumbers[position].toInt()
                    val inAccentedBook = n in 42..72 || n in 90..106
                    (itemView as TextView).setTextColor(
                        if (inAccentedBook) bookNumberAccent else ContextCompat.getColor(
                            requireContext(),
                            R.color.app_text_primary
                        )
                    )
                    return itemView
                }
            }
            gridView.post {
                gridView.requestLayout()
                gridView.invalidateViews()
            }
            gridView.setOnItemClickListener { _, _, position, _ ->
                openPsalmOrGezang("Psalm", psalmNumbers[position].toInt())
            }
            return
        }

        val rows = if (bookType == HymnRepository.PSALTERS_TITLE) psalterRows() else gezangRows()
        if (rows.isNotEmpty()) {
            emptyTextView.visibility = View.GONE
            gridView.visibility = View.VISIBLE
            gridView.numColumns = 1
            gridView.verticalSpacing = 0
            val secondary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.app_text_secondary)
            val primary = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.app_text_primary)
            gridView.adapter = object : ArrayAdapter<HymnRow>(requireContext(), R.layout.item_hymn_list, rows) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val itemView = convertView ?: layoutInflater.inflate(R.layout.item_hymn_list, parent, false)
                    val row = getItem(position) ?: return itemView
                    val tileHeight = gridTileHeight(parent)
                    if (tileHeight > 0) {
                        itemView.layoutParams = (itemView.layoutParams ?: AbsListView.LayoutParams(-1, -2)).apply {
                            height = tileHeight
                        }
                    }
                    val numberTv = itemView.findViewById<TextView>(R.id.hymnNumberTextView)
                    val titleTv = itemView.findViewById<TextView>(R.id.hymnFirstLineTextView)
                    val infoButton = itemView.findViewById<ImageButton>(R.id.hymnInfoTextView)
                    numberTv.text = row.number
                    titleTv.text = row.title
                    val showInfo = row.kind == KIND_HEADER &&
                        (row.title == "Schriftliederen" || row.title == "Psalters")
                    infoButton.visibility = if (showInfo) View.VISIBLE else View.GONE
                    infoButton.setColorFilter(if (row.title == "Schriftliederen") secondary else primary)
                    infoButton.setOnClickListener(if (showInfo) View.OnClickListener {
                        if (row.title == "Psalters") showPsaltersAbout() else showSchriftliederenAbout()
                    } else null)
                    when (row.kind) {
                        KIND_HEADER -> {
                            titleTv.setTypeface(null, Typeface.BOLD)
                            titleTv.setTextColor(primary)
                        }
                        KIND_PLACEHOLDER -> {
                            // Nog geen tekst/muziek: cursief + gedempt als indicatie.
                            titleTv.setTypeface(null, Typeface.ITALIC)
                            titleTv.setTextColor(secondary)
                            numberTv.setTextColor(secondary)
                        }
                        else -> {
                            titleTv.setTypeface(null, Typeface.NORMAL)
                            titleTv.setTextColor(primary)
                            numberTv.setTextColor(primary)
                        }
                    }
                    return itemView
                }
            }
            gridView.post {
                gridView.requestLayout()
                gridView.invalidateViews()
            }
            gridView.setOnItemClickListener { _, _, position, _ ->
                val row = rows[position]
                when (row.kind) {
                    KIND_PLACEHOLDER -> Toast.makeText(
                        requireContext(),
                        "${row.title}: nog geen tekst en muziek toegevoegd.",
                        Toast.LENGTH_SHORT
                    ).show()
                    KIND_HEADER -> {}
                    else -> row.openNumber?.let {
                        openPsalmOrGezang(if (bookType == HymnRepository.PSALTERS_TITLE) "Psalter" else "Gezang", it)
                    }
                }
            }
        } else {
            gridView.visibility = View.GONE
            emptyTextView.visibility = View.VISIBLE
            emptyTextView.text = if (bookType == HymnRepository.PSALTERS_TITLE) {
                "Nog geen psalters toegevoegd."
            } else {
                "Nog geen gezangen toegevoegd."
            }
        }
    }

    private fun gridTileHeight(parent: ViewGroup): Int {
        val columns = AppSettings.psalmGridColumns(requireContext())
        val tileWidth = ((parent.width - parent.paddingLeft - parent.paddingRight) / columns.toFloat()).toInt()
        return if (tileWidth > 0) {
            (tileWidth * 1.05f).toInt().coerceIn(requireContext().dpToPx(48), requireContext().dpToPx(104))
        } else {
            0
        }
    }

    /**
     * Rijen voor de enige gezangen: de gewone gezangen (1-13) en, indien de instelling
     * aanstaat, daaronder de Schriftliederen (vaste lijst, bijbelvolgorde). Liederen
     * zonder tekst/muziek verschijnen cursief als placeholder.
     */
    fun showCurrentInfo() {
        val cat = activeCategory ?: PSALM_CATEGORY_DEFAULT
        val message = SpannableStringBuilder(cat.description)
        if (activeCategory == null) {
            val quote = "“Wij moeten liederen hebben die niet alleen eerbaar, maar ook heilig zijn, die ons aansporen tot het bidden en loven van God, tot het overdenken van Zijn werken, opdat wij Hem liefhebben, vrezen, eren en verheerlijken.\n\nWij zullen geen betere liederen vinden, noch liederen die geschikter zijn voor dit doel, dan de Psalmen van David, die de Heilige Geest hem heeft ingegeven en gemaakt.\n\nEn daarom, wanneer wij ze zingen, zijn wij ervan verzekerd dat God ons de woorden in de mond legt, alsof Hij Zelf in ons zingt, om Zijn heerlijkheid te verheerlijken.”"
            message.append("\n\n")
            val quoteStart = message.length
            message.append(quote)
            message.setSpan(StyleSpan(Typeface.ITALIC), quoteStart, message.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            message.append("\n\n")
            val attributionStart = message.length
            message.append("— Johannes Calvijn, Voorrede bij het Geneefse Psalter (1543)")

            message.append("\n\n")
            val signatureStart = message.length
            message.append("S.D.G.")
        }
        message.append("\n\n")
        val linkStart = message.length
        message.append("Meer informatie over de functies van deze app")
        message.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, "https://adriaanva.github.io/Psalmverzen/".toUri()))
                } catch (_: Exception) {
                    // Geen browser beschikbaar.
                }
            }
        }, linkStart, message.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(cat.name)
            .setMessage(message)
            .setPositiveButton("Sluiten", null)
            .create()
        dialog.setOnShowListener {
            dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }
        dialog.show()
    }

    fun showCategoryChooser() {
        val context = requireContext()
        val currentCat = activeCategory

        class Entry(val category: PsalmCategory?, val label: String, val isHeader: Boolean = false)

        val entries = mutableListOf<Entry>()
        entries += Entry(null, "Alle 150 psalmen")
        listOf(
            "Psalterindeling" to listOf(0,1,2,3,4),
            "Bijbels/literair" to listOf(5,6,7,8,9,10,11,12,33,32,34,22,23,28),
            "Thema" to listOf(13,14,15,16,24,17,18,19,20,21,25,27,26,29),
            "Gebruik" to listOf(30,31)
        ).forEach { (title, indices) ->
            entries += Entry(null, title, isHeader = true)
            indices.forEach { idx ->
                val c = PSALM_CATEGORIES[idx]
                entries += Entry(c, "${c.name} (${c.psalms.size})")
            }
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(pos: Int) = entries[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun isEnabled(pos: Int) = !entries[pos].isHeader
            override fun getViewTypeCount() = 2
            override fun getItemViewType(pos: Int) = if (entries[pos].isHeader) 0 else 1

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val entry = entries[pos]
                return if (entry.isHeader) {
                    (convertView as? TextView ?: TextView(context)).apply {
                        text = entry.label
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = true
                        setTextColor(ContextCompat.getColor(context, R.color.app_text_secondary))
                        setPadding(context.dpToPx(16), context.dpToPx(14), context.dpToPx(16), context.dpToPx(2))
                    }
                } else {
                    (convertView as? TextView ?: TextView(context)).apply {
                        text = entry.label
                        textSize = 15f
                        setTypeface(null, if (entry.category == currentCat) Typeface.BOLD else Typeface.NORMAL)
                        setPadding(context.dpToPx(32), context.dpToPx(10), context.dpToPx(16), context.dpToPx(10))
                    }
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Categorie")
            .setAdapter(adapter) { _, which ->
                val entry = entries[which]
                if (!entry.isHeader) {
                    activeCategory = entry.category
                    rootView?.let { populate(it) }
                }
            }
            .show()
    }

    private fun gezangRows(): List<HymnRow> {
        val rows = mutableListOf<HymnRow>()
        HymnRepository.groupsForBook(HymnRepository.HYMNS_TITLE)
            .filter { (it.verses.firstOrNull()?.number ?: 0) < SCHRIFTLIEDEREN_FROM }
            .forEach { g ->
                val number = g.verses.firstOrNull()?.number ?: return@forEach
                rows += HymnRow(number.toString(), g.title, KIND_NORMAL, number)
            }

        if (AppSettings.showSchriftliederen(requireContext())) {
            rows += HymnRow("", "Schriftliederen", KIND_HEADER, null)
            for ((number, label) in SCHRIFTLIEDEREN) {
                val available = number !in HymnRepository.disabledHymnNumbers &&
                    HymnRepository.versesFor("Gezang", number).isNotEmpty()
                rows += if (available) {
                    HymnRow(number.toString(), label, KIND_NORMAL, number)
                } else {
                    HymnRow(number.toString(), label, KIND_PLACEHOLDER, null)
                }
            }
        }
        return rows
    }

    private fun showSchriftliederenAbout() {
        requireContext().showInfoDialog("Schriftliederen", SCHRIFTLIEDEREN_ABOUT)
    }

    private fun showPsaltersAbout() {
        requireContext().showInfoDialog("Psalters", PSALTERS_ABOUT)
    }

    private fun psalterRows(): List<HymnRow> = buildList {
        add(HymnRow("", "Psalters", KIND_HEADER, null))
        addAll(HymnRepository.groupsForBook(HymnRepository.PSALTERS_TITLE).mapNotNull { group ->
            val number = group.verses.firstOrNull()?.number ?: return@mapNotNull null
            HymnRow(number.toString(), group.title, KIND_NORMAL, number)
        })
    }

    private fun openPsalmOrGezang(type: String, number: Int) {
        val verses = HymnRepository.versesFor(type, number)
        if (verses.isEmpty()) {
            Toast.makeText(requireContext(), "$type $number heeft nog geen noten.", Toast.LENGTH_SHORT).show()
            return
        }
        if (shouldOpenDirectly(verses.size)) {
            openVerse(verses.first())
        } else {
            startActivity(
                Intent(requireContext(), VerseListActivity::class.java)
                    .putExtra(VerseListActivity.EXTRA_TYPE, type)
                    .putExtra(VerseListActivity.EXTRA_NUMBER, number)
            )
        }
    }

    private fun openVerse(verse: Verse) {
        startActivity(
            Intent(requireContext(), SheetMusicActivity::class.java)
                .putExtra(SheetMusicActivity.EXTRA_FILE_NAME, verse.fileName)
        )
    }

    companion object {
        internal fun shouldOpenDirectly(verseCount: Int): Boolean = verseCount == 1

        fun newInstance(bookType: String): BookFragment {
            val fragment = BookFragment()
            val args = Bundle()
            args.putString("BOOK_TYPE", bookType)
            fragment.arguments = args
            return fragment
        }

        // Gezang 14 en verder = Schriftliederen (aparte instelling).
        private const val SCHRIFTLIEDEREN_FROM = 14
        private const val KIND_NORMAL = 0
        private const val KIND_HEADER = 1
        private const val KIND_PLACEHOLDER = 2

        // Vaste catalogus van Schriftliederen in bijbelvolgorde (nummering 14+).
        // Titels worden altijd zo getoond; beschikbaarheid volgt uit de aanwezige inhoud.
        private val SCHRIFTLIEDEREN = listOf(
            14 to "Exodus 15 – Lied van Mozes en Mirjam",
            15 to "Deuteronomium 32 – Lied van Mozes",
            16 to "Richteren 5 – Lied van Debora",
            17 to "1 Samuël 2 – Lofzang van Hanna",
            18 to "2 Samuël 1 – Klaaglied van David",
            19 to "2 Samuël 22 – Loflied van David (Psalm 18)",
            20 to "Jesaja 5 – Lied van den wijngaard",
            21 to "Jesaja 12 – Danklied",
            22 to "Jesaja 26 – Lied van Juda",
            23 to "Jesaja 38 – Lofzang van Hizkia",
            24 to "Jesaja 42 – Nieuw Lied van Jesaja",
            25 to "Habakuk 3 – Gebed/Lied van Habakuk",
            26 to "Lukas 2 – Ere zij God",
            27 to "Openbaring 5 – Lofzang voor het Lam",
            28 to "Openbaring 15 – Lied van Mozes en het Lam",
            29 to "Openbaring 19 – Halleluja's",
        )
    }
}

private data class HymnRow(
    val number: String,
    val title: String,
    val kind: Int,
    val openNumber: Int?
)
