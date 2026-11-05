package app.olauncher.data

enum class AppCommand(val letter: Char) {
    RENAME('r'), INFO('i'), PLAY_STORE('p'), UNINSTALL('u');
}

data class AppCommandQuery(val command: AppCommand, val name: String) {
    companion object {
        fun parse(query: String): AppCommandQuery? {
            if (query.length < 2 || query[1] != ' ') return null
            val command = AppCommand.entries.firstOrNull { it.letter == query[0].lowercaseChar() } ?: return null
            return AppCommandQuery(command, query.substring(2).trim())
        }
    }
}

class AppCommandSession(var query: String = "", var consumed: Boolean = false) {
    fun update(text: String) {
        if (query != text) {
            query = text
            consumed = false
        }
    }

    fun claim(text: String): Boolean {
        if (query != text || consumed || AppCommandQuery.parse(text) == null) return false
        consumed = true
        return true
    }

    fun automaticCandidate(matches: Int, ready: Boolean, resumed: Boolean, composing: Boolean): Boolean =
        !consumed && matches == 1 && ready && resumed && !composing &&
            AppCommandQuery.parse(query)?.name?.isNotBlank() == true
}