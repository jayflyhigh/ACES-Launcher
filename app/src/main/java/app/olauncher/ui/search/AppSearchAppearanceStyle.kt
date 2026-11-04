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
import app.olauncher.helper.getColorFromAttr
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
    val context = binding.root.context
    // Keep the window's existing drawable in sync while its popup is already open.
    val panel = (binding.root.background as GradientDrawable).mutate() as GradientDrawable
    panel.setColor(popupColor ?: context.getColorFromAttr(R.attr.primaryColorInverseTrans90))
    val tileColor = if (applyTileColorToMenu)
        tileColor ?: context.getColorFromAttr(R.attr.primaryInverseColor) else menuTileColor
    binding.searchPageSettings.background = context.appSearchSurface(R.drawable.bg_quick_bar, tileColor)
    val tileCountRow = binding.decreaseTileCount.parent as View
    tileCountRow.background = context.appSearchSurface(R.drawable.bg_quick_bar, tileColor)
    binding.searchPageSettings.setTextColor(Color.WHITE)
    binding.tileCount.setTextColor(Color.WHITE)
    binding.decreaseTileCount.imageTintList = ColorStateList.valueOf(Color.WHITE)
    binding.increaseTileCount.imageTintList = ColorStateList.valueOf(Color.WHITE)
    return panel
}

/** Shared by the real app results and the two non-launching preview rows. */
internal class AppSearchTileStyle(private val binding: ItemSearchAppBinding) {
    private val defaultShadowColor = binding.itemTitle.shadowColor
    private val defaultShadowRadius = binding.itemTitle.shadowRadius
    private val defaultShadowDx = binding.itemTitle.shadowDx
    private val defaultShadowDy = binding.itemTitle.shadowDy
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
        val padding = ((if (columns == 2) 12 else 24) * density).roundToInt()
        root.setPadding(padding, 0, padding, 0)
        itemIcon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = ((if (columns == 2) 8 else 16) * density).roundToInt()
        }
        itemTitle.maxLines = if (columns == 2) 2 else 1
        root.background = root.context.appSearchSurface(R.drawable.bg_search_result, appearance.tileColor)
        itemTitle.setTextColor(Color.WHITE)
        itemTitle.setShadowLayer(defaultShadowRadius, defaultShadowDx, defaultShadowDy, defaultShadowColor)
        itemTitle.gravity = labelGravity
        appearance.applyResultSpacing(root)
    }
}

internal fun AppSearchAppearance.applyResultSurface(view: View, vararg labels: TextView) {
    view.background = view.context.appSearchSurface(R.drawable.bg_search_result, tileColor)
    labels.forEach { it.setTextColor(Color.WHITE) }
    applyResultSpacing(view)
}

private fun AppSearchAppearance.applyResultSpacing(view: View) {
    val density = view.resources.displayMetrics.density
    val gap = spacingDp?.let { (it * density).roundToInt() }
    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        leftMargin = gap?.div(2) ?: (5 * density).roundToInt()
        rightMargin = gap?.let { it - it / 2 } ?: (5 * density).roundToInt()
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
