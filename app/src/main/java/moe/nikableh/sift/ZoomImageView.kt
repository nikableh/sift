package moe.nikableh.sift

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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Fit-to-view image with pinch and double-tap zoom. A horizontal swipe at 1x reports prev or next. */
@SuppressLint("ViewConstructor")
class ZoomImageView(ctx: Context) : View(ctx) {
    /** +1 for next, -1 for previous. */
    var onSwipe: ((Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    /** Fired once per image when the user first zooms in, to fetch a sharper bitmap. */
    var onZoomIn: (() -> Unit)? = null

    private var bitmap: Bitmap? = null
    private var key: String? = null
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var dragX = 0f
    private var flingDir = 0
    private var zoomReported = false
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    val isZoomed get() = zoom > 1.01f

    /** Shows [b] for the photo [newKey]. A new bitmap for the same photo keeps zoom and pan. */
    fun show(newKey: String?, b: Bitmap?) {
        if (newKey == key && b === bitmap) return
        val samePhoto = newKey == key && bitmap != null && b != null
        key = newKey
        setImage(b, keepView = samePhoto)
    }

    /** [keepView] swaps in a sharper bitmap of the same photo without resetting zoom and pan. */
    fun setImage(b: Bitmap?, keepView: Boolean = false) {
        bitmap = b
        if (!keepView) {
            zoom = 1f
            panX = 0f
            panY = 0f
            dragX = 0f
            zoomReported = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val scale = fitScale(b) * zoom
        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(
            (width - b.width * scale) / 2f + panX + dragX,
            (height - b.height * scale) / 2f + panY,
        )
        canvas.drawBitmap(b, drawMatrix, paint)
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

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (bitmap == null) return true
            if (isZoomed) zoomTo(1f, e.x, e.y) else zoomTo(3f, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaleDetector.isInProgress || e2.pointerCount > 1) return true
            if (isZoomed) {
                panX -= dx
                panY -= dy
                clampPan()
            } else {
                dragX -= dx
            }
            invalidate()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (!isZoomed && abs(vx) > 600 * resources.displayMetrics.density && abs(vx) > abs(vy) * 1.5f) flingDir = if (vx < 0) 1 else -1
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) flingDir = 0
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val dir = when {
                isZoomed || event.actionMasked == MotionEvent.ACTION_CANCEL -> 0
                flingDir != 0 -> flingDir
                abs(dragX) > width * 0.2f -> if (dragX < 0) 1 else -1
                else -> 0
            }
            dragX = 0f
            invalidate()
            if (dir != 0) onSwipe?.invoke(dir)
        }
        return true
    }
}
