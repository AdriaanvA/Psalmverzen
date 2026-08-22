package nl.psalmbladmuziek.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private var bookTabMediator: TabLayoutMediator? = null
    private var attachedBookTitles: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_main)
        bindStatusBarBackground(findViewById(R.id.mainRoot), findViewById(R.id.statusBarBackground))

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        HymnRepository.loadAsync(this) { refreshBookFragments() }

        rebuildBookPager()
        selectInitialBookTab()

        attachBookTabs()

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
            "Gezang" -> HymnRepository.bookTitles(this).indexOf(HymnRepository.HYMNS_TITLE)
            "Psalter" -> HymnRepository.bookTitles(this).indexOf(HymnRepository.PSALTERS_TITLE)
            else -> HymnRepository.bookTitles(this).indexOf(HymnRepository.PSALMS_TITLE)
        }.coerceAtLeast(0)
        viewPager.setCurrentItem(index, false)
    }

    private fun getPsalmFragment(): BookFragment? =
        supportFragmentManager.fragments.filterIsInstance<BookFragment>()
            .find { it.arguments?.getString("BOOK_TYPE") == HymnRepository.PSALMS_TITLE }

    private fun refreshBookFragments() {
        val currentTitles = HymnRepository.bookTitles(this)
        if (currentTitles != attachedBookTitles) {
            rebuildBookPager()
            selectInitialBookTab()
        }
        supportFragmentManager.fragments.filterIsInstance<BookFragment>().forEach { it.refresh() }
    }

    private fun rebuildBookPager() {
        bookTabMediator?.detach()
        attachedBookTitles = HymnRepository.bookTitles(this)
        viewPager.adapter = BookPagerAdapter(this, attachedBookTitles)
        if (bookTabMediator != null) attachBookTabs()
    }

    private fun attachBookTabs() {
        bookTabMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = HymnRepository.bookTitles(this)[position]
        }.also { it.attach() }
    }

    companion object {
        const val EXTRA_BOOK_TYPE = "nl.psalmbladmuziek.app.extra.BOOK_TYPE"
    }
}
