package app.olauncher.ui.quickactions

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max

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
    var onDismissRequested: (() -> Unit)? = null
    var onDragProgress: ((Float) -> Unit)? = null

    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private val minFlingVelocity = config.scaledMinimumFlingVelocity * 4
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - downY
                val dx = abs(ev.rawX - downX)
                if (dy > touchSlop && dy > dx && scrollChild?.canScrollVertically(-1) != true) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> recycleTracker()
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = ev.rawY - downY
                if (!dragging && dy > touchSlop) dragging = true
                if (dragging) {
                    translationY = max(0f, dy - touchSlop)
                    if (height > 0) onDragProgress?.invoke(translationY / height)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    val tracker = velocityTracker
                    tracker?.computeCurrentVelocity(1000)
                    val velocityY = tracker?.yVelocity ?: 0f
                    if (ev.actionMasked == MotionEvent.ACTION_UP &&
                        (translationY > height * 0.25f || velocityY > minFlingVelocity)
                    ) onDismissRequested?.invoke()
                    else settleBack()
                } else if (ev.actionMasked == MotionEvent.ACTION_UP) performClick()
                dragging = false
                recycleTracker()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun settleBack() {
        animate().translationY(0f).setDuration(150)
            .setUpdateListener { if (height > 0) onDragProgress?.invoke(translationY / height) }
            .withEndAction { animate().setUpdateListener(null) }
            .start()
    }

    // Raw coordinates, since the panel moves under the finger while dragging
    private fun trackVelocity(ev: MotionEvent) {
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX, ev.rawY)
        tracker.addMovement(copy)
        copy.recycle()
    }

    private fun recycleTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
