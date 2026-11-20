package moe.nikableh.sift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.view.SurfaceView
import android.view.ViewGroup

/**
 * The pager with the video picture laid over its current page. Both are views of one parent, so a
 * touch on the picture falls through to the pager under it. It covers the whole screen, and
 * [setSlot] says which part of that the header and the folder buttons leave free
 */
@SuppressLint("ViewConstructor")
class MediaView(ctx: Context) : ViewGroup(ctx) {
    val pager = ZoomImageView(ctx)
    val surface = SurfaceView(ctx)

    /** Where the pager draws its current page, the still frame the picture covers. */
    private val page = RectF()
    private val slot = RectF()
    private var aspect = 0f
    private var shown = false

    init {
        addView(pager)
        addView(surface)
        // Laid out over the whole view once and then only scaled and moved onto the page. A new size
        // would make the surface set up a new buffer on every frame of a zoom
        surface.pivotX = 0f
        surface.pivotY = 0f
        surface.translationX = AWAY
        pager.onPlace = {
            page.set(it)
            place()
        }
        // The page under the picture just became the neighbour, and the picture must not ride on with the new one
        pager.onTurn = { show(false) }
    }

    fun setSlot(left: Int, top: Int, right: Int, bottom: Int) {
        if (slot.left == left.toFloat() && slot.top == top.toFloat() && slot.right == right.toFloat() && slot.bottom == bottom.toFloat()) return
        slot.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        pager.setSlot(slot.left, slot.top, slot.right, slot.bottom)
        place()
    }

    /** [aspect] is the video's width over its height, or 0 while nothing says what it is. */
    fun video(aspect: Float, show: Boolean) {
        this.aspect = aspect
        show(show)
    }

    private fun show(show: Boolean) {
        shown = show
        place()
    }

    /**
     * Fits the picture into the still frame the way the pager fits the still into its room, so it
     * covers the still exactly however far it is zoomed, panned or dragged. A video without a still
     * frame sits in the slot
     */
    private fun place() {
        val w = width
        val h = height
        val room = if (page.isEmpty) slot else page
        // Moved out of sight instead of hidden. Hiding a SurfaceView destroys its surface, and the
        // player would have to set its decoder up again for the next video
        if (!shown || w == 0 || h == 0 || room.isEmpty) {
            surface.translationX = AWAY
            return
        }
        var fw = room.width()
        var fh = room.height()
        if (aspect > 0f) {
            if (aspect > fw / fh) fh = fw / aspect else fw = fh * aspect
        }
        surface.scaleX = fw / w
        surface.scaleY = fh / h
        surface.translationX = room.centerX() - fw / 2f
        surface.translationY = room.centerY() - fh / 2f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        pager.measure(exactly(width), exactly(height))
        surface.measure(exactly(width), exactly(height))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        pager.layout(0, 0, r - l, b - t)
        surface.layout(0, 0, r - l, b - t)
        place()
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private companion object {
        /** Farther than any screen is wide. */
        const val AWAY = 100_000f
    }
}
