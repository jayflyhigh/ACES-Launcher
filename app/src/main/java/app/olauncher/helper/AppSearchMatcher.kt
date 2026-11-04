package app.olauncher.helper

import java.text.Normalizer

private val diacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")
private val separatorsRegex = Regex("[-_+,.`'\\s\\p{Z}]")

/**
 * True if [appLabel] matches [query] for the app-drawer search: either a plain
 * case-insensitive substring match, or a match once both sides are normalised
 * (diacritics stripped, separator characters removed) — e.g. "cafe" matches
 * "Café", and "googlemaps" matches "Google Maps".
 */
fun appLabelMatches(appLabel: String, query: CharSequence): Boolean =
    AppSearchMatcher(query).matches(appLabel)

/** Reuse for all labels in one filtering pass so the query is normalized only once. */
class AppSearchMatcher(query: CharSequence) {
    private val query = query.toString()
    private val trimmedQuery = this.query.trim()
    private val normalizedQuery by lazy(LazyThreadSafetyMode.NONE) { this.query.normalizeForSearch() }

    fun matches(appLabel: String): Boolean {
        if (appLabel.contains(trimmedQuery, true)) return true
        return normalizedQuery.isNotEmpty() &&
            appLabel.normalizeForSearch().contains(normalizedQuery, true)
    }
}

private fun CharSequence.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(diacriticsRegex, "")
        .replace(separatorsRegex, "")
