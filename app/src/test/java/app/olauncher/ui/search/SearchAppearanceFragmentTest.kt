package app.olauncher.ui.search

import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppSearchAppearance
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.SearchMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class SearchAppearanceFragmentTest {
    @Test
    fun `each page renders both layouts and survives recreation`() {
        for (mode in SearchMode.entries) {
            val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
            try {
                val activity = controller.get()
                val fragment = SearchAppearanceFragment().apply {
                    arguments = Bundle().apply { putString(Constants.Key.SEARCH_MODE, mode.name) }
                }
                activity.supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment).commitNow()
                val root = fragment.requireView()
                for (columns in 1..2) {
                    root.findViewById<View>(if (columns == 1) R.id.decreaseLayout else R.id.increaseLayout).performClick()
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, 1080, 1920)
                    shadowOf(Looper.getMainLooper()).idle()
                    val results = root.findViewById<RecyclerView>(R.id.previewResults)
                    assertEquals(if (mode == SearchMode.ALL_SEARCH) 9 else 2, results.adapter!!.itemCount)
                    assertTrue("Preview rows must actually render", results.childCount > 0)
                }
                controller.recreate()
                shadowOf(Looper.getMainLooper()).idle()
                val restored = controller.get().supportFragmentManager.findFragmentById(android.R.id.content)!!
                assertEquals(mode.name, restored.requireArguments().getString(Constants.Key.SEARCH_MODE))
                assertEquals(2, (restored.requireView().findViewById<RecyclerView>(R.id.previewResults)
                    .layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanCount)
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test
    fun `app settings edits redesigned tiles and persists without changing another page`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = AppSearchSettingsFragment()
            activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commitNow()
            val root = fragment.requireView()
            root.findViewById<View>(R.id.tileColor).performClick()
            root.findViewById<View>(R.id.tileColorPicker).findViewById<EditText>(R.id.hex).setText("#336699")
            assertEquals(Color.rgb(51, 102, 153), Prefs(activity).searchAppearance(SearchMode.APPS_ONLY, 1).tileColor)
            assertNull(Prefs(activity).searchAppearance(SearchMode.CONTACTS_ONLY, 1).tileColor)
            controller.recreate()
            assertEquals(Color.rgb(51, 102, 153), Prefs(controller.get()).searchAppearance(SearchMode.APPS_ONLY, 2).tileColor)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `legacy appearance is retained and page edits are independent with separate layout spacing`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = Prefs(context)
        val one = AppSearchAppearance(tileColor = Color.RED, spacingDp = 12, paddingLeftDp = 24)
        val two = one.copy(spacingDp = 20, paddingLeftDp = 8)
        prefs.setAppSearchAppearance(1, one)
        prefs.setAppSearchAppearance(2, two)
        SearchMode.entries.forEach {
            assertEquals(one, prefs.searchAppearance(it, 1))
            assertEquals(two, prefs.searchAppearance(it, 2))
        }
        prefs.setSearchAppearance(SearchMode.CONTACTS_ONLY, 1, one.copy(tileColor = Color.BLUE, spacingDp = 4))
        val restored = Prefs(context)
        assertEquals(one.copy(tileColor = Color.BLUE, spacingDp = 4), restored.searchAppearance(SearchMode.CONTACTS_ONLY, 1))
        assertEquals(two.copy(tileColor = Color.BLUE), restored.searchAppearance(SearchMode.CONTACTS_ONLY, 2))
        assertEquals(one, restored.searchAppearance(SearchMode.APPS_ONLY, 1))
        assertEquals(two, restored.searchAppearance(SearchMode.ENGINES_ONLY, 2))
        assertEquals(one, restored.appSearchAppearance(1))
    }
}
