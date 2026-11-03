package app.olauncher.data

enum class SearchMode(
    val showsApps: Boolean,
    val showsContacts: Boolean,
    val showsEngines: Boolean,
) {
    APPS_ONLY(true, false, false),
    CONTACTS_ONLY(false, true, false),
    ENGINES_ONLY(false, false, true),
    ALL_SEARCH(true, true, true);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: ALL_SEARCH
    }
}
