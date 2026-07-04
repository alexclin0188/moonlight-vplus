package com.alexclin.moonlink.android.util

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

/**
 * A [ListView] that disables internal scrolling so it can be safely nested
 * inside a [ScrollView] without conflicting scroll gestures.
 *
 * The parent ScrollView handles all vertical scrolling; this view simply
 * measures its full content height and delegates the scroll to the parent.
 */
class NonScrollableListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.listViewStyle,
) : ListView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Use AT_MOST with a large value so the ListView expands to its
        // full content height within the parent ScrollView.
        val expandSpec = MeasureSpec.makeMeasureSpec(
            Int.MAX_VALUE shr 2,
            MeasureSpec.AT_MOST,
        )
        super.onMeasure(widthMeasureSpec, expandSpec)
        layoutParams?.let {
            it.height = measuredHeight
        }
    }
}
