package app.olauncher.data

import kotlin.math.roundToInt

data class AppSearchAppearance(
    val tileColor: Int? = null,
    val applyTileColorToMenu: Boolean = false,
    val backgroundColor: Int? = null,
    val backgroundTransparency: Int? = null,
    val popupColor: Int? = null,
    val menuTileColor: Int? = null,
    val spacingDp: Int? = null,
    val paddingLeftDp: Int = 0,
    val paddingRightDp: Int = 0,
    val paddingTopDp: Int = 0,
    val paddingBottomDp: Int = 0,
) {
    init {
        require(backgroundTransparency == null || backgroundTransparency in 0..100)
        require(spacingDp == null || spacingDp in 0..MAX_SPACING_DP)
        require(listOf(paddingLeftDp, paddingRightDp, paddingTopDp, paddingBottomDp).all { it in 0..MAX_PADDING_DP })
    }

    fun background(defaultColor: Int): Int {
        val color = backgroundColor ?: defaultColor
        val alpha = backgroundTransparency?.let { ((100 - it) * 255 / 100f).roundToInt() }
            ?: (defaultColor ushr 24)
        return (color and 0x00ffffff) or (alpha shl 24)
    }

    fun save(columns: Int, write: (String, Int?) -> Unit) {
        val prefix = prefix(columns)
        write(SHARED_COLORS + "SAVED", 1)
        mapOf(
            "TILE_COLOR" to tileColor,
            "MENU_TILES" to if (applyTileColorToMenu) 1 else 0,
            "BACKGROUND_COLOR" to backgroundColor,
            "TRANSPARENCY" to backgroundTransparency,
            "POPUP_COLOR" to popupColor,
            "MENU_TILE_COLOR" to menuTileColor,
        ).forEach { (key, value) -> write(SHARED_COLORS + key, value) }
        write(prefix + "SPACING", spacingDp)
        write(prefix + "PADDING_LEFT", paddingLeftDp)
        write(prefix + "PADDING_RIGHT", paddingRightDp)
        write(prefix + "PADDING_TOP", paddingTopDp)
        write(prefix + "PADDING_BOTTOM", paddingBottomDp)
    }

    companion object {
        const val MAX_SPACING_DP = 32
        const val MAX_PADDING_DP = 64
        private const val SHARED_COLORS = "APP_SEARCH_APPEARANCE_COLORS_"

        private fun prefix(columns: Int): String {
            require(columns in 1..2)
            return "APP_SEARCH_APPEARANCE_${columns}_"
        }

        fun load(columns: Int, read: (String) -> Int?): AppSearchAppearance {
            val prefix = prefix(columns)
            fun value(key: String) = read(prefix + key)
            val sharedColorsSaved = read(SHARED_COLORS + "SAVED") == 1
            fun color(key: String): Int? = if (sharedColorsSaved) read(SHARED_COLORS + key)
                // Prefer the old one-column color, or retain the two-column color if it was the only one set.
                else read(prefix(1) + key) ?: read(prefix(2) + key)
            return AppSearchAppearance(
                tileColor = color("TILE_COLOR"),
                applyTileColorToMenu = color("MENU_TILES") == 1,
                backgroundColor = color("BACKGROUND_COLOR"),
                backgroundTransparency = color("TRANSPARENCY"),
                popupColor = color("POPUP_COLOR"),
                menuTileColor = color("MENU_TILE_COLOR"),
                spacingDp = value("SPACING"),
                paddingLeftDp = value("PADDING_LEFT") ?: 0,
                paddingRightDp = value("PADDING_RIGHT") ?: 0,
                paddingTopDp = value("PADDING_TOP") ?: 0,
                paddingBottomDp = value("PADDING_BOTTOM") ?: 0,
            )
        }
    }
}
