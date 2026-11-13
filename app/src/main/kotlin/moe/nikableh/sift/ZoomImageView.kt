package moe.nikableh.sift

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Fit-to-view image with pinch and double-tap zoom. A horizontal swipe at 1x pages to the neighbour and reports prev or next. A vertical one can close it. */
@SuppressLint("ViewConstructor")
class ZoomImageView(ctx: Context) : View(ctx) {
    /** +1 for next, -1 for previous. */
    var onSwipe: ((Int) -> Unit)? = null

    /** Fired once per image when the user first zooms in, to fetch a sharper bitmap. */
    var onZoomIn: (() -> Unit)? = null

    /** Set to let a swipe up or down at 1x close the photo. Without it a vertical drag does nothing. */
    var onDismiss: (() -> Unit)? = null

    /** How far the photo is on its way out, from 0 at rest to 1 when gone, for fading what is around it. */
    var onLift: ((Float) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private var prev: Bitmap? = null
    private var next: Bitmap? = null
    private var canPrev = false
    private var canNext = false
    private var key: String? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    /** Offset of the current page from rest. The neighbours sit one page to either side of it. */
    private var dragX = 0f
    private var downDragX = 0f
    private var flingDir = 0
    private var settle: ValueAnimator? = null

    /** How far a vertical swipe has moved the current page off rest. Negative is up. */
    private var liftY = 0f
    private var lifting = false

    /** Vertical speed of the fling that ended a lift, or 0 when it wasn't one. */
    private var flungY = 0f

    /** A drag keeps the direction it started in, so paging can't turn into closing half-way. */
    private var axisChosen = false
    private var drop: ValueAnimator? = null

    /** Set while the page flies out. Touches wait, since the view is about to go. */
    private var closing = false
    private var zoomReported = false
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gap = 16 * resources.displayMetrics.density

    val isZoomed get() = zoom > 1.01f

    private val page get() = width + gap

    /**
     * Shows [b] for the photo [newKey], with its neighbours ready to slide in. A new bitmap for the
     * same photo keeps zoom and pan. A new photo arriving as the bitmap already on screen is the
     * echo of a swipe this view turned itself, so the slide carries on.
     */
    fun show(newKey: String?, b: Bitmap?, prev: Bitmap?, next: Bitmap?, canPrev: Boolean, canNext: Boolean) {
        this.prev = prev
        this.next = next
        this.canPrev = canPrev
        this.canNext = canNext
        val samePhoto = newKey == key
        key = newKey
        if (b !== bitmap) setImage(b, keepView = samePhoto && b != null)
        invalidate()
    }

    /** [keepView] swaps in a sharper bitmap of the same photo without resetting zoom and pan. */
    fun setImage(b: Bitmap?, keepView: Boolean = false) {
        bitmap = b
        if (!keepView) {
            settle?.cancel()
            closing = false
            drop?.cancel()
            zoom = 1f
            panX = 0f
            panY = 0f
            dragX = 0f
            lift(0f)
            zoomReported = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (dragX > 0f) drawPage(canvas, prev, dragX - page)
        if (dragX < 0f) drawPage(canvas, next, dragX + page)
        // Fades as it lifts, in step with what [onLift] reports
        paint.alpha = if (liftY != 0f && height > 0) (255 * (1f - abs(liftY) / height).coerceIn(0f, 1f)).toInt() else 255
        drawPage(canvas, bitmap, dragX, zoom, panX, panY + liftY)
        paint.alpha = 255
    }

    private fun drawPage(canvas: Canvas, b: Bitmap?, dx: Float, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f) {
        if (b == null) return
        val scale = fitScale(b) * zoom
        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(
            (width - b.width * scale) / 2f + panX + dx,
            (height - b.height * scale) / 2f + panY,
        )
        canvas.drawBitmap(b, drawMatrix, paint)
    }

    /** Makes the neighbour the current page without moving anything on screen. */
    private fun turn(dir: Int) {
        val old = bitmap
        if (dir > 0) {
            bitmap = next
            prev = old
            next = null
        } else {
            bitmap = prev
            next = old
            prev = null
        }
        dragX += dir * page
        zoom = 1f
        panX = 0f
        panY = 0f
        zoomReported = false
    }

    /** Slides the current page to rest. A touch cancels it and takes over from where it stopped. */
    private fun settle() {
        settle?.cancel()
        val from = dragX
        if (from == 0f) return
        settle = ValueAnimator.ofFloat(from, 0f).apply {
            duration = (SETTLE_MS * abs(from) / page).coerceIn(60f, SETTLE_MS).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                dragX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun lift(y: Float) {
        liftY = y
        onLift?.invoke(if (height > 0) (abs(y) / height).coerceIn(0f, 1f) else 0f)
        invalidate()
    }

    /** Lowers a page that wasn't lifted far enough, or slides it the rest of the way out and then closes. */
    private fun release(close: Boolean) {
        drop?.cancel()
        closing = close
        // Out through the edge it was heading for
        val out = if (liftY < 0f || (liftY == 0f && flungY < 0f)) -height.toFloat() else height.toFloat()
        drop = ValueAnimator.ofFloat(liftY, if (close) out else 0f).apply {
            duration = SETTLE_MS.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { lift(it.animatedValue as Float) }
            if (close) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Also runs for a cancelled slide, which clears the flag first
                        if (closing) onDismiss?.invoke()
                    }
                })
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        settle?.cancel()
        closing = false
        drop?.cancel()
        super.onDetachedFromWindow()
    }

    private fun fitScale(b: Bitmap) = min(width / b.width.toFloat(), height / b.height.toFloat())

    private fun clampPan() {
        val b = bitmap ?: return
        val scale = fitScale(b) * zoom
        val maxX = max(0f, (b.width * scale - width) / 2f)
        val maxY = max(0f, (b.height * scale - height) / 2f)
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    private fun zoomTo(target: Float, focusX: Float, focusY: Float) {
        val next = target.coerceIn(1f, 12f)
        val cx = focusX - width / 2f
        val cy = focusY - height / 2f
        val k = next / zoom
        panX = cx - (cx - panX) * k
        panY = cy - (cy - panY) * k
        zoom = next
        if (!isZoomed) {
            zoom = 1f
            panX = 0f
            panY = 0f
        } else if (!zoomReported) {
            zoomReported = true
            onZoomIn?.invoke()
        }
        clampPan()
        invalidate()
    }

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (bitmap == null) return true
            dragX = 0f
            zoomTo(zoom * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (bitmap == null) return true
            if (isZoomed) zoomTo(1f, e.x, e.y) else zoomTo(3f, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress || e2.pointerCount > 1) return true
            if (!axisChosen) {
                axisChosen = true
                // Only from rest: a page caught mid-slide stays a page turn
                lifting = onDismiss != null && !isZoomed && dragX == 0f && abs(dy) > abs(dx)
            }
            if (isZoomed) {
                panX -= dx
                panY -= dy
                clampPan()
            } else if (lifting) {
                lift(liftY - dy)
            } else {
                val to = dragX - dx
                // Resists when there is no page on that side
                val blocked = (to > 0f && !canPrev) || (to < 0f && !canNext)
                dragX -= if (blocked) dx * 0.3f else dx
            }
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val fast = 600 * resources.displayMetrics.density
            if (lifting) {
                flungY = if (abs(vy) > fast && abs(vy) > abs(vx) * 1.5f) vy else 0f
            } else if (!isZoomed && abs(vx) > fast && abs(vx) > abs(vy) * 1.5f) {
                flingDir = if (vx < 0) 1 else -1
            }
            return true
        }
    })

    private companion object {
        /** Time for a full page to slide in. Shorter slides scale down from it. */
        const val SETTLE_MS = 140f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (closing) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            flingDir = 0
            flungY = 0f
            lifting = false
            axisChosen = false
            settle?.cancel()
            drop?.cancel()
            downDragX = dragX
        }
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (lifting && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)) {
            // Like paging: a flick back toward the middle keeps the photo, whatever the distance
            val flungBack = flungY * liftY < 0f
            val close = event.actionMasked == MotionEvent.ACTION_UP && !isZoomed && !flungBack &&
                (flungY != 0f || abs(liftY) > height * 0.2f)
            release(close)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Measured from where the finger landed, so catching a slide mid-way and letting go doesn't turn back
            val moved = dragX - downDragX
            var dir = when {
                isZoomed || event.actionMasked == MotionEvent.ACTION_CANCEL -> 0
                // A flick back after peeking at the neighbour returns to the current photo
                flingDir != 0 -> if (moved * flingDir > 0f) 0 else flingDir
                abs(moved) > width * 0.2f -> if (moved < 0) 1 else -1
                else -> 0
            }
            if ((dir > 0 && !canNext) || (dir < 0 && !canPrev)) dir = 0
            if (dir != 0) turn(dir)
            settle()
            // A touch that caught the page on its way down and went elsewhere
            if (liftY != 0f) release(close = false)
            // The index changes right away and only the picture trails, so a folder tap mid-slide files the new photo
            if (dir != 0) onSwipe?.invoke(dir)
        }
        return true
    }
}
