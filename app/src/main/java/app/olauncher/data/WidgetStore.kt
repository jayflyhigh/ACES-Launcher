package app.olauncher.data

import org.json.JSONArray
import org.json.JSONObject

/** Widget bounds are stored as fractions of the widget area so they survive screen size changes. */
data class HomeWidget(
    val id: Int,
    var x: Float = -1f,
    var y: Float = -1f,
    var w: Float = -1f,
    var h: Float = -1f,
) {
    val isPlaced get() = w > 0f && h > 0f
}

object WidgetStore {

    fun load(prefs: Prefs): MutableList<HomeWidget> = try {
        val array = JSONArray(prefs.homeWidgetsJson)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            HomeWidget(
                id = o.getInt("id"),
                x = o.optDouble("x", -1.0).toFloat(),
                y = o.optDouble("y", -1.0).toFloat(),
                w = o.optDouble("w", -1.0).toFloat(),
                h = o.optDouble("h", -1.0).toFloat(),
            )
        }.toMutableList()
    } catch (_: Exception) {
        mutableListOf()
    }

    fun save(prefs: Prefs, widgets: List<HomeWidget>) {
        val array = JSONArray()
        widgets.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("x", it.x.toDouble())
                    .put("y", it.y.toDouble())
                    .put("w", it.w.toDouble())
                    .put("h", it.h.toDouble())
            )
        }
        prefs.homeWidgetsJson = array.toString()
    }

    fun add(prefs: Prefs, id: Int) {
        val list = load(prefs)
        if (list.none { it.id == id }) list.add(HomeWidget(id))
        save(prefs, list)
    }

    fun remove(prefs: Prefs, id: Int) = save(prefs, load(prefs).filterNot { it.id == id })

    fun update(prefs: Prefs, widget: HomeWidget) {
        val list = load(prefs)
        val index = list.indexOfFirst { it.id == widget.id }
        if (index >= 0) list[index] = widget else list.add(widget)
        save(prefs, list)
    }
}
