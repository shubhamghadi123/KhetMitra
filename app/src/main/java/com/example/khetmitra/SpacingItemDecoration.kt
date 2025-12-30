package com.example.khetmitra

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class VerticalSpacingItemDecoration(private val spaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        // Add space to the bottom of every item
        outRect.bottom = spaceHeight

        // Optional: Avoid adding space to the very last item
        if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
            outRect.bottom = 0
        }
    }
}

class HorizontalSpacingItemDecoration(private val spaceWidth: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        // Add space to the RIGHT of every item
        outRect.right = spaceWidth

        // Optional: Avoid adding space to the very last item so it aligns perfectly
        if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
            outRect.right = 0
        }
    }
}