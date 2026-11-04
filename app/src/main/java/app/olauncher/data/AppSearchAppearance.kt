package app.olauncher.data

import kotlin.math.roundToInt

data class AppSearchAppearance(
    val tileColor: Int? = null,
    val applyTileColorToMenu: Boolean = false,
    val backgroundColor: Int? = null,
    val backgroundTransparency: Int? = null,
    val popupColor: Int? = null,
    val alignment: Alignment? = null,
    val spacingDp: Int? = null,
) {
    enum class Alignment { TOP, BOTTOM, LEFT, RIGHT }

    init {
        require(backgroundTransparency == null || backgroundTransparency in 0..100)
        require(spacingDp == null || spacingDp in 0..MAX_SPACING_DP)
    }

    fun background(defaultColor: Int): Int {
        val color = backgroundColor ?: defaultColor
        val alpha = backgroundTransparency?.let { ((100 - it) * 255 / 100f).roundToInt() }
            ?: (defaultColor ushr 24)
        return (color and 0x00ffffff) or (alpha shl 24)
    }

    fun save(columns: Int, write: (String, Int?) -> Unit) {
        val prefix = prefix(columns)
        mapOf(
            "TILE_COLOR" to tileColor,
            "MENU_TILES" to if (applyTileColorToMenu) 1 else 0,
            "BACKGROUND_COLOR" to backgroundColor,
            "TRANSPARENCY" to backgroundTransparency,
            "POPUP_COLOR" to popupColor,
            "ALIGNMENT" to alignment?.ordinal,
            "SPACING" to spacingDp,
        ).forEach { (key, value) -> write(prefix + key, value) }
    }

    companion object {
        const val MAX_SPACING_DP = 32

        private fun prefix(columns: Int): String {
            require(columns in 1..2)
            return "APP_SEARCH_APPEARANCE_${columns}_"
        }

        fun load(columns: Int, read: (String) -> Int?): AppSearchAppearance {
            val prefix = prefix(columns)
            fun value(key: String) = read(prefix + key)
            return AppSearchAppearance(
                tileColor = value("TILE_COLOR"),
                applyTileColorToMenu = value("MENU_TILES") == 1,
                backgroundColor = value("BACKGROUND_COLOR"),
                backgroundTransparency = value("TRANSPARENCY"),
                popupColor = value("POPUP_COLOR"),
                alignment = value("ALIGNMENT")?.let { Alignment.entries[it] },
                spacingDp = value("SPACING"),
            )
        }
    }
}
