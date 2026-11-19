package moe.nikableh.sift

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Covers the whole screen. The image starts out fitted to its slot, the room the header and the
 * folder buttons leave, where a horizontal swipe pages to the neighbour and reports prev or next.
 * A tap spreads it over the screen on black, the way Telegram opens a photo from a message. Only
 * there do pinch and double tap zoom, and dragging it up or down at 1x sends it back to its slot.
 *
 * The timing of the animations, the 3x double tap and the drag past an edge copy Telegram's photo
 * viewer. The fling does not, see [onFling]
 */
@SuppressLint("ViewConstructor")
class ZoomImageView(ctx: Context) : View(ctx) {
    /** +1 for next, -1 for previous. */
    var onSwipe: ((Int) -> Unit)? = null

    /** Fired once per image when the user first zooms in, to fetch a sharper bitmap. */
    var onZoomIn: (() -> Unit)? = null

    /** A tap on a page that doesn't open, which is a video. It can't be the start of a double tap, so it fires at once */
    var onTap: (() -> Unit)? = null

    /** The current page's offset from rest, whenever it changes. The video picture laid over the page follows it */
    var onDrag: ((Float) -> Unit)? = null

    /** Fired when a swipe makes the neighbour the current page, before [onSwipe]. */
    var onTurn: (() -> Unit)? = null

    /** True from the moment the image leaves its slot until it is back. Meanwhile it draws over the rest of the screen and takes every touch */
    var onRaise: ((Boolean) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private var prev: Bitmap? = null
    private var next: Bitmap? = null

    /** Drawn in place of [bitmap], which is its first frame. */
    private var animation: AnimatedImageDrawable? = null
    private var canPrev = false
    private var canNext = false
    private var key: String? = null
    private var zoomable = true

    /** 0 with the image in its slot, 1 with it spread over the screen. */
    private var spread = 0f
        set(value) {
            field = value
            reframe()
        }

    /** 1 fits the image to [frame]. More than that only happens with the image spread over the screen. */
    private var zoom = 1f

    /** Offset of the image's centre from the centre of [frame]. */
    private var panX = 0f
    private var panY = 0f

    /** How far the pan may go either way, as [rest] left it. */
    private var maxX = 0f
    private var maxY = 0f

    /** Offset of the current page from rest. The neighbours sit one page to either side of it. */
    private var dragX = 0f
        set(value) {
            if (field == value) return
            field = value
            onDrag?.invoke(value)
        }

    val dragOffset get() = dragX
    private var downDragX = 0f
    private var flingDir = 0
    private var settle: ValueAnimator? = null
    private var glide: ValueAnimator? = null
    private var closing = false
    private val scroller = OverScroller(ctx)
    private var raised = false
    private var lastX = 0f
    private var lastY = 0f
    private var tracking = false

    /** Where the drag that closes the image is measured from, and whether this touch may still become one. */
    private var downX = 0f
    private var downY = 0f
    private var canDismiss = false
    private var dismissing = false
    private var speed: VelocityTracker? = null

    /** True from the tap that opened the image until the next finger comes down. */
    private var openTap = false

    /** True while a glide runs that only brings an image at 1x to rest, open: the opening itself, and the return from a drag that fell short. */
    private var settling = false
    private var zoomReported = false
    private val slot = RectF()

    /** The room the image is fitted to. It grows from the slot to the whole view with [spread]. */
    private val frame = RectF()
    private val drawMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val gap = 16 * resources.displayMetrics.density
    private val bounce = (BOUNCE_DP * resources.displayMetrics.density).roundToInt()

    val isZoomed get() = zoom > 1.01f

    private val page get() = frame.width() + gap

    /**
     * Shows [b] for the photo [newKey], with its neighbours ready to slide in. A new bitmap for the
     * same photo keeps zoom and pan. A new photo arriving as the bitmap already on screen is the
     * echo of a swipe this view turned itself, so the slide carries on. A page that isn't [zoomable]
     * reports taps instead of opening. [animation] plays on the current page in place of [b].
     */
    fun show(
        newKey: String?,
        b: Bitmap?,
        prev: Bitmap?,
        next: Bitmap?,
        canPrev: Boolean,
        canNext: Boolean,
        zoomable: Boolean,
        animation: AnimatedImageDrawable?,
    ) {
        this.zoomable = zoomable
        this.prev = prev
        this.next = next
        this.canPrev = canPrev
        this.canNext = canNext
        val samePhoto = newKey == key
        key = newKey
        if (b !== bitmap) setImage(b, keepView = samePhoto && b != null)
        play(animation)
        invalidate()
    }

    private fun play(new: AnimatedImageDrawable?) {
        if (new === animation) return
        animation?.stop()
        animation?.callback = null
        animation = new
        new?.callback = this
        new?.start()
    }

    override fun verifyDrawable(who: Drawable) = who === animation || super.verifyDrawable(who)

    /** [keepView] swaps in a sharper bitmap of the same photo without resetting zoom and pan. */
    fun setImage(b: Bitmap?, keepView: Boolean = false) {
        bitmap = b
        if (!keepView) {
            settle?.cancel()
            dragX = 0f
            unzoom()
            // Nothing to show, and whatever stands in for it is under this view
            if (b == null) {
                spread = 0f
                raise()
            }
        }
        invalidate()
    }

    /** Where the image sits while it isn't spread over the screen, in this view's coordinates. */
    fun setSlot(left: Float, top: Float, right: Float, bottom: Float) {
        if (slot.left == left && slot.top == top && slot.right == right && slot.bottom == bottom) return
        slot.set(left, top, right, bottom)
        refit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = refit()

    /** The screen changed shape under the image, which may now rest somewhere it no longer can. */
    private fun refit() {
        glide?.end()
        scroller.forceFinished(true)
        reframe()
        if (isZoomed) clampPan()
        invalidate()
    }

    private fun reframe() {
        frame.set(
            slot.left * (1f - spread),
            slot.top * (1f - spread),
            slot.right + (width - slot.right) * spread,
            slot.bottom + (height - slot.bottom) * spread,
        )
    }

    private fun open() {
        if (bitmap == null || glide != null || spread != 0f) return
        glideTo(1f, 1f, 0f, 0f)
    }

    /** Returns the image to its slot from wherever it is. Safe to call over and over. */
    fun close() {
        if (closing || (spread == 0f && glide == null)) return
        glideTo(0f, 1f, 0f, 0f)
    }

    override fun onDraw(canvas: Canvas) {
        if (slot.isEmpty) return
        fling()
        val count = canvas.save()
        if (raised) {
            // As in Telegram, what is underneath shows through while the image is dragged away, up to half
            val reach = width / 4f
            val held = if (zoom == 1f) max(0.5f, 1f - min(abs(panY), reach) / reach) else 1f
            canvas.drawColor(Color.argb((spread * held * 255).roundToInt(), 0, 0, 0))
        } else {
            canvas.clipRect(slot)
        }
        if (dragX > 0f) drawPage(canvas, prev, dragX - page)
        if (dragX < 0f) drawPage(canvas, next, dragX + page)
        drawPage(canvas, bitmap, dragX, zoom, panX, panY, animation)
        canvas.restoreToCount(count)
    }

    private fun drawPage(
        canvas: Canvas,
        b: Bitmap?,
        dx: Float,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
        animation: Drawable? = null,
    ) {
        if (b == null) return
        val scale = fitScale(b) * zoom
        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(
            frame.centerX() - b.width * scale / 2f + panX + dx,
            frame.centerY() - b.height * scale / 2f + panY,
        )
        if (animation == null) {
            canvas.drawBitmap(b, drawMatrix, paint)
            return
        }
        // The still's size, so zoom and pan limits worked out from the still hold for this too
        animation.setBounds(0, 0, b.width, b.height)
        val count = canvas.save()
        canvas.concat(drawMatrix)
        animation.draw(canvas)
        canvas.restoreToCount(count)
    }

    /** Makes the neighbour the current page without moving anything on screen. */
    private fun turn(dir: Int) {
        onTurn?.invoke()
        play(null)
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
        unzoom()
    }

    /**
     * Back to 1x at once, wherever the image is. Also stops a zoom or a fling in flight, which would
     * otherwise carry on into the next photo
     */
    private fun unzoom() {
        glide?.end()
        scroller.forceFinished(true)
        zoom = 1f
        panX = 0f
        panY = 0f
        dismissing = false
        canDismiss = false
        zoomReported = false
        raise()
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

    override fun onDetachedFromWindow() {
        settle?.cancel()
        glide?.end()
        scroller.forceFinished(true)
        super.onDetachedFromWindow()
    }

    private fun fitScale(b: Bitmap) = min(frame.width() / b.width, frame.height() / b.height)

    /** Works out how far the pan may go at [zoom]. The image covers the view where it can, and sits in the middle where it can't */
    private fun rest(zoom: Float) {
        val b = bitmap ?: return
        val scale = fitScale(b) * zoom
        maxX = max(0f, (b.width * scale - width) / 2f)
        maxY = max(0f, (b.height * scale - height) / 2f)
    }

    private fun clampPan() {
        rest(zoom)
        panX = panX.coerceIn(-maxX, maxX)
        panY = panY.coerceIn(-maxY, maxY)
    }

    private fun raise() {
        val now = spread > 0f || glide != null
        if (now == raised) return
        raised = now
        onRaise?.invoke(now)
    }

    private fun reportZoomIn() {
        if (zoomReported) return
        zoomReported = true
        onZoomIn?.invoke()
    }

    /** Follows a pinch, which holds the image inside its bounds the whole way. */
    private fun zoomTo(target: Float, focusX: Float, focusY: Float) {
        val next = target.coerceIn(1f, MAX_ZOOM)
        val cx = focusX - frame.centerX()
        val cy = focusY - frame.centerY()
        val k = next / zoom
        panX = cx - (cx - panX) * k
        panY = cy - (cy - panY) * k
        zoom = next
        if (!isZoomed) {
            zoom = 1f
            panX = 0f
            panY = 0f
        } else {
            reportZoomIn()
        }
        clampPan()
        invalidate()
    }

    /**
     * Telegram's animateTo. Every value runs straight to its end, and until they get there the
     * gestures below do nothing, so a drag started meanwhile picks up once it is over. The one
     * exception is the drag that closes the image, which takes the pan away from a glide that is
     * only [settling]
     */
    private fun glideTo(toSpread: Float, toZoom: Float, toX: Float, toY: Float) {
        if (spread == toSpread && zoom == toZoom && panX == toX && panY == toY) return
        scroller.forceFinished(true)
        glide?.cancel()
        val fromSpread = spread
        val fromZoom = zoom
        val fromX = panX
        val fromY = panY
        if (toZoom > fromZoom) reportZoomIn()
        closing = toSpread == 0f
        settling = toSpread == 1f && toZoom == 1f && fromZoom == 1f
        glide = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLIDE_MS
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val f = it.animatedValue as Float
                spread = fromSpread + (toSpread - fromSpread) * f
                zoom = fromZoom + (toZoom - fromZoom) * f
                if (!dismissing) {
                    panX = fromX + (toX - fromX) * f
                    panY = fromY + (toY - fromY) * f
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (glide !== animation) return
                    glide = null
                    closing = false
                    settling = false
                    if (!cancelled) {
                        spread = toSpread
                        zoom = toZoom
                        if (!dismissing) {
                            panX = toX
                            panY = toY
                        }
                    }
                    raise()
                    invalidate()
                }
            })
        }
        raise()
        glide?.start()
    }

