package app.olauncher.data

import org.junit.Assert.*
import org.junit.Test

class AppCommandTest {
    @Test
    fun `commands require a space and accept either case`() {
        for (command in AppCommand.entries) {
            for (letter in listOf(command.letter, command.letter.uppercaseChar())) {
                assertEquals(AppCommandQuery(command, "Maps"), AppCommandQuery.parse("$letter  Maps "))
                assertNull(AppCommandQuery.parse("${letter}Maps"))
            }
        }
        assertNull(AppCommandQuery.parse("x Maps"))
        assertNull(AppCommandQuery.parse(" r Maps"))
    }

    @Test
    fun `automatic actions require one ready match and explicit query text`() {
        val session = AppCommandSession("r maps")
        assertTrue(session.automaticCandidate(1, true, true, false))
        assertFalse(session.automaticCandidate(2, true, true, false))
        assertFalse(session.automaticCandidate(0, true, true, false))
        assertFalse(session.automaticCandidate(1, false, true, false))
        assertFalse(session.automaticCandidate(1, true, false, false))
        assertFalse(session.automaticCandidate(1, true, true, true))
        session.update("r ")
        assertFalse(session.automaticCandidate(1, true, true, false))
    }

    @Test
    fun `actions execute at most once until the query changes`() {
        val session = AppCommandSession("i maps")
        assertFalse(session.claim("i other"))
        assertTrue(session.claim("i maps"))
        assertFalse(session.claim("i maps"))
        session.update("i maps")
        assertTrue(session.consumed)
        session.update("p maps")
        assertFalse(session.consumed)
        assertTrue(session.claim("p maps"))
        val restored = AppCommandSession(session.query, session.consumed)
        assertFalse(restored.claim("p maps"))
    }
}