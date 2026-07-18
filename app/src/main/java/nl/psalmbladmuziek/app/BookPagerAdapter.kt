package nl.psalmbladmuziek.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class BookPagerAdapter(fragmentActivity: FragmentActivity, private val titles: List<String>) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = titles.size

    override fun createFragment(position: Int): Fragment {
        val bookType = titles[position]
        return BookFragment.newInstance(bookType)
    }
}
