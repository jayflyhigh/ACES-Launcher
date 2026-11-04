package app.olauncher.ui.search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
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
        tileColor ?: context.getColorFromAttr(R.attr.primaryInverseColor) else null
    binding.searchPageSettings.background = context.appSearchSurface(R.drawable.bg_quick_bar, tileColor)
    val tileCountRow = binding.decreaseTileCount.parent as View
    tileCountRow.background = context.appSearchSurface(R.drawable.bg_quick_bar, tileColor)
    val defaultTextColors = context.obtainStyledAttributes(
        R.style.TextSmall, intArrayOf(android.R.attr.textColor)
    ).use { it.getColorStateList(0) }
    binding.searchPageSettings.setTextColor(defaultTextColors)
    binding.tileCount.setTextColor(defaultTextColors)
    val defaultTint = ColorStateList.valueOf(context.getColorFromAttr(R.attr.primaryColor))
    binding.decreaseTileCount.imageTintList = defaultTint
    binding.increaseTileCount.imageTintList = defaultTint
    if (popupColor != null || applyTileColorToMenu) {
        val panelColor = popupColor ?: context.getColorFromAttr(R.attr.primaryInverseColor)
        val fill = tileColor ?: ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(context.getColorFromAttr(R.attr.primaryColor), 26), panelColor,
        )
        val foreground = contrastColor(fill)
        binding.searchPageSettings.setTextColor(foreground)
        binding.tileCount.setTextColor(foreground)
        binding.decreaseTileCount.imageTintList = ColorStateList.valueOf(foreground)
        binding.increaseTileCount.imageTintList = ColorStateList.valueOf(foreground)
    }
    return panel
}

/** Shared by the real app results and the two non-launching preview rows. */
internal class AppSearchTileStyle(private val binding: ItemSearchAppBinding) {
    private val defaultTextColors = binding.itemTitle.textColors
    private val defaultShadowColor = binding.itemTitle.shadowColor
    private val defaultShadowRadius = binding.itemTitle.shadowRadius
    private val defaultShadowDx = binding.itemTitle.shadowDx
    private val defaultShadowDy = binding.itemTitle.shadowDy
    private val defaultGravity = binding.root.gravity
    private var horizontalAlignment = false
    private var appliedAppearance: AppSearchAppearance? = null
    private var appliedColumns = 0
    private var appliedLabelGravity = 0

    init {
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> constrainTitle() }
    }

    fun apply(appearance: AppSearchAppearance, columns: Int, labelGravity: Int) = with(binding) {
        if (appliedAppearance == appearance && appliedColumns == columns && appliedLabelGravity == labelGravity) {
            constrainTitle()
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
        appearance.tileColor?.let {
            itemTitle.setTextColor(contrastColor(it))
            itemTitle.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } ?: run {
            itemTitle.setTextColor(defaultTextColors)
            itemTitle.setShadowLayer(defaultShadowRadius, defaultShadowDx, defaultShadowDy, defaultShadowColor)
        }
        horizontalAlignment = appearance.alignment == AppSearchAppearance.Alignment.LEFT ||
            appearance.alignment == AppSearchAppearance.Alignment.RIGHT
        val gravity = when (appearance.alignment) {
            AppSearchAppearance.Alignment.LEFT -> Gravity.LEFT
            AppSearchAppearance.Alignment.RIGHT -> Gravity.RIGHT
            else -> labelGravity
        }
        root.gravity = if (horizontalAlignment) gravity or Gravity.CENTER_VERTICAL else defaultGravity
        itemTitle.gravity = gravity
        itemTitle.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (horizontalAlignment) ViewGroup.LayoutParams.WRAP_CONTENT else 0
            weight = if (horizontalAlignment) 0f else 1f
        }
        val gap = appearance.spacingDp?.let { (it * density).roundToInt() }
        root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            leftMargin = gap?.div(2) ?: (5 * density).roundToInt()
            rightMargin = gap?.let { it - it / 2 } ?: (5 * density).roundToInt()
            topMargin = gap?.div(2) ?: (4 * density).roundToInt()
            bottomMargin = gap?.let { it - it / 2 } ?: (4 * density).roundToInt()
        }
        constrainTitle()
    }

    private fun constrainTitle() = with(binding) {
        val iconParams = itemIcon.layoutParams as ViewGroup.MarginLayoutParams
        val iconWidth = if (itemIcon.visibility == View.GONE) 0 else
            iconParams.width + iconParams.marginStart + iconParams.marginEnd
        val maxWidth = if (horizontalAlignment && root.width > 0)
            (root.width - root.paddingLeft - root.paddingRight - iconWidth).coerceAtLeast(0)
        else Int.MAX_VALUE
        if (itemTitle.maxWidth != maxWidth) itemTitle.maxWidth = maxWidth
    }
}
