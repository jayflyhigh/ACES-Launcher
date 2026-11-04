package app.olauncher.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchLayoutTest {
    @Test
    fun `short results align to the bottom of their available area`() {
        assertEquals(300, bottomAlignmentOffset(500, 200))
    }

    @Test
    fun `results that fill or exceed the area retain normal scrolling positions`() {
        assertEquals(0, bottomAlignmentOffset(500, 500))
        assertEquals(0, bottomAlignmentOffset(500, 800))
    }

    @Test
    fun `keyboard reducing the results area never creates a negative offset`() {
        assertEquals(0, bottomAlignmentOffset(100, 200))
    }
}
