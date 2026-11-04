package app.olauncher.data

enum class ContactCommand(val letter: Char) {
    PHONE('p'), MESSAGE('m'), WHATSAPP('w'), CONTACT('c');
}

data class ContactCommandQuery(val command: ContactCommand, val name: String) {
    companion object {
        fun parse(query: String): ContactCommandQuery? {
            if (query.length < 2 || query[1] != ' ') return null
            val command = ContactCommand.entries.firstOrNull { it.letter == query[0].lowercaseChar() }
                ?: return null
            return ContactCommandQuery(command, query.substring(2).trim())
        }
    }
}

data class ContactCommandSettings(
    val automatic: Boolean = true,
    val rememberNumbers: Boolean = true,
    val delayMillis: Int = 250,
    val minimumCharacters: Int = 2,
    val showIndicator: Boolean = true,
)

/** One search event lasts until the text changes, not until results or views refresh. */
class ContactCommandSession(var query: String = "", var consumed: Boolean = false) {
    fun update(text: String) {
        if (query != text) {
            query = text
            consumed = false
        }
    }

    fun claim(text: String): Boolean {
        if (text != query || consumed || ContactCommandQuery.parse(text) == null) return false
        consumed = true
        return true
    }

    fun automaticCandidate(
        settings: ContactCommandSettings,
        matches: Int,
        loading: Boolean,
        resumed: Boolean,
        composing: Boolean,
    ): Boolean {
        val parsed = ContactCommandQuery.parse(query) ?: return false
        val characters = parsed.name.codePoints().filter { !Character.isWhitespace(it) }.count()
        return !consumed && settings.automatic && matches == 1 && !loading && resumed &&
            !composing && characters >= settings.minimumCharacters
    }
}

data class ContactPhoneNumber(
    val number: String,
    val normalizedNumber: String?,
    val label: String,
    val preferred: Boolean,
) {
    val identity: String
        get() = (normalizedNumber?.takeIf { it.isNotBlank() } ?: number).filter { it.isDigit() || it == '+' }
}

fun preferredContactNumber(numbers: List<ContactPhoneNumber>, remembered: String?): ContactPhoneNumber? =
    numbers.firstOrNull { it.identity == remembered }
        ?: numbers.singleOrNull()
        ?: numbers.singleOrNull { it.preferred }
