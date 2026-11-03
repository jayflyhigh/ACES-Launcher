package app.olauncher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.HomeWidget
import app.olauncher.data.Prefs
import app.olauncher.data.WidgetStore
import app.olauncher.helper.createDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.showToast
import kotlin.math.max
import kotlin.math.roundToInt

/** Places, restores and persists the live widgets shown on the home screen. */
class HomeWidgets(
    private val fragment: Fragment,
    private val container: FrameLayout,
    private val prefs: Prefs,
    private val host: AppWidgetHost,
    private val manager: AppWidgetManager,
) : WidgetFrame.Callbacks {

    private val context = fragment.requireContext()
    private val frames = LinkedHashMap<Int, WidgetFrame>()
    private var editingFrame: WidgetFrame? = null
    private val edgeMargin = 16.dpToPx()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitEditMode()
        }
    }

    init {
        fragment.requireActivity().onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner, backCallback)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val lp = view.layoutParams as FrameLayout.LayoutParams
            if (lp.topMargin != bars.top || lp.bottomMargin != bars.bottom || lp.leftMargin != bars.left || lp.rightMargin != bars.right) {
                lp.setMargins(bars.left, bars.top, bars.right, bars.bottom)
                view.layoutParams = lp
            }
            insets
        }
        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) container.post { layoutFrames() }
        }
    }

    fun refresh() {
        val stored = WidgetStore.load(prefs)
        frames.keys.filter { id -> stored.none { it.id == id } }.forEach { id ->
            frames.remove(id)?.let { container.removeView(it) }
        }

        val valid = stored.filter { widget ->
            val info = manager.getAppWidgetInfo(widget.id)
            if (info == null) {
                // Provider was uninstalled or the binding was lost
                host.deleteAppWidgetId(widget.id)
                frames.remove(widget.id)?.let { container.removeView(it) }
                false
            } else {
                if (!frames.containsKey(widget.id)) frames[widget.id] = createFrame(widget.id, info)
                true
            }
        }
        if (valid.size != stored.size) WidgetStore.save(prefs, valid)
        container.doOnLayout { layoutFrames() }
    }

    /** Returns true if a widget was being edited. */
    fun exitEditMode(): Boolean {
        val frame = editingFrame ?: return false
        frame.setEditing(false)
        return true
    }

    private fun createFrame(id: Int, info: AppWidgetProviderInfo): WidgetFrame {
        val hostView = host.createView(context, id, info)
        val frame = WidgetFrame(context, id, hostView, this).apply {
            minWidthPx = max(48.dpToPx(), info.minResizeWidth.takeIf { it > 0 } ?: info.minWidth)
            minHeightPx = max(48.dpToPx(), info.minResizeHeight.takeIf { it > 0 } ?: info.minHeight)
        }
        container.addView(frame, FrameLayout.LayoutParams(0, 0))
        return frame
    }

    private fun layoutFrames() {
        val width = container.width
        val height = container.height
        if (width <= 0 || height <= 0) return
        val stored = WidgetStore.load(prefs)
        var changed = false

        stored.forEach { widget ->
            val frame = frames[widget.id] ?: return@forEach
            if (!widget.isPlaced) {
                placeNewWidget(widget, frame, stored, width, height)
                changed = true
            }
            val w = (widget.w * width).roundToInt().coerceIn(frame.minWidthPx.coerceAtMost(width), width)
            val h = (widget.h * height).roundToInt().coerceIn(frame.minHeightPx.coerceAtMost(height), height)
            val left = (widget.x * width).roundToInt().coerceIn(0, width - w)
            val top = (widget.y * height).roundToInt().coerceIn(0, height - h)
            val lp = frame.layoutParams as FrameLayout.LayoutParams
            if (lp.width != w || lp.height != h || lp.leftMargin != left || lp.topMargin != top) {
                lp.width = w
                lp.height = h
                lp.leftMargin = left
                lp.topMargin = top
                frame.layoutParams = lp
                updateWidgetSize(frame, w, h)
            }
        }
        if (changed) WidgetStore.save(prefs, stored)
    }

    // New widgets go full width below existing ones, or centered if there's no room left
    private fun placeNewWidget(widget: HomeWidget, frame: WidgetFrame, all: List<HomeWidget>, width: Int, height: Int) {
        val info = manager.getAppWidgetInfo(widget.id)
        val w = max(width - 2 * edgeMargin, frame.minWidthPx).coerceAtMost(width)
        val h = (info?.minHeight ?: 0).coerceIn(100.dpToPx(), (height * 0.4f).toInt().coerceAtLeast(100.dpToPx()))
            .coerceAtMost(height)
        val lowestBottom = all.filter { it.isPlaced && it.id != widget.id }
            .maxOfOrNull { ((it.y + it.h) * height).roundToInt() } ?: (height * 0.12f).roundToInt()
        val top = if (lowestBottom + edgeMargin + h <= height) lowestBottom + edgeMargin else (height - h) / 2
        widget.x = ((width - w) / 2f) / width
        widget.y = top.toFloat() / height
        widget.w = w.toFloat() / width
        widget.h = h.toFloat() / height
    }

    private fun updateWidgetSize(frame: WidgetFrame, widthPx: Int, heightPx: Int) {
        val density = context.resources.displayMetrics.density
        val wDp = widthPx / density
        val hDp = heightPx / density
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                frame.hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
            else
                @Suppress("DEPRECATION")
                frame.hostView.updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onEditModeChanged(frame: WidgetFrame, editing: Boolean) {
        if (editing) {
            editingFrame?.takeIf { it != frame }?.setEditing(false)
            editingFrame = frame
        } else if (editingFrame == frame) {
            editingFrame = null
        }
        backCallback.isEnabled = editingFrame != null
    }

    override fun onBoundsChanged(frame: WidgetFrame) {
        val width = container.width.toFloat()
        val height = container.height.toFloat()
        if (width <= 0f || height <= 0f) return
        val lp = frame.layoutParams as FrameLayout.LayoutParams
        WidgetStore.update(
            prefs,
            HomeWidget(frame.widgetId, lp.leftMargin / width, lp.topMargin / height, lp.width / width, lp.height / height)
        )
        updateWidgetSize(frame, lp.width, lp.height)
    }

    override fun onRemoveRequested(frame: WidgetFrame) {
        context.createDialog(
            title = R.string.remove_widget,
            action = R.string.remove,
            message = R.string.remove_widget_message,
            onAction = { removeWidget(frame) },
        ).showRespectingStatusBar()
    }

    override fun onSettingsRequested(frame: WidgetFrame) {
        val info = manager.getAppWidgetInfo(frame.widgetId)
        val configure = info?.configure
        if (configure == null) {
            context.showToast(R.string.widget_settings_unavailable)
            return
        }
        val activity = fragment.activity as? Activity ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                host.startAppWidgetConfigureActivityForResult(
                    activity, frame.widgetId, 0, WIDGET_SETTINGS_REQUEST, null
                )
            } else {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, frame.widgetId),
                    WIDGET_SETTINGS_REQUEST
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            context.showToast(R.string.widget_settings_unavailable)
        }
    }

    private fun removeWidget(frame: WidgetFrame) {
        frame.setEditing(false)
        frames.remove(frame.widgetId)
        container.removeView(frame)
        host.deleteAppWidgetId(frame.widgetId)
        WidgetStore.remove(prefs, frame.widgetId)
    }

    private companion object {
        const val WIDGET_SETTINGS_REQUEST = 701
    }
}
