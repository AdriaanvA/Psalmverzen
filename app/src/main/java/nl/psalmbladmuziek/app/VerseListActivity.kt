package nl.psalmbladmuziek.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class VerseListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_verse_list)

        val root = findViewById<View>(R.id.verseListRoot)
        val statusBarBackground = findViewById<View>(R.id.statusBarBackground)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            statusBarBackground.layoutParams = statusBarBackground.layoutParams.apply {
                height = statusBars.top
            }
            insets
        }

        val type = intent.getStringExtra(EXTRA_TYPE) ?: "Psalm"
        val number = intent.getIntExtra(EXTRA_NUMBER, 1)

        val verses = HymnRepository.versesFor(type, number)
        val title = verses.firstOrNull { it.title.isNotBlank() }?.title ?: "$type $number"
        findViewById<TextView>(R.id.titleTextView).text = title

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        val about = ScoreBundleRenderer.readAbout(this, type, number)
        val aboutButton = findViewById<View>(R.id.aboutButton)
        if (about != null) {
            aboutButton.visibility = View.VISIBLE
            aboutButton.setOnClickListener { showAboutDialog(title, about) }
        } else {
            aboutButton.visibility = View.GONE
        }

        val listView = findViewById<ListView>(R.id.verseListView)
        listView.adapter = object : ArrayAdapter<Verse>(this, R.layout.item_verse_row, verses) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemView = convertView ?: layoutInflater.inflate(R.layout.item_verse_row, parent, false)
                val verse = getItem(position) ?: return itemView
                itemView.findViewById<TextView>(R.id.verseNumberTextView).text = verse.verse.toString()
                itemView.findViewById<TextView>(R.id.verseFirstLineTextView).text = verse.firstLine
                return itemView
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            val verse = verses[position]
            startActivity(
                Intent(this, SheetMusicActivity::class.java)
                    .putExtra(SheetMusicActivity.EXTRA_FILE_NAME, verse.fileName)
            )
            // Versoverzicht meteen sluiten: 'terug' vanuit de bladmuziek keert dan
            // direct terug naar het psalm-/gezangoverzicht i.p.v. dit tussenscherm.
            finish()
        }
    }

    private fun showAboutDialog(title: String, about: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(about)
            .setPositiveButton("Sluiten", null)
            .show()
    }

    companion object {
        const val EXTRA_TYPE = "nl.psalmbladmuziek.app.extra.TYPE"
        const val EXTRA_NUMBER = "nl.psalmbladmuziek.app.extra.NUMBER"
    }
}
