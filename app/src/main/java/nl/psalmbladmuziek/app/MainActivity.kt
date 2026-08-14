package nl.psalmbladmuziek.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_main)
        bindStatusBarBackground(findViewById(R.id.mainRoot), findViewById(R.id.statusBarBackground))

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        HymnRepository.loadAsync(this) { refreshBookFragments() }

        val adapter = BookPagerAdapter(this, HymnRepository.bookTitles)
        viewPager.adapter = adapter
        selectInitialBookTab()

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = HymnRepository.bookTitles[position]
        }.attach()

        findViewById<View>(R.id.infoButton).setOnClickListener {
            getPsalmFragment()?.showCurrentInfo()
        }
        val filterButton = findViewById<View>(R.id.filterButton)
        filterButton.setOnClickListener { getPsalmFragment()?.showCategoryChooser() }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                filterButton.isClickable = position == 0
                filterButton.isFocusable = position == 0
            }
        })

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            showAppSettingsDialog { refreshBookFragments() }
        }
        findViewById<View>(R.id.searchButton).setOnClickListener {
            startActivity(android.content.Intent(this, SearchActivity::class.java))
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectInitialBookTab()
    }

    private fun selectInitialBookTab() {
        val bookType = intent.getStringExtra(EXTRA_BOOK_TYPE)
        val index = when (bookType) {
            "Gezang" -> HymnRepository.bookTitles.indexOf(HymnRepository.HYMNS_TITLE)
            else -> HymnRepository.bookTitles.indexOf(HymnRepository.PSALMS_TITLE)
        }.coerceAtLeast(0)
        viewPager.setCurrentItem(index, false)
    }

    private fun getPsalmFragment(): BookFragment? =
        supportFragmentManager.fragments.filterIsInstance<BookFragment>()
            .find { it.arguments?.getString("BOOK_TYPE") == HymnRepository.PSALMS_TITLE }

    private fun refreshBookFragments() {
        supportFragmentManager.fragments.filterIsInstance<BookFragment>().forEach { it.refresh() }
    }

    companion object {
        const val EXTRA_BOOK_TYPE = "nl.psalmbladmuziek.app.extra.BOOK_TYPE"
    }
}
