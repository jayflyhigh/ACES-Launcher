package app.olauncher.data

import org.junit.Assert.*
import org.junit.Test

class ContactCommandTest {
    @Test
    fun `all commands accept either case and remove extra spaces`() {
        ContactCommand.entries.forEach { command ->
            for (letter in listOf(command.letter, command.letter.uppercaseChar())) {
                assertEquals(
                    ContactCommandQuery(command, "Jöhn Smith"),
                    ContactCommandQuery.parse("$letter   Jöhn Smith  "),
                )
                assertEquals(ContactCommandQuery(command, ""), ContactCommandQuery.parse("$letter "))
            }
        }
    }

    @Test
    fun `normal names and invalid prefixes never activate commands`() {
        listOf("", "p", "Paul", "x john", " phone john", " p john", "p\tjohn", "pp john")
            .forEach { assertNull(it, ContactCommandQuery.parse(it)) }
    }

    @Test
    fun `automatic execution requires all safeguards`() {
        val session = ContactCommandSession("p john")
        val settings = ContactCommandSettings()
        assertTrue(session.automaticCandidate(settings, 1, false, true, false))
        assertFalse(session.automaticCandidate(settings.copy(automatic = false), 1, false, true, false))
        assertFalse(session.automaticCandidate(settings, 2, false, true, false))
        assertFalse(session.automaticCandidate(settings, 0, false, true, false))
        assertFalse(session.automaticCandidate(settings, 1, true, true, false))
        assertFalse(session.automaticCandidate(settings, 1, false, false, false))
        assertFalse(session.automaticCandidate(settings, 1, false, true, true))
        session.update("p j")
        assertFalse(session.automaticCandidate(settings, 1, false, true, false))
        session.update("p j   ")
        assertFalse(session.automaticCandidate(settings, 1, false, true, false))
        session.update("p jo")
        assertTrue(session.automaticCandidate(settings, 1, false, true, false))
        assertFalse(session.automaticCandidate(settings.copy(minimumCharacters = 3), 1, false, true, false))
        session.update("p john")
        assertTrue(session.automaticCandidate(settings.copy(minimumCharacters = 4), 1, false, true, false))
    }

    @Test
    fun `claim is once per event and survives refresh and restoration`() {
        val session = ContactCommandSession("m john")
        assertFalse(session.claim("m jane"))
        assertTrue(session.claim("m john"))
        session.update("m john")
        assertFalse(session.claim("m john"))
        val restored = ContactCommandSession(session.query, session.consumed)
        assertFalse(restored.claim("m john"))
        assertFalse(restored.automaticCandidate(ContactCommandSettings(), 1, false, true, false))
        restored.update("m jane")
        assertTrue(restored.claim("m jane"))
    }

    @Test
    fun `number selection honors explicit memory then single or android preferred number`() {
        val mobile = ContactPhoneNumber("+1 555 1000", "+15551000", "Mobile", false)
        val work = ContactPhoneNumber("+1 555 2000", "+15552000", "Work", true)
        assertEquals(mobile, preferredContactNumber(listOf(mobile), null))
        assertEquals(work, preferredContactNumber(listOf(mobile, work), null))
        assertEquals(mobile, preferredContactNumber(listOf(mobile, work), mobile.identity))
        assertEquals(work, preferredContactNumber(listOf(mobile, work), "deleted"))
        assertNull(preferredContactNumber(listOf(mobile, work.copy(preferred = false)), "deleted"))
        assertNull(preferredContactNumber(emptyList(), mobile.identity))
    }
}
