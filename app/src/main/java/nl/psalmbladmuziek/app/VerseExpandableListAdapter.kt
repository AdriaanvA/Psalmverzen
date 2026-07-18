package nl.psalmbladmuziek.app

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView

class VerseExpandableListAdapter(
    private val context: Context,
    private val groups: List<VerseGroup>
) : BaseExpandableListAdapter() {

    override fun getGroupCount(): Int = groups.size

    override fun getChildrenCount(groupPosition: Int): Int = groups[groupPosition].verses.size

    override fun getGroup(groupPosition: Int): VerseGroup = groups[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): Verse = groups[groupPosition].verses[childPosition]

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long = getChild(groupPosition, childPosition).fileName.hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View {
        val group = getGroup(groupPosition)
        return (convertView as? TextView ?: createTextView()).apply {
            text = if (group.verses.isEmpty()) {
                "${group.title}\nNog geen noten toegevoegd"
            } else {
                "${group.title}\n${group.verses.size} vers(en) beschikbaar"
            }
            typeface = Typeface.DEFAULT_BOLD
            textSize = 17f
            setPadding(56, 22, 24, 22)
        }
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val verse = getChild(groupPosition, childPosition)
        return (convertView as? TextView ?: createTextView()).apply {
            text = "Vers ${verse.verse} - ${verse.firstLine}"
            typeface = Typeface.DEFAULT
            textSize = 16f
            setPadding(80, 24, 24, 24)
        }
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    private fun createTextView(): TextView = TextView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}