package nl.psalmbladmuziek.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SearchActivity : AppCompatActivity() {

    private val results = ArrayList<SearchResult>()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_search)

        bindStatusBarBackground(findViewById(R.id.searchRoot), findViewById(R.id.statusBarBackground))

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        val adapter = object : ArrayAdapter<SearchResult>(this, R.layout.item_search_result, results) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemView = convertView ?: layoutInflater.inflate(R.layout.item_search_result, parent, false)
                val result = getItem(position) ?: return itemView
                itemView.findViewById<TextView>(R.id.resultFirstLineTextView).text =
                    highlight(result.displayLine, currentQuery)
                itemView.findViewById<TextView>(R.id.resultLabelTextView).text =
                    "${result.verse.type} ${result.verse.number}:${result.verse.verse}"
                return itemView
            }
        }

        val listView = findViewById<ListView>(R.id.searchListView)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val verse = results[position].verse
            startActivity(
                Intent(this, SheetMusicActivity::class.java)
                    .putExtra(SheetMusicActivity.EXTRA_FILE_NAME, verse.fileName)
            )
        }

        // Alle resultaten (lege query) alvast async laden; bouwt de index buiten de UI-thread.
        VerseSearchIndex.searchAsync(this, currentQuery) { res ->
            if (currentQuery.isEmpty()) {
                results.clear()
                results.addAll(res)
                adapter.notifyDataSetChanged()
            }
        }

        findViewById<EditText>(R.id.searchEditText).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                val queryAtRequest = currentQuery
                VerseSearchIndex.searchAsync(this@SearchActivity, queryAtRequest) { res ->
                    // Verouderde (trager binnengekomen) resultaten negeren.
                    if (queryAtRequest == currentQuery) {
                        results.clear()
                        results.addAll(res)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        })
    }

    /** Markeert het gezochte woord in de regel (vetgedrukt + accentkleur). */
    private fun highlight(line: String, query: String): CharSequence {
        val q = query.trim()
        if (q.isEmpty()) return line
        val index = line.lowercase().indexOf(q.lowercase())
        if (index < 0) return line
        val spannable = SpannableString(line)
        spannable.setSpan(StyleSpan(Typeface.BOLD), index, index + q.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.tab_selected)), index, index + q.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannable
    }
}

