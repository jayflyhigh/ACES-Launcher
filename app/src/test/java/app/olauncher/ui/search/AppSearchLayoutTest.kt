package app.olauncher.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchLayoutTest {
    @Test
    fun `padding is unchanged when enough content space remains`() {
        assertEquals(12 to 24, fitResultPadding(12, 24, 400, 192))
    }

    @Test
    fun `padding scales proportionally to keep two columns usable on narrow screens`() {
        assertEquals(24 to 24, fitResultPadding(64, 64, 240, 192))
        assertEquals(16 to 32, fitResultPadding(32, 64, 240, 192))
    }

    @Test
    fun `keyboard and very small preview areas never produce negative content dimensions`() {
        assertEquals(0 to 0, fitResultPadding(64, 64, 30, 48))
        assertEquals(0 to 0, fitResultPadding(0, 0, 0, 192))
    }
}
