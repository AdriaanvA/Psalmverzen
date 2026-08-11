package nl.psalmbladmuziek.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureReadableSystemBars()
        setContentView(R.layout.activity_main)
        bindStatusBarBackground(findViewById(R.id.mainRoot), findViewById(R.id.statusBarBackground))

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        HymnRepository.loadAsync(this) { refreshBookFragments() }

        val adapter = BookPagerAdapter(this, HymnRepository.bookTitles)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(0, false)

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

    private fun getPsalmFragment(): BookFragment? =
        supportFragmentManager.fragments.filterIsInstance<BookFragment>()
            .find { it.arguments?.getString("BOOK_TYPE") == HymnRepository.PSALMS_TITLE }

    private fun refreshBookFragments() {
        supportFragmentManager.fragments.filterIsInstance<BookFragment>().forEach { it.refresh() }
    }
}
