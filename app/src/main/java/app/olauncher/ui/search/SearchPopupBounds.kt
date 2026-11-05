package app.olauncher.ui.search

import android.graphics.Rect

internal fun searchPopupBounds(
    visible: Rect,
    anchorX: Int,
    anchorY: Int,
    anchorWidth: Int,
    anchorHeight: Int,
    width: Int,
    height: Int,
): Rect {
    val popupWidth = width.coerceIn(1, visible.width().coerceAtLeast(1))
    val below = (visible.bottom - anchorY - anchorHeight).coerceAtLeast(0)
    val above = (anchorY - visible.top).coerceAtLeast(0)
    val down = height <= below || below >= above
    val popupHeight = height.coerceIn(1, (if (down) below else above).coerceAtLeast(1))
    val left = (anchorX + anchorWidth - popupWidth).coerceIn(visible.left, (visible.right - popupWidth).coerceAtLeast(visible.left))
    val top = if (down) anchorY + anchorHeight else anchorY - popupHeight
    val safeTop = top.coerceIn(visible.top, (visible.bottom - popupHeight).coerceAtLeast(visible.top))
    return Rect(left, safeTop, left + popupWidth, safeTop + popupHeight)
}

internal fun centeredSearchPopupBounds(
    visible: Rect,
    anchorX: Int,
    anchorY: Int,
    anchorWidth: Int,
    anchorHeight: Int,
    width: Int,
    height: Int,
): Rect {
    val popupWidth = width.coerceIn(1, visible.width().coerceAtLeast(1))
    val popupHeight = height.coerceIn(1, visible.height().coerceAtLeast(1))
    val maxLeft = (visible.right - popupWidth).coerceAtLeast(visible.left)
    val maxTop = (visible.bottom - popupHeight).coerceAtLeast(visible.top)
    val left = (anchorX + (anchorWidth - popupWidth) / 2).coerceIn(visible.left, maxLeft)
    val top = (anchorY + (anchorHeight - popupHeight) / 2).coerceIn(visible.top, maxTop)
    return Rect(left, top, left + popupWidth, top + popupHeight)
}
