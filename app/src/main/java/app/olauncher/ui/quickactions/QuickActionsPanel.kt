package app.olauncher.ui.quickactions

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Bottom panel that follows a downward drag and asks to be dismissed once the drag
 * is long or fast enough. Drags starting on child tiles are intercepted too, unless
 * [scrollChild] can still scroll up.
 */
class QuickActionsPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var scrollChild: View? = null
    var maxDragDistance: Float = 0f
    var onDragStarted: (() -> Unit)? = null
    var onDragProgress: ((Float) -> Unit)? = null
    var onDragReleased: ((Float) -> Unit)? = null

    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startTranslationY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null
    private var lastTrackedEventTime = Long.MIN_VALUE
    private var lastTrackedAction = -1
    private var lastTrackedX = Float.NaN
    private var lastTrackedY = Float.NaN

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                startTranslationY = translationY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - downY
                val dx = abs(ev.rawX - downX)
                val closing = dy > touchSlop && scrollChild?.canScrollVertically(-1) != true
                val reopening = dy < -touchSlop && startTranslationY > 0f && scrollChild?.canScrollVertically(-1) != true
                if (abs(dy) > dx && (closing || reopening)) {
                    if (!dragging) {
                        onDragStarted?.invoke()
                        startTranslationY = translationY
                        downY = ev.rawY
                    }
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) recycleTracker()
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                startTranslationY = translationY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - downY
                if (dragging) {
                    val range = maxDragDistance.takeIf { it > 0f } ?: height.toFloat()
                    translationY = (startTranslationY + dy).coerceIn(0f, range)
                    reportDragProgress()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val tracker = velocityTracker
                    tracker?.computeCurrentVelocity(1000)
                    val velocityY = tracker?.yVelocity ?: 0f
                    onDragReleased?.invoke(if (ev.actionMasked == MotionEvent.ACTION_UP) velocityY else 0f)
                } else if (ev.actionMasked == MotionEvent.ACTION_UP) performClick()
                dragging = false
                recycleTracker()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun reportDragProgress() {
        val range = maxDragDistance.takeIf { it > 0f } ?: height.toFloat()
        if (range > 0f) onDragProgress?.invoke(1f - (translationY / range).coerceIn(0f, 1f))
    }

    // Raw coordinates, since the panel moves under the finger while dragging
    private fun trackVelocity(ev: MotionEvent) {
        if (lastTrackedEventTime == ev.eventTime && lastTrackedAction == ev.actionMasked &&
            lastTrackedX == ev.rawX && lastTrackedY == ev.rawY
        ) return
        lastTrackedEventTime = ev.eventTime
        lastTrackedAction = ev.actionMasked
        lastTrackedX = ev.rawX
        lastTrackedY = ev.rawY
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX, ev.rawY)
        tracker.addMovement(copy)
        copy.recycle()
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
        lastTrackedEventTime = Long.MIN_VALUE
        lastTrackedAction = -1
        lastTrackedX = Float.NaN
        lastTrackedY = Float.NaN
    }
}
