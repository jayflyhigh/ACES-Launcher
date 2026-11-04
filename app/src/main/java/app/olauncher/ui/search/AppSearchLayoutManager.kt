package app.olauncher.ui.search

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class AppSearchLayoutManager(context: Context, columns: Int) : GridLayoutManager(context, columns) {
    var alignBottom = false
    private var bottomOffset = 0
    private var shortContent = false

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (bottomOffset != 0) offsetChildrenVertical(-bottomOffset)
        bottomOffset = 0
        shortContent = false
        super.onLayoutChildren(recycler, state)
        if (!alignBottom || state.isPreLayout || childCount == 0 || childCount != state.itemCount) return
        val first = getChildAt(0) ?: return
        val last = getChildAt(childCount - 1) ?: return
        val top = getDecoratedTop(first) - (first.layoutParams as RecyclerView.LayoutParams).topMargin
        var bottom = top
        for (index in 0 until childCount) {
            val child = getChildAt(index) ?: continue
            bottom = maxOf(bottom, getDecoratedBottom(child) + (child.layoutParams as RecyclerView.LayoutParams).bottomMargin)
        }
        val available = height - paddingTop - paddingBottom
        if (getPosition(first) == 0 && getPosition(last) == state.itemCount - 1 && bottom - top <= available) {
            shortContent = true
            bottomOffset = paddingTop + bottomAlignmentOffset(available, bottom - top) - top
            if (bottomOffset != 0) offsetChildrenVertical(bottomOffset)
        }
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int =
        if (shortContent) 0 else super.scrollVerticallyBy(dy, recycler, state)
}

internal fun bottomAlignmentOffset(availableHeight: Int, contentHeight: Int): Int =
    (availableHeight - contentHeight).coerceAtLeast(0)
