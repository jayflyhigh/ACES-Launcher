package app.olauncher.ui.widgets

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import kotlin.math.abs

/**
 * Hosts one live widget. Long press enters edit mode, where the frame can be dragged,
 * resized from the corner handle, removed, or closed with "Done".
 */
@SuppressLint("ViewConstructor")
class WidgetFrame(
    context: Context,
    val widgetId: Int,
    val hostView: AppWidgetHostView,
    private val callbacks: Callbacks,
) : FrameLayout(context) {

    interface Callbacks {
        fun onEditModeChanged(frame: WidgetFrame, editing: Boolean)
        fun onBoundsChanged(frame: WidgetFrame)
        fun onRemoveRequested(frame: WidgetFrame)
    }

    var minWidthPx = 48.dpToPx()
    var minHeightPx = 48.dpToPx()

    var isEditing = false
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val editOverlay = FrameLayout(context)
    private val removeButton = controlButton("✕", R.string.remove_widget, Gravity.TOP or Gravity.END)
    private val doneButton = controlButton("✓", R.string.done, Gravity.TOP or Gravity.START)
    private val resizeHandle = controlButton("⤡", R.string.resize, Gravity.BOTTOM or Gravity.END)
    private val controls = listOf(removeButton, doneButton, resizeHandle)

    private var downRawX = 0f
    private var downRawY = 0f
    private var startLeft = 0
    private var startTop = 0
    private var startWidth = 0
    private var startHeight = 0
    private var longPressPosted = false
    private var draggingFrame = false
    private var touchOnControl = false

    private val longPressRunnable = Runnable {
        longPressPosted = false
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        setEditing(true)
    }

    init {
        addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        editOverlay.setBackgroundResource(R.drawable.bg_widget_edit)
        editOverlay.isVisible = false
        controls.forEach { editOverlay.addView(it) }
        addView(editOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        doneButton.setOnClickListener { setEditing(false) }
        removeButton.setOnClickListener { callbacks.onRemoveRequested(this) }
        resizeHandle.setOnTouchListener { _, ev -> handleResize(ev) }
    }

    fun setEditing(editing: Boolean) {
        if (isEditing == editing) return
        isEditing = editing
        editOverlay.isVisible = editing
        if (editing) bringToFront()
        callbacks.onEditModeChanged(this, editing)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isEditing) {
            // A gesture that starts on a control stays with it, so its click/drag isn't cancelled
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                touchOnControl = isOnControl(ev)
                if (!touchOnControl) recordDown(ev)
            }
            return !touchOnControl
        }
        trackLongPress(ev)
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEditing) {
            // Consume touches on non-interactive widget areas so they don't reach the home gestures
            trackLongPress(ev)
            return true
        }
        val params = layoutParams as LayoutParams
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> recordDown(ev)
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!draggingFrame && (abs(dx) > touchSlop || abs(dy) > touchSlop)) draggingFrame = true
                if (draggingFrame) {
                    val parentView = parent as? View ?: return true
                    val left = (startLeft + dx).coerceIn(0f, (parentView.width - params.width).coerceAtLeast(0).toFloat())
                    val top = (startTop + dy).coerceIn(0f, (parentView.height - params.height).coerceAtLeast(0).toFloat())
                    translationX = left - startLeft
                    translationY = top - startTop
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingFrame) {
                    params.leftMargin = startLeft + translationX.toInt()
                    params.topMargin = startTop + translationY.toInt()
                    translationX = 0f
                    translationY = 0f
                    layoutParams = params
                    callbacks.onBoundsChanged(this)
                }
                draggingFrame = false
            }
        }
        return true
    }

    private fun handleResize(ev: MotionEvent): Boolean {
        val params = layoutParams as LayoutParams
        val parentView = parent as? View ?: return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                recordDown(ev)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val maxWidth = (parentView.width - params.leftMargin).coerceAtLeast(minWidthPx)
                val maxHeight = (parentView.height - params.topMargin).coerceAtLeast(minHeightPx)
                params.width = (startWidth + (ev.rawX - downRawX).toInt()).coerceIn(minWidthPx, maxWidth)
                params.height = (startHeight + (ev.rawY - downRawY).toInt()).coerceIn(minHeightPx, maxHeight)
                layoutParams = params
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> callbacks.onBoundsChanged(this)
        }
        return true
    }

    private fun recordDown(ev: MotionEvent) {
        val params = layoutParams as LayoutParams
        downRawX = ev.rawX
        downRawY = ev.rawY
        startLeft = params.leftMargin
        startTop = params.topMargin
        startWidth = params.width
        startHeight = params.height
        draggingFrame = false
    }

    private fun trackLongPress(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchOnControl = false
                recordDown(ev)
                cancelLongPress()
                postDelayed(longPressRunnable, longPressTimeout)
                longPressPosted = true
            }

            MotionEvent.ACTION_MOVE ->
                if (abs(ev.rawX - downRawX) > touchSlop || abs(ev.rawY - downRawY) > touchSlop) cancelLongPress()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPress()
        }
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        if (longPressPosted) removeCallbacks(longPressRunnable)
        longPressPosted = false
    }

    private fun isOnControl(ev: MotionEvent): Boolean {
        val rect = Rect()
        return controls.any {
            it.getHitRect(rect)
            rect.contains(ev.x.toInt(), ev.y.toInt())
        }
    }

    private fun controlButton(symbol: String, description: Int, gravity: Int): TextView {
        val size = 36.dpToPx()
        return TextView(context).apply {
            text = symbol
            contentDescription = context.getString(description)
            this.gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryInverseColor))
            setBackgroundResource(R.drawable.bg_widget_handle)
            layoutParams = LayoutParams(size, size, gravity).apply { setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx()) }
        }
    }

    override fun onDetachedFromWindow() {
        cancelLongPress()
        super.onDetachedFromWindow()
    }
}
