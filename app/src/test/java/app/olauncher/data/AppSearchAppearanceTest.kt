package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchAppearanceTest {
    private val saved = mutableMapOf<String, Int>()

    private fun save(columns: Int, appearance: AppSearchAppearance) {
        appearance.save(columns) { key, value ->
            if (value == null) saved.remove(key) else saved[key] = value
        }
    }

    private fun load(columns: Int) = AppSearchAppearance.load(columns, saved::get)

    @Test
    fun `unsaved layouts preserve themed defaults`() {
        for (columns in 1..2) {
            assertEquals(AppSearchAppearance(), load(columns))
            assertEquals(0x40ffffff, load(columns).background(0x40ffffff))
            assertNull(load(columns).spacingDp)
            assertNull(load(columns).alignment)
            assertFalse(load(columns).applyTileColorToMenu)
        }
    }

    @Test
    fun `all appearance fields survive a saved preferences round trip`() {
        val appearance = AppSearchAppearance(
            tileColor = 0xff123456.toInt(),
            applyTileColorToMenu = true,
            backgroundColor = 0xffabcdef.toInt(),
            backgroundTransparency = 73,
            popupColor = 0xff223344.toInt(),
            alignment = AppSearchAppearance.Alignment.RIGHT,
            spacingDp = 21,
        )
        save(1, appearance)
        assertEquals(appearance, load(1))
    }

    @Test
    fun `one and two column settings never overwrite one another`() {
        val one = AppSearchAppearance(tileColor = 0xff112233.toInt(), spacingDp = 0)
        val two = AppSearchAppearance(
            tileColor = 0xffaabbcc.toInt(), applyTileColorToMenu = true,
            backgroundTransparency = 100, alignment = AppSearchAppearance.Alignment.BOTTOM, spacingDp = 32,
        )
        save(1, one)
        save(2, two)
        save(1, one.copy(popupColor = 0xff445566.toInt()))
        assertEquals(two, load(2))
        assertEquals(one.copy(popupColor = 0xff445566.toInt()), load(1))
    }

    @Test
    fun `editing appearance never writes the actual search tile count or other page settings`() {
        saved["SEARCH_TILE_COUNT_APPS_ONLY"] = 2
        saved["SEARCH_TILE_COUNT_CONTACTS_ONLY"] = 1
        save(1, AppSearchAppearance(spacingDp = 16))
        save(2, AppSearchAppearance(backgroundTransparency = 50))
        assertEquals(2, saved["SEARCH_TILE_COUNT_APPS_ONLY"])
        assertEquals(1, saved["SEARCH_TILE_COUNT_CONTACTS_ONLY"])
        assertTrue(saved.keys.all {
            it.startsWith("APP_SEARCH_APPEARANCE_") || it.startsWith("SEARCH_TILE_COUNT_")
        })
    }

    @Test
    fun `a fresh reader restores both layouts from the persisted values`() {
        val first = AppSearchAppearance(backgroundColor = 0xff778899.toInt(), backgroundTransparency = 18)
        val second = AppSearchAppearance(popupColor = 0xff111111.toInt(), spacingDp = 30)
        save(1, first)
        save(2, second)
        val reopened = saved.toMap()
        assertEquals(first, AppSearchAppearance.load(1, reopened::get))
        assertEquals(second, AppSearchAppearance.load(2, reopened::get))
    }

    @Test
    fun `menu tile opt in is independent from popup background and from other layout`() {
        val appearance = AppSearchAppearance(tileColor = 0xffaabbcc.toInt(), popupColor = 0xff112233.toInt())
        save(1, appearance.copy(applyTileColorToMenu = true))
        save(2, appearance)
        assertTrue(load(1).applyTileColorToMenu)
        assertFalse(load(2).applyTileColorToMenu)
        assertEquals(appearance.popupColor, load(1).popupColor)
    }

    @Test
    fun `transparency endpoints are fully opaque and fully transparent without changing RGB`() {
        val appearance = AppSearchAppearance(backgroundColor = 0xff123456.toInt())
        assertEquals(0xff123456.toInt(), appearance.copy(backgroundTransparency = 0).background(0x40111111))
        assertEquals(0x00123456, appearance.copy(backgroundTransparency = 100).background(0x40111111))
        assertEquals(0x80123456.toInt(), appearance.copy(backgroundTransparency = 50).background(0x40111111))
    }

    @Test
    fun `choosing a background color alone preserves themed transparency`() {
        val appearance = AppSearchAppearance(backgroundColor = 0xff123456.toInt())
        assertEquals(0x40123456, appearance.background(0x40ffffff))
    }

    @Test
    fun `transparency can be customized without choosing a background color`() {
        val appearance = AppSearchAppearance(backgroundTransparency = 0)
        assertEquals(0xffabcdef.toInt(), appearance.background(0x40abcdef))
    }

    @Test
    fun `each alignment round trips independently`() {
        AppSearchAppearance.Alignment.entries.forEach { alignment ->
            val appearance = AppSearchAppearance(alignment = alignment)
            save(2, appearance)
            assertEquals(alignment, load(2).alignment)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `spacing cannot overlap tiles through a negative margin`() {
        AppSearchAppearance(spacingDp = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transparency cannot exceed 100 percent`() {
        AppSearchAppearance(backgroundTransparency = 101)
    }
}
