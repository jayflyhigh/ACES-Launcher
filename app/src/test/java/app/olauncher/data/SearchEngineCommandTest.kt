package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchEngineCommandTest {
    @Test
    fun `edit command requires e followed by a space and retains the engine query`() {
        assertEquals(SearchEngines.SearchEngineCommandQuery("Google"), SearchEngines.SearchEngineCommandQuery.parse("e Google"))
        assertEquals(SearchEngines.SearchEngineCommandQuery("Google"), SearchEngines.SearchEngineCommandQuery.parse("E  Google "))
        assertEquals(SearchEngines.SearchEngineCommandQuery(""), SearchEngines.SearchEngineCommandQuery.parse("e "))
        assertNull(SearchEngines.SearchEngineCommandQuery.parse("engine"))
        assertNull(SearchEngines.SearchEngineCommandQuery.parse("eGoogle"))
        assertNull(SearchEngines.SearchEngineCommandQuery.parse("r Google"))
    }
}