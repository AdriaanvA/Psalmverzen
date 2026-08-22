package nl.psalmbladmuziek.app

import android.content.Context
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.ListView

object AnchoredChoicePopup {
    fun show(
        context: Context,
        anchor: View,
        labels: List<String>,
        currentIndex: Int,
        minWidthDp: Int,
        onItemClick: (Int) -> Unit
    ) {
        val popupHeight = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_single_choice, labels)

        ListPopupWindow(context).apply {
            anchorView = anchor
            width = maxOf(anchor.width, dp(context, minWidthDp))
            height = popupHeight
            isModal = true
            inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                dismiss()
                onItemClick(position)
            }
            show()

            listView?.apply {
                choiceMode = ListView.CHOICE_MODE_SINGLE
                setItemChecked(currentIndex, true)
                post {
                    val rowHeight = dp(context, 48)
                    val topOffset = when (currentIndex) {
                        0 -> 0
                        labels.lastIndex -> popupHeight - rowHeight
                        else -> (popupHeight - rowHeight) / 2
                    }
                    setSelectionFromTop(currentIndex, topOffset)
                }
            }
        }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
