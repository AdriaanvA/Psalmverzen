package nl.psalmbladmuziek.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        HymnRepository.refreshDownloadedContent(this)
        ContentDownloadWorker.enqueueIfConfigured(this)

        val adapter = BookPagerAdapter(this, HymnRepository.bookTitles)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(0, false)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = HymnRepository.bookTitles[position]
        }.attach()

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            showAppSettingsDialog { refreshBookFragments() }
        }
        findViewById<View>(R.id.searchButton).setOnClickListener {
            startActivity(android.content.Intent(this, SearchActivity::class.java))
        }
    }

    private fun refreshBookFragments() {
        supportFragmentManager.fragments.filterIsInstance<BookFragment>().forEach { it.refresh() }
    }

    private fun bindStatusBarBackground(root: View, statusBarBackground: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            statusBarBackground.layoutParams = statusBarBackground.layoutParams.apply {
                height = statusBars.top
            }
            insets
        }
    }
}
