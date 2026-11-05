package app.olauncher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.olauncher.R
import app.olauncher.helper.showToast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

data class SearchEngine(
    val id: String,
    val name: String,
    val url: String,
    val alias: String,
) {
    fun buildUri(query: String): Uri {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val target = if (url.contains(QUERY_PLACEHOLDER)) url.replace(QUERY_PLACEHOLDER, encoded) else url + encoded
        return Uri.parse(target)
    }

    companion object {
        const val QUERY_PLACEHOLDER = "%s"
    }
}

/** One shared, ordered search engine configuration used by every search mode. */
object SearchEngines {

    private val ALIAS_REGEX = Regex("^[A-Za-z0-9]{1,12}$")
    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "intent")

    private var cache: List<SearchEngine>? = null
    private val _engines = MutableLiveData<List<SearchEngine>>()
    val engines: LiveData<List<SearchEngine>> get() = _engines

    fun get(context: Context): List<SearchEngine> {
        cache?.let { return it }
        val prefs = Prefs(context)
        val loaded = prefs.searchEnginesJson?.let { parse(it) } ?: defaults().also { prefs.searchEnginesJson = serialize(it) }
        cache = loaded
        _engines.value = loaded
        return loaded
    }

    fun save(context: Context, list: List<SearchEngine>) {
        val copy = list.toList()
        Prefs(context).searchEnginesJson = serialize(copy)
        cache = copy
        _engines.value = copy
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Returns the engine whose alias is the first word, and the remaining query, for input like "g weather". */
    fun parseAliasQuery(engines: List<SearchEngine>, text: CharSequence): Pair<SearchEngine, String>? {
        val trimmed = text.trimStart()
        val spaceIndex = trimmed.indexOf(' ')
        if (spaceIndex <= 0) return null
        val alias = trimmed.substring(0, spaceIndex)
        val engine = engines.firstOrNull { it.alias.equals(alias, ignoreCase = true) } ?: return null
        return engine to trimmed.substring(spaceIndex + 1).trim()
    }

    fun isValidAlias(alias: String) = ALIAS_REGEX.matches(alias)

    fun isAliasTaken(engines: List<SearchEngine>, alias: String, exceptId: String?) =
        engines.any { it.id != exceptId && it.alias.equals(alias, ignoreCase = true) }

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank() || url.any { it.isWhitespace() }) return false
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in BLOCKED_SCHEMES) return false
        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) return false
        return true
    }

    /** Opens [engine] for [query]; returns false if nothing could handle it. */
    fun open(context: Context, engine: SearchEngine, query: String): Boolean {
        if (!isValidUrl(engine.url)) {
            context.showToast(R.string.search_destination_unavailable)
            return false
        }
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, engine.buildUri(query)))
            true
        } catch (_: ActivityNotFoundException) {
            context.showToast(R.string.search_destination_unavailable)
            false
        } catch (_: SecurityException) {
            context.showToast(R.string.search_destination_unavailable)
            false
        }
    }

    private fun defaults() = listOf(
        SearchEngine(newId(), "Google", "https://www.google.com/search?q=%s", "g"),
        SearchEngine(newId(), "DuckDuckGo", "https://duckduckgo.com/?q=%s", "d"),
        SearchEngine(newId(), "YouTube", "https://www.youtube.com/results?search_query=%s", "yt"),
        SearchEngine(newId(), "Reddit", "https://www.reddit.com/search/?q=%s", "r"),
        SearchEngine(newId(), "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s", "w"),
        SearchEngine(newId(), "Google Maps", "https://www.google.com/maps/search/?api=1&query=%s", "m"),
    )

    data class SearchEngineCommandQuery(val name: String) {
        companion object {
            fun parse(query: String): SearchEngineCommandQuery? =
                if (query.length >= 2 && query[1] == ' ' && query[0].lowercaseChar() == 'e')
                    SearchEngineCommandQuery(query.substring(2).trim())
                else null
        }
    }

    private fun serialize(list: List<SearchEngine>): String {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("alias", it.alias)
            )
        }
        return array.toString()
    }

    private fun parse(json: String): List<SearchEngine> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            SearchEngine(
                id = o.optString("id").ifBlank { newId() },
                name = o.optString("name"),
                url = o.optString("url"),
                alias = o.optString("alias"),
            ).takeIf { it.name.isNotBlank() && it.url.isNotBlank() }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
