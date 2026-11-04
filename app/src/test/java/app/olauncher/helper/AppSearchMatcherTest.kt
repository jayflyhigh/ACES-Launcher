package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchMatcherTest {
    @Test
    fun `one matcher can be reused across a whole result set`() {
        val labels = listOf("Camera", "Café", "Google Maps", "Google-Maps", "Sam's App", "")
        val queries = listOf("cam", "CAMERA", "  cam  ", "cafe", "café", "googlemaps", "sams app", "", "   ", "---")
        for (query in queries) {
            val matcher = AppSearchMatcher(query)
            for (label in labels) {
                assertEquals(appLabelMatches(label, query), matcher.matches(label))
            }
        }
    }

    @Test
    fun `matcher snapshots mutable query text`() {
        val query = StringBuilder("cafe")
        val matcher = AppSearchMatcher(query)
        query.clear().append("camera")
        assertTrue(matcher.matches("Café"))
        assertFalse(matcher.matches("Camera"))
    }

    @Test
    fun `exact case-insensitive substring matches`() {
        assertTrue(appLabelMatches("Camera", "cam"))
        assertTrue(appLabelMatches("Camera", "CAMERA"))
    }

    @Test
    fun `no match when query is not a substring at all`() {
        assertFalse(appLabelMatches("Camera", "xyz"))
    }

    @Test
    fun `query is trimmed before the plain substring check`() {
        assertTrue(appLabelMatches("Camera", "  cam  "))
    }

    @Test
    fun `diacritics are ignored — cafe matches Café`() {
        assertTrue(appLabelMatches("Café", "cafe"))
    }

    @Test
    fun `diacritics in the query are also normalised`() {
        assertTrue(appLabelMatches("Cafe", "café"))
    }

    @Test
    fun `separator characters are ignored on both sides`() {
        assertTrue(appLabelMatches("Google Maps", "googlemaps"))
        assertTrue(appLabelMatches("Google-Maps", "google maps"))
        assertTrue(appLabelMatches("Sam's App", "sams app"))
    }

    @Test
    fun `blank query matches everything, via plain substring semantics`() {
        // String.contains("") is always true — this mirrors the pre-existing behaviour
        // exactly (not a new edge case introduced by extracting this function). The
        // production caller (AppDrawerAdapter) never reaches this path with a blank
        // query: it shows the unfiltered list instead via its own isNullOrBlank() guard.
        assertTrue(appLabelMatches("Camera", ""))
        assertTrue(appLabelMatches("Camera", "   "))
    }

    @Test
    fun `normalized match still requires real content, not just separators`() {
        // A query of only separator characters normalizes to an empty string,
        // which must not vacuously match every label via the fallback path.
        assertFalse(appLabelMatches("Camera", "---"))
    }
}