    /** Moves a zoomed image with the finger. Telegram's rules: a third of the speed once past an edge, and no slack to take up first */
    private fun pan(dx: Float, dy: Float) {
        rest(zoom)
        // An image with no room to move along an axis stays where it is on it
        if (maxX == 0f) panX = 0f else panX -= if (abs(panX) > maxX) dx / 3f else dx
        if (maxY == 0f) panY = 0f else panY -= if (abs(panY) > maxY) dy / 3f else dy
        invalidate()
    }

    /**
     * Telegram's drag to close. It starts 30dp into a move that is more than twice as far up or
     * down as it is sideways, and from there the image follows the finger
     */
    private fun dismiss(event: MotionEvent) {
        if (!dismissing) {
            val dx = abs(event.x - downX)
            val dy = abs(event.y - downY)
            if (dy < DISMISS_START_DP * resources.displayMetrics.density || dy / 2f <= dx) return
            dismissing = true
            // Counted from here, so the image doesn't jump by the distance it took to tell. An image
            // caught on its way back to the middle carries on from where it was
            downY = event.y - panY
        }
        panY = event.y - downY
        invalidate()
    }

    /** A hard flick closes short of the distance, as long as it goes the way the drag went. */
    private fun flickedAway(): Boolean {
        val speed = speed ?: return false
        speed.computeCurrentVelocity(1000)
        val vx = speed.xVelocity
        val vy = speed.yVelocity
        return abs(vy) > DISMISS_FLICK_DP * resources.displayMetrics.density && abs(vy) > abs(vx) * 2f && vy * panY > 0f
    }

    /** A zoomed image let go past an edge returns to it. */
    private fun springBack() {
        rest(zoom)
        val x = floor(maxX).toInt()
        val y = floor(maxY).toInt()
        if (scroller.springBack(panX.roundToInt(), panY.roundToInt(), -x, x, -y, y)) postInvalidateOnAnimation()
    }

    /** One step of the fling or of the return from past an edge, taken on every frame it draws. */
    private fun fling() {
        if (scroller.isFinished || !scroller.computeScrollOffset()) return
        panX = scroller.currX.toFloat()
        panY = scroller.currY.toFloat()
        postInvalidateOnAnimation()
    }

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            if (bitmap == null || !zoomable || glide != null || dismissing) return true
            dragX = 0f
            // A pinch in the slot opens the image first, and goes on zooming once it is open
            if (spread < 1f) open() else zoomTo(zoom * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    }).apply {
        // A tap and then a drag is the detector's one-finger zoom. Here it is a tap that opens the
        // image and a drag that closes it again
        isQuickScaleEnabled = false
    }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // In the slot a double tap means nothing, so the tap doesn't have to wait to see if it becomes one
            tap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // To the detector, a swipe that closely follows the tap that opened the image starts as a double tap
            if (bitmap == null || !zoomable || glide != null || spread < 1f || dismissing || openTap) return true
            if (isZoomed) {
                glideTo(1f, 1f, 0f, 0f)
            } else {
                val cx = e.x - frame.centerX()
                val cy = e.y - frame.centerY()
                val k = DOUBLE_TAP_ZOOM / zoom
                rest(DOUBLE_TAP_ZOOM)
                glideTo(1f, DOUBLE_TAP_ZOOM, (cx - (cx - panX) * k).coerceIn(-maxX, maxX), (cy - (cy - panY) * k).coerceIn(-maxY, maxY))
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            // Pages turn in the slot only. An open image is closed before the next one is looked at
            if (scaleDetector.isInProgress || e2.pointerCount > 1 || raised) return true
            val to = dragX - dx
            // Resists when there is no page on that side
            val blocked = (to > 0f && !canPrev) || (to < 0f && !canNext)
            dragX -= if (blocked) dx * 0.3f else dx
            invalidate()
            return true
        }

        /**
         * Telegram stops a fling dead at an edge, and drops it altogether when the image is let go
         * even a pixel past one, which a flick along an edge nearly always is. Here each axis runs on
         * its own: one past its edge comes back while the other carries on, and one that reaches its
         * edge at speed runs a little past it and returns
         */
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (glide != null) return true
            if (isZoomed) {
                rest(zoom)
                val x = floor(maxX).toInt()
                val y = floor(maxY).toInt()
                scroller.fling(
                    panX.roundToInt(), panY.roundToInt(),
                    if (x == 0) 0 else vx.roundToInt(), if (y == 0) 0 else vy.roundToInt(),
                    -x, x, -y, y,
                    if (x == 0) 0 else bounce, if (y == 0) 0 else bounce,
                )
                postInvalidateOnAnimation()
            } else if (!raised && abs(vx) > FLICK_DP * resources.displayMetrics.density && abs(vx) > abs(vy) * 1.5f) {
                flingDir = if (vx < 0) 1 else -1
            }
            return true
        }
    }).apply {
        // A finger that rests before it moves would count as a long press, and then the detector
        // reports neither the drag nor the fling that follow
        setIsLongpressEnabled(false)
    }

    private companion object {
        /** Time for a full page to slide in. Shorter slides scale down from it. */
        const val SETTLE_MS = 140f

        /** Telegram's time for a double tap zoom, used for opening and closing too. */
        const val GLIDE_MS = 250L
        const val DOUBLE_TAP_ZOOM = 3f
        const val MAX_ZOOM = 12f

        /** How far past an edge a fling may carry the image before it comes back. */
        const val BOUNCE_DP = 24f

        /** Telegram's distance before a move up or down takes hold of the image. */
        const val DISMISS_START_DP = 30f

        /**
         * The part of the view's height a drag has to cover to close the image. Telegram asks for a
         * sixth, which is a long way for a thumb, and a twelfth closed it by accident
         */
        const val DISMISS_PART = 1f / 8f

        /** The speed, in dp a second, from which a sideways move in the slot counts as a flick to the next page. */
        const val FLICK_DP = 600f

        /** The same for closing an open image, which a flick should not do by accident. */
        const val DISMISS_FLICK_DP = 1500f
    }

    private fun tap() {
        if (!zoomable) {
            onTap?.invoke()
        } else {
            openTap = spread == 0f
            open()
        }
    }

    // No click listener is set, so super plays no click sound. It only reports the click to accessibility
    override fun performClick(): Boolean {
        super.performClick()
        tap()
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            // In its slot the image has the header and the buttons lying over the rest of the view, and what they let through isn't for it
            if (!raised && !slot.contains(event.x, event.y)) return false
            flingDir = 0
            settle?.cancel()
            downDragX = dragX
            downX = event.x
            downY = event.y
            // An image still on its way open can be dragged shut already, or the swipe that follows a tap closely is lost
            canDismiss = zoom == 1f && if (glide == null) spread == 1f else settling
            speed?.recycle()
            // The detector reports no fling for a drag that starts right after a tap, which it takes for a double tap
            speed = if (canDismiss) VelocityTracker.obtain() else null
        }
        speed?.addMovement(event)
        // A second finger means a pinch, and what is left of it afterwards isn't a drag from where the first one landed
        if (action == MotionEvent.ACTION_POINTER_DOWN && !dismissing) canDismiss = false
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) scroller.forceFinished(true)
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (action == MotionEvent.ACTION_DOWN) openTap = false

        val single = event.pointerCount == 1
        if (action == MotionEvent.ACTION_MOVE && single && tracking && isZoomed && glide == null) pan(lastX - event.x, lastY - event.y)
        if (action == MotionEvent.ACTION_MOVE && single && canDismiss && zoom == 1f && (glide == null || settling)) dismiss(event)
        // The finger left over from a pinch starts from where it is now
        tracking = single
        lastX = event.x
        lastY = event.y

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Measured from where the finger landed, so catching a slide mid-way and letting go doesn't turn back
            val moved = dragX - downDragX
            if (dismissing) {
                val away = abs(panY) > height * DISMISS_PART || flickedAway()
                dismissing = false
                if (action == MotionEvent.ACTION_UP && away) close() else glideTo(1f, 1f, 0f, 0f)
            }
            speed?.recycle()
            speed = null
            var dir = when {
                raised || action == MotionEvent.ACTION_CANCEL -> 0
                // A flick back after peeking at the neighbour returns to the current photo
                flingDir != 0 -> if (moved * flingDir > 0f) 0 else flingDir
                abs(moved) > frame.width() * 0.2f -> if (moved < 0) 1 else -1
                else -> 0
            }
            if ((dir > 0 && !canNext) || (dir < 0 && !canPrev)) dir = 0
            if (dir != 0) turn(dir)
            settle()
            // Unless the detector has just started a fling, which sees to the edges itself
            if (isZoomed && glide == null && scroller.isFinished) springBack()
            // The index changes right away and only the picture trails, so a folder tap mid-slide files the new photo
            if (dir != 0) onSwipe?.invoke(dir)
        }
        return true
    }
}
