package app.olauncher.ui.search

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.view.View
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class SearchRedesignTest {
    private val app = AppModel.App("Chrome", null, "com.android.chrome", null, user = Process.myUserHandle())

    private fun withPage(mode: SearchMode, columns: Int = 1,
        action: (ContactCommandTestActivity, UniversalSearchFragment, MainViewModel) -> Unit,
    ) {
        CommandContactsProvider().install()
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            Prefs(activity).apply {
                setSearchTileCount(mode, columns)
                setContactCommandSettings(ContactCommandSettings(automatic = false))
            }
            SearchEngines.save(activity, listOf(SearchEngine("test", "Google", "https://google.com/search?q=%s", "g")))
            val vm = ViewModelProvider(activity)[MainViewModel::class.java]
            vm.appList.value = listOf(app)
            val fragment = UniversalSearchFragment().apply {
                arguments = Bundle().apply { putString(Constants.Key.SEARCH_MODE, mode.name) }
            }
            activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commitNow()
            awaitCommand { vm.contactsLoading.value == false }
            val expected = (if (mode.showsApps) 1 else 0) + (if (mode.showsContacts) 2 else 0) + (if (mode.showsEngines) 1 else 0)
            awaitCommand { count(fragment) == "$expected results" || (expected == 1 && count(fragment) == "1 result") }
            layout(fragment)
            action(activity, fragment, vm)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun count(fragment: UniversalSearchFragment) =
        fragment.requireView().findViewById<TextView>(R.id.resultsCount).text.toString()

    private fun layout(fragment: UniversalSearchFragment, width: Int = 400, height: Int = 800, idle: Boolean = true) {
        val density = fragment.resources.displayMetrics.density
        fragment.requireView().apply {
            val w = (width * density).toInt()
            val h = (height * density).toInt()
            measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            layout(0, 0, w, h)
        }
        if (idle) shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `all pages have one pill field fixed heading counter and consistently labelled tiles in both layouts`() {
        val titles = listOf("Apps", "Contacts", "Search Engines", "Search Anything")
        for ((index, mode) in SearchMode.entries.withIndex()) for (columns in 1..2) {
            withPage(mode, columns) { _, fragment, _ ->
                val root = fragment.requireView()
                assertEquals(
                    androidx.core.content.ContextCompat.getColor(root.context, R.color.search_background),
                    (root.background as ColorDrawable).color,
                )
                assertEquals(titles[index], root.findViewById<TextView>(R.id.modeTitle).text.toString())
                assertEquals("", root.findViewById<SearchView>(R.id.search).queryHint.toString())
                val editor = root.findViewById<TextView>(R.id.search_src_text)
                assertEquals(
                    android.view.Gravity.CENTER_VERTICAL,
                    editor.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK,
                )
                val list = root.findViewById<RecyclerView>(R.id.recyclerView)
                assertEquals(columns, (list.layoutManager as GridLayoutManager).spanCount)
                val adapter = list.adapter as SearchResultsAdapter
                for ((position, item) in adapter.currentList.withIndex()) {
                    val row = list.findViewHolderForAdapterPosition(position)?.itemView ?: continue
                    if (item is SearchItem.Header) continue
                    assertEquals(
                        if (item is SearchItem.ContactItem) View.GONE else View.VISIBLE,
                        row.findViewById<View>(R.id.itemMenu).visibility,
                    )
                    assertEquals(
                        if (item is SearchItem.ContactItem && columns == 1) View.VISIBLE else View.GONE,
                        row.findViewById<View>(R.id.contactActions).visibility,
                    )
                    val label = when (item) {
                        is SearchItem.App -> "App"
                        is SearchItem.Engine -> "Search Engine"
                        is SearchItem.ContactItem -> null
                        else -> error("Unexpected row")
                    }
                    val itemType = row.findViewById<TextView>(R.id.itemType)
                    if (label == null) assertEquals(View.GONE, itemType.visibility)
                    else assertEquals(label, itemType.text.toString())
                    assertTrue(row.findViewById<View>(R.id.itemTitle).width > 0)
                    assertTrue(row.findViewById<View>(R.id.itemIcon).width > 0)
                }
            }
        }
    }

    @Test
    fun `contact counts and empty state update and loading does not show a false empty state`() {
        withPage(SearchMode.CONTACTS_ONLY) { _, fragment, vm ->
            val root = fragment.requireView()
            val search = root.findViewById<SearchView>(R.id.search)
            search.setQuery("john", false)
            awaitCommand { count(fragment) == "1 result" }
            search.setQuery("not present", false)
            awaitCommand { count(fragment) == "0 results" }
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.emptyState).visibility)
            assertEquals("No contacts found", root.findViewById<TextView>(R.id.emptyMessage).text.toString())
            vm.contactsLoading.value = true
            awaitCommand { root.findViewById<View>(R.id.emptyState).visibility == View.GONE }
            vm.contacts.value = listOf(Contact(3, "new", "Not Present", null, null))
            vm.contactsLoading.value = false
            awaitCommand { count(fragment) == "1 result" }
            assertEquals(View.GONE, root.findViewById<View>(R.id.emptyState).visibility)
        }
    }

    @Test
    fun `all search count excludes headings and permission rows and observes engine and app changes`() {
        withPage(SearchMode.ALL_SEARCH) { activity, fragment, vm ->
            assertEquals("4 results", count(fragment))
            vm.appList.value = listOf(app, app.copy(appLabel = "Calendar", appPackage = "calendar"))
            awaitCommand { count(fragment) == "5 results" }
            SearchEngines.save(activity, emptyList())
            awaitCommand { count(fragment) == "4 results" }
            shadowOf(activity.application).denyPermissions(android.Manifest.permission.READ_CONTACTS)
            fragment.requireView().findViewById<SearchView>(R.id.search).setQuery(" ", false)
            awaitCommand { count(fragment) == "2 results" }
        }
    }

    @Test
    fun `heading stays fixed while results scroll and keyboard insets change`() {
        withPage(SearchMode.CONTACTS_ONLY) { _, fragment, vm ->
            vm.contacts.value = (1..100).map { Contact(it.toLong(), "$it", "Person $it", null, null) }
            awaitCommand { count(fragment) == "100 results" }
            layout(fragment)
            val root = fragment.requireView()
            val heading = root.findViewById<View>(R.id.searchHeading)
            val top = heading.top
            val list = root.findViewById<RecyclerView>(R.id.recyclerView)
            list.scrollBy(0, 1000)
            layout(fragment)
            assertEquals(top, heading.top)
            val inset = (250 * fragment.resources.displayMetrics.density).toInt()
            val keyboard = WindowInsetsCompat.Builder()
                .setSystemWindowInsets(Insets.of(0, 0, 0, inset))
                .setStableInsets(Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, inset)).build()
            assertEquals(inset, keyboard.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            // Pre-30 platform WindowInsets cannot carry synthetic IME type overrides.
            val listener = root.getTag(androidx.core.R.id.tag_on_apply_window_listener) as
                androidx.core.view.OnApplyWindowInsetsListener
            listener.onApplyWindowInsets(root, keyboard)
            assertEquals(inset, root.paddingBottom)
            layout(fragment, idle = false)
            assertEquals(top, heading.top)
            assertEquals(inset, root.paddingBottom)
        }
    }

    @Test
    fun `result menu does not launch contact is scrollable and dismisses on Back without scrolling`() {
        withPage(SearchMode.CONTACTS_ONLY) { activity, fragment, _ ->
            val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            val tile = list.getChildAt(0)
            tile.findViewById<View>(R.id.itemMenu).performClick()
            val field = UniversalSearchFragment::class.java.getDeclaredField("activePopupWindow").apply { isAccessible = true }
            val popup = field.get(fragment) as PopupWindow
            assertTrue(popup.isShowing)
            assertTrue(popup.contentView is ScrollView)
            assertNull(shadowOf(activity).peekNextStartedActivity())
            activity.onBackPressedDispatcher.onBackPressed()
            assertFalse(popup.isShowing)
            assertEquals(0, (list.layoutManager as GridLayoutManager).findFirstVisibleItemPosition())
        }
    }

    @Test
    fun `search page popup settings remain on engines and page appearance is hidden`() {
        for (mode in SearchMode.entries) withPage(mode) { _, fragment, _ ->
            fragment.requireView().findViewById<View>(R.id.searchPageMenu).performClick()
            val field = UniversalSearchFragment::class.java.getDeclaredField("activePopupWindow").apply {
                isAccessible = true
            }
            val popup = field.get(fragment) as PopupWindow
            val menu = popup.contentView
            val settingsVisibility = menu.findViewById<View>(R.id.searchPageSettings).visibility
            val appearanceVisibility = menu.findViewById<View>(R.id.searchPageAppearance).visibility
            assertEquals(
                if (mode == SearchMode.ENGINES_ONLY) View.VISIBLE else View.GONE,
                settingsVisibility,
            )
            assertEquals(
                View.GONE,
                appearanceVisibility,
            )
            popup.dismiss()
        }
    }

    @Test
    fun `popup opens down or up within keyboard safe bounds and caps oversized content`() {
        val visible = Rect(0, 24, 400, 500)
        assertEquals(Rect(100, 100, 356, 300), searchPopupBounds(visible, 308, 52, 48, 48, 256, 200))
        assertEquals(Rect(100, 230, 356, 430), searchPopupBounds(visible, 308, 430, 48, 48, 256, 200))
        val tall = searchPopupBounds(visible, 390, 430, 48, 48, 600, 1000)
        assertTrue(visible.contains(tall))
        assertEquals(400, tall.width())
        assertEquals(406, tall.height())
    }

    @Test
    fun `app and engine empty states remove themselves as data arrives`() {
        withPage(SearchMode.APPS_ONLY) { _, fragment, vm ->
            vm.appList.value = emptyList()
            awaitCommand { count(fragment) == "0 results" }
            assertEquals("No apps found", fragment.requireView().findViewById<TextView>(R.id.emptyMessage).text.toString())
            assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.emptyState).visibility)
            vm.appList.value = listOf(app)
            awaitCommand { count(fragment) == "1 result" }
            assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.emptyState).visibility)
        }
        withPage(SearchMode.ENGINES_ONLY) { activity, fragment, _ ->
            SearchEngines.save(activity, emptyList())
            awaitCommand { count(fragment) == "0 results" }
            assertEquals("No search engines found", fragment.requireView().findViewById<TextView>(R.id.emptyMessage).text.toString())
            assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.emptyState).visibility)
        }
    }

    @Test
    fun `contact action menu dispatches the selected action instead of opening details`() {
        withPage(SearchMode.CONTACTS_ONLY) { activity, fragment, _ ->
            val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            list.getChildAt(0).findViewById<View>(R.id.itemMenu).performClick()
            val field = UniversalSearchFragment::class.java.getDeclaredField("activePopupWindow").apply { isAccessible = true }
            val popup = field.get(fragment) as PopupWindow
            val menu = (popup.contentView as ScrollView).getChildAt(0) as android.widget.LinearLayout
            val call = (0 until menu.childCount).map { menu.getChildAt(it) as TextView }
                .single { it.text == activity.getString(R.string.call) }
            call.performClick()
            assertEquals(android.content.Intent.ACTION_DIAL, shadowOf(activity).nextStartedActivity.action)
            assertFalse(popup.isShowing)
        }
    }

    @Test
    fun `contact command prefixes use inline icons without a heading label`() {
        withPage(SearchMode.CONTACTS_ONLY) { _, fragment, _ ->
            val root = fragment.requireView()
            val search = root.findViewById<SearchView>(R.id.search)
            val editor = root.findViewById<TextView>(R.id.search_src_text)
            for (prefix in listOf("p", "m", "w", "c")) {
                search.setQuery("$prefix ", false)
                awaitCommand { editor.text.toString() == "$prefix " }
                assertEquals("$prefix ", editor.text.toString())
                assertEquals(
                    1,
                    (editor.text as android.text.Spanned).getSpans(0, 1, android.text.style.ImageSpan::class.java).size,
                )
                assertEquals(View.GONE, root.findViewById<View>(R.id.commandIndicator).visibility)
            }
        }
    }

    @Test
    fun `single tile contact actions are inline and launch their existing actions`() {
        withPage(SearchMode.CONTACTS_ONLY, columns = 1) { activity, fragment, _ ->
            val row = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView).getChildAt(0)
            assertEquals(View.GONE, row.findViewById<View>(R.id.itemMenu).visibility)
            assertEquals(View.VISIBLE, row.findViewById<View>(R.id.contactActions).visibility)
            row.findViewById<View>(R.id.contactCallAction).performClick()
            assertEquals(android.content.Intent.ACTION_DIAL, shadowOf(activity).nextStartedActivity.action)
        }
    }

    @Test
    fun `two tile contact shortcuts open without scrollbar indicators`() {
        withPage(SearchMode.CONTACTS_ONLY, columns = 2) { _, fragment, _ ->
            val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            list.getChildAt(0).performClick()
            val field = UniversalSearchFragment::class.java.getDeclaredField("activePopupWindow").apply {
                isAccessible = true
            }
            val popup = field.get(fragment) as PopupWindow
            assertTrue(popup.isShowing)
            assertFalse(popup.contentView.isVerticalScrollBarEnabled)
            assertFalse(popup.contentView.isHorizontalScrollBarEnabled)
            for (action in listOf(R.id.contactCallAction, R.id.contactTextAction, R.id.contactWhatsAppAction)) {
                assertEquals(View.VISIBLE, popup.contentView.findViewById<View>(action).visibility)
            }
            popup.dismiss()
        }
    }

    @Test
    @Config(sdk = [28])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `redesigned pages render black backgrounds and readable tiles at phone widths`() {
        for (mode in SearchMode.entries) withPage(mode) { _, fragment, _ ->
            for (columns in 1..2) {
                val root = fragment.requireView()
                val list = root.findViewById<RecyclerView>(R.id.recyclerView)
                (list.layoutManager as GridLayoutManager).spanCount = columns
                (list.adapter as SearchResultsAdapter).setTileCount(columns)
                list.adapter!!.notifyItemRangeChanged(0, list.adapter!!.itemCount)
                layout(fragment, width = 360)
                fun invalidate(view: View) {
                    view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    view.invalidate()
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) invalidate(view.getChildAt(i))
                }
                invalidate(root)
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                assertEquals(
                    androidx.core.content.ContextCompat.getColor(root.context, R.color.search_background),
                    image.getPixel(0, 0),
                )
                for (i in 0 until list.childCount) {
                    val row = list.getChildAt(i)
                    val title = row.findViewById<TextView>(R.id.itemTitle) ?: continue
                    assertTrue("Result names need usable width in both layouts", title.width >= 32 * root.resources.displayMetrics.density)
                    assertEquals(Color.WHITE, title.currentTextColor)
                    val itemType = row.findViewById<TextView>(R.id.itemType)
                    if (itemType.visibility == View.VISIBLE) assertTrue(itemType.height > 0)
                    val menu = row.findViewById<View>(R.id.itemMenu)
                    if (menu.visibility == View.VISIBLE)
                        assertTrue(menu.width >= 48 * root.resources.displayMetrics.density)
                    val contactActions = row.findViewById<View>(R.id.contactActions)
                    if (contactActions.visibility == View.VISIBLE)
                        assertTrue(contactActions.width >= 120 * root.resources.displayMetrics.density)
                }
                System.getenv("ACES_SCREENSHOT_DIR")?.let { directory ->
                    File(directory, "search-${mode.name.lowercase()}-$columns.png").outputStream().use {
                        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }

                }
                image.recycle()
            }
        }
    }

    @Test
    fun `two tile contact action popup centers over its tile and stays within visible bounds`() {
        assertEquals(
            Rect(95, 312, 255, 368),
            centeredSearchPopupBounds(Rect(0, 0, 400, 800), 100, 300, 150, 80, 160, 56),
        )
        assertEquals(
            Rect(0, 0, 160, 56),
            centeredSearchPopupBounds(Rect(0, 0, 400, 800), 0, 0, 80, 40, 160, 56),
        )
    }
}
