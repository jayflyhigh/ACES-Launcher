package app.olauncher.ui.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import app.olauncher.R
import app.olauncher.data.AppSearchAppearance
import app.olauncher.databinding.ItemSearchAppBinding
import app.olauncher.databinding.PopupSearchPageMenuBinding
import app.olauncher.helper.isSystemAnimationsDisabled
import kotlin.math.roundToInt

internal fun Context.appSearchSurface(resource: Int, color: Int? = null): Drawable {
    val drawable = requireNotNull(ContextCompat.getDrawable(this, resource)).mutate()
    if (color != null) {
        when (drawable) {
            is RippleDrawable -> {
                (drawable.getDrawable(0) as GradientDrawable).setColor(color)
                drawable.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(contrastColor(color), 80)))
            }
            is GradientDrawable -> drawable.setColor(color)
        }
    }
    return drawable
}

internal fun contrastColor(background: Int): Int =
    if (ColorUtils.calculateContrast(Color.WHITE, background or Color.BLACK) >=
        ColorUtils.calculateContrast(Color.BLACK, background or Color.BLACK)
    ) Color.WHITE else Color.BLACK

internal fun AppSearchAppearance.applyMenu(binding: PopupSearchPageMenuBinding): Drawable {
    // Keep the window's existing drawable in sync while its popup is already open.
    val panel = (binding.root.background as GradientDrawable).mutate() as GradientDrawable
    panel.setColor(popupColor ?: Color.rgb(32, 32, 32))
    val tileColor = if (applyTileColorToMenu)
        tileColor ?: Color.rgb(32, 32, 32) else menuTileColor ?: Color.rgb(40, 40, 40)
    binding.searchPageSettings.background = searchTileSurface(binding.root.context, tileColor)
    binding.searchPageAppearance.background = searchTileSurface(binding.root.context, tileColor)
    for (index in 0 until binding.contactCommands.childCount) {
        (binding.contactCommands.getChildAt(index) as TextView).apply {
            background = searchTileSurface(context, tileColor)
            setTextColor(Color.WHITE)
        }
    }
    val tileCountRow = binding.decreaseTileCount.parent as View
    tileCountRow.background = searchTileSurface(binding.root.context, tileColor)
    binding.searchPageSettings.setTextColor(Color.WHITE)
    binding.searchPageAppearance.setTextColor(Color.WHITE)
    binding.tileCount.setTextColor(Color.WHITE)
    binding.decreaseTileCount.imageTintList = ColorStateList.valueOf(Color.WHITE)
    binding.increaseTileCount.imageTintList = ColorStateList.valueOf(Color.WHITE)
    return panel
}

/** Shared by the real app results and the two non-launching preview rows. */
internal class AppSearchTileStyle(private val binding: ItemSearchAppBinding) {
    private var appliedAppearance: AppSearchAppearance? = null
    private var appliedColumns = 0
    private var appliedLabelGravity = 0

    fun apply(appearance: AppSearchAppearance, columns: Int, labelGravity: Int) = with(binding) {
        if (appliedAppearance == appearance && appliedColumns == columns && appliedLabelGravity == labelGravity) {
            return@with
        }
        appliedAppearance = appearance
        appliedColumns = columns
        appliedLabelGravity = labelGravity
        val density = root.resources.displayMetrics.density
        val padding = ((if (columns == 2) 8 else 20) * density).roundToInt()
        root.setPadding(padding, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
        itemIcon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = ((if (columns == 2) 8 else 20) * density).roundToInt()
            width = ((if (columns == 2) 28 else 44) * density).roundToInt()
            height = width
        }
        itemTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (columns == 2) 14f else 18f)
        itemTitle.maxLines = if (columns == 2) 2 else 1
        root.background = searchTileSurface(root.context, appearance.tileColor)
        itemTitle.setTextColor(Color.WHITE)
        itemTitle.gravity = labelGravity
        itemType.gravity = labelGravity
        appearance.applyResultSpacing(root)
    }
}

internal fun searchTileSurface(context: Context, color: Int? = null): Drawable {
    val base = color ?: ContextCompat.getColor(context, R.color.search_surface)
    val pressed = ContextCompat.getColor(context, R.color.search_surface_pressed)
    val outline = ContextCompat.getColor(context, R.color.search_outline)
    val activeOutline = ContextCompat.getColor(context, R.color.search_outline_active)
    val accent = ContextCompat.getColor(context, R.color.search_accent)
    fun shape(fill: Int, stroke: Int = outline) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = context.resources.getDimension(R.dimen.search_corner_radius)
        setStroke(context.resources.displayMetrics.density.roundToInt().coerceAtLeast(1), stroke)
    }
    return android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
        addState(intArrayOf(android.R.attr.state_focused), shape(base, activeOutline))
        addState(
            intArrayOf(android.R.attr.state_activated),
            shape(ColorUtils.blendARGB(base, accent, 0.12f), activeOutline),
        )
        addState(intArrayOf(), shape(base))
        val duration = if (context.isSystemAnimationsDisabled()) 0 else 80
        setEnterFadeDuration(duration)
        setExitFadeDuration(duration)
    }
}

private fun AppSearchAppearance.applyResultSpacing(view: View) {
    val density = view.resources.displayMetrics.density
    val gap = spacingDp?.let { (it * density).roundToInt() }
    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        leftMargin = gap?.div(2) ?: (2 * density).roundToInt()
        rightMargin = gap?.let { it - it / 2 } ?: (2 * density).roundToInt()
        topMargin = gap?.div(2) ?: (4 * density).roundToInt()
        bottomMargin = gap?.let { it - it / 2 } ?: (4 * density).roundToInt()
    }
}

internal fun AppSearchAppearance.applyResultsPadding(view: View, columns: Int) {
    val density = view.resources.displayMetrics.density
    val horizontal = fitResultPadding(
        (paddingLeftDp * density).roundToInt(), (paddingRightDp * density).roundToInt(),
        view.width, (96 * columns * density).roundToInt(),
    )
    val vertical = fitResultPadding(
        (paddingTopDp * density).roundToInt(), (paddingBottomDp * density).roundToInt(),
        view.height, (48 * density).roundToInt(),
    )
    if (view.paddingLeft != horizontal.first || view.paddingRight != horizontal.second ||
        view.paddingTop != vertical.first || view.paddingBottom != vertical.second
    ) view.updatePadding(left = horizontal.first, right = horizontal.second, top = vertical.first, bottom = vertical.second)
}

internal fun fitResultPadding(first: Int, second: Int, available: Int, minimumContent: Int): Pair<Int, Int> {
    val budget = (available - minimumContent).coerceAtLeast(0)
    val total = first + second
    if (total <= budget || total == 0) return first to second
    val scaledFirst = (first.toFloat() * budget / total).roundToInt()
    return scaledFirst to budget - scaledFirst
}
