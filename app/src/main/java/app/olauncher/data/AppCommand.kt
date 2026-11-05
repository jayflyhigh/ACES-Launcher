package app.olauncher.data

enum class AppCommand(val code: String) {
    RENAME("r"), INFO("i"), PLAY_STORE("p"), UNINSTALL("u"), MASS_UNINSTALL("mu");

    val letter: Char get() = code.first()
}

data class AppCommandQuery(val command: AppCommand, val name: String) {
    companion object {
        fun parse(query: String): AppCommandQuery? {
            val command = AppCommand.entries.sortedByDescending { it.code.length }
                .firstOrNull { query.startsWith("${it.code} ", ignoreCase = true) } ?: return null
            return AppCommandQuery(command, query.substring(command.code.length + 1).trim())
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
        !consumed && AppCommandQuery.parse(query)?.command != AppCommand.MASS_UNINSTALL &&
            matches == 1 && ready && resumed && !composing &&
            AppCommandQuery.parse(query)?.name?.isNotBlank() == true
}