package moe.nikableh.sift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.SurfaceView
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * The pager with the video picture laid over its current page. Both are views of one parent, so a
 * touch on the picture falls through to the pager under it. It covers the whole screen, and
 * [setSlot] says which part of that the header and the folder buttons leave free
 */
@SuppressLint("ViewConstructor")
class MediaView(ctx: Context) : ViewGroup(ctx) {
    val pager = ZoomImageView(ctx)
    val surface = SurfaceView(ctx)

    private val slot = Rect()
    private var aspect = 0f
    private var shown = false

    init {
        addView(pager)
        addView(surface)
        surface.translationX = AWAY
        pager.onDrag = { if (shown) surface.translationX = it }
        // The page under the picture just became the neighbour, and the picture must not ride on with the new one
        pager.onTurn = { show(false) }
    }

    fun setSlot(left: Int, top: Int, right: Int, bottom: Int) {
        if (slot.left == left && slot.top == top && slot.right == right && slot.bottom == bottom) return
        slot.set(left, top, right, bottom)
        pager.setSlot(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        requestLayout()
    }

    /** [aspect] is the video's width over its height, or 0 while nothing says what it is. */
    fun video(aspect: Float, show: Boolean) {
        if (aspect != this.aspect) {
            this.aspect = aspect
            requestLayout()
        }
        show(show)
    }

    private fun show(show: Boolean) {
        shown = show
        // Moved out of sight instead of hidden. Hiding a SurfaceView destroys its surface, and the
        // player would have to set its decoder up again for the next video
        surface.translationX = if (show) pager.dragOffset else AWAY
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        pager.measure(exactly(width), exactly(height))
        // Fitted the way the pager fits the still frame, so the picture covers it exactly
        var w = slot.width()
        var h = slot.height()
        if (aspect > 0f && h > 0) {
            if (aspect > w / h.toFloat()) h = (w / aspect).roundToInt() else w = (h * aspect).roundToInt()
        }
        surface.measure(exactly(w), exactly(h))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        pager.layout(0, 0, r - l, b - t)
        val x = slot.left + (slot.width() - surface.measuredWidth) / 2
        val y = slot.top + (slot.height() - surface.measuredHeight) / 2
        surface.layout(x, y, x + surface.measuredWidth, y + surface.measuredHeight)
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private companion object {
        /** Farther than any screen is wide. */
        const val AWAY = 100_000f
    }
}
