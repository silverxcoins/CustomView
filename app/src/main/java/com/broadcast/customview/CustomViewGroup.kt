package com.broadcast.customview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.withStyledAttributes
import androidx.core.view.marginTop
import kotlin.math.max

class CustomViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val firstChild: View?
        get() = if (childCount > 0) getChildAt(0) else null
    private val secondChild: View?
        get() = if (childCount > 1) getChildAt(1) else null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        checkChildCount()

        firstChild?.let { measureChild(it, widthMeasureSpec) }
        secondChild?.let { measureChild(it, widthMeasureSpec) }

        val firstWidth = firstChild?.measuredWidth ?: 0
        val firstHeight = firstChild?.measuredHeight ?: 0
        val secondWidth = secondChild?.measuredWidth ?: 0
        val secondHeight = secondChild?.measuredHeight ?: 0

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd

        val childrenOnSameLine = firstWidth + secondWidth < widthSize || widthMode == MeasureSpec.UNSPECIFIED
        val width = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> firstWidth + secondWidth
            MeasureSpec.EXACTLY -> widthSize

            MeasureSpec.AT_MOST -> {
                if (childrenOnSameLine) {
                    firstWidth + secondWidth
                } else {
                    max(firstWidth, secondWidth)
                }
            }

            else -> error("Unreachable")
        }

        val height = if (childrenOnSameLine) {
            max(firstHeight, secondHeight)
        } else {
            firstHeight + secondHeight
        }

        setMeasuredDimension(width + paddingLeft + paddingRight, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        firstChild?.layout(
            paddingLeft,
            paddingTop,
            paddingLeft + (firstChild?.measuredWidth ?: 0),
            paddingTop + (firstChild?.measuredHeight ?: 0)
        )
        secondChild?.layout(
            r - l - paddingRight - (secondChild?.measuredWidth ?: 0),
            b - t - paddingBottom - (secondChild?.measuredHeight ?: 0),
            r - l - paddingRight,
            b - t - paddingBottom
        )
    }

    private fun measureChild(child: View, widthMeasureSpec: Int) {
        val specSize = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd

        val childWidthSpec = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> widthMeasureSpec
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(specSize, MeasureSpec.AT_MOST)
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(specSize, MeasureSpec.AT_MOST)
            else -> error("Unreachable")
        }
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        child.measure(childWidthSpec, childHeightSpec)
    }

    private fun checkChildCount() {
        if (childCount > 2) error("CustomViewGroup should not contain more than 2 children")
    }
}