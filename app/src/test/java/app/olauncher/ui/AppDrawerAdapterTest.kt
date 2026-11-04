package app.olauncher.ui

import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class AppDrawerAdapterTest {
    private fun app(label: String) = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = "test.${label.lowercase()}",
        activityClassName = "MainActivity",
        user = Process.myUserHandle(),
    )

    private fun adapter(onClick: (AppModel) -> Unit = {}) = AppDrawerAdapter(
        Constants.FLAG_LAUNCH_APP,
        Gravity.START,
        appClickListener = onClick,
        appInfoListener = {},
        appDeleteListener = {},
        appHideListener = { _, _ -> },
        appRenameListener = { _, _ -> },
    )

    private fun parent() = FrameLayout(
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
    )

    @Test
    fun `refreshing does not mutate caller data or accumulate padding rows`() {
        val source = mutableListOf<AppModel>(app("Camera"))
        val adapter = adapter()
        adapter.setAppList(source)
        adapter.setAppList(source)
        assertEquals(1, source.size)
        assertEquals(2, adapter.itemCount)
        assertEquals(1, adapter.currentList.count { it.appPackage.isEmpty() })
    }

    @Test
    fun `binding and clicks use the displayed snapshot while a diff is pending`() {
        var clicked: AppModel? = null
        val camera = app("Camera")
        val adapter = adapter { clicked = it }
        adapter.setAppList(listOf(camera))
        adapter.setAppList(listOf(AppModel.PrivateSpaceHeader()))

        assertEquals(AppDrawerAdapter.VIEW_TYPE_APP, adapter.getItemViewType(0))
        val holder = adapter.onCreateViewHolder(parent(), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        val title = holder.itemView.findViewById<TextView>(R.id.appTitle)
        assertEquals("Camera", title.text.toString())
        title.performClick()
        assertEquals(camera, clicked)
        adapter.launchFirstInList()
        assertEquals(camera, clicked)
    }

    @Test
    fun `hiding an app preserves previously submitted snapshots`() {
        val camera = app("Camera")
        val adapter = adapter()
        adapter.setAppList(listOf(camera))
        val previous = adapter.currentList
        adapter.removeApp(camera)
        assertEquals(2, previous.size)
        assertEquals(camera, previous.first())
        await { adapter.currentList.none { it == camera } }
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `recycled rows do not accumulate rename text watchers`() {
        val adapter = adapter()
        adapter.setAppList(listOf(app("Camera")))
        val holder = adapter.onCreateViewHolder(parent(), AppDrawerAdapter.VIEW_TYPE_APP)
        adapter.onBindViewHolder(holder, 0)
        val editor = holder.itemView.findViewById<EditText>(R.id.etAppRename)
        val listeners = TextView::class.java.getDeclaredField("mListeners").apply { isAccessible = true }
        val initialCount = (listeners.get(editor) as List<*>).size
        repeat(25) { adapter.onBindViewHolder(holder, 0) }
        assertEquals(initialCount, (listeners.get(editor) as List<*>).size)
    }

    @Test
    fun `keyboard done saves a rename once and consumes the editor action`() {
        val camera = app("Camera")
        val saved = mutableListOf<Pair<AppModel, String>>()
        val adapter = AppDrawerAdapter(
            Constants.FLAG_LAUNCH_APP,
            Gravity.START,
            appClickListener = {},
            appInfoListener = {},
            appDeleteListener = {},
            appHideListener = { _, _ -> },
            appRenameListener = { app, label -> saved.add(app to label) },
        )
        adapter.setAppList(listOf(camera))
        val holder = adapter.onCreateViewHolder(parent(), AppDrawerAdapter.VIEW_TYPE_APP)
        adapter.onBindViewHolder(holder, 0)
        val editor = holder.itemView.findViewById<EditText>(R.id.etAppRename)
        editor.setText("  My camera  ")
        assertTrue(shadowOf(editor).onEditorActionListener.onEditorAction(editor, EditorInfo.IME_ACTION_DONE, null))
        assertEquals(listOf(camera to "My camera"), saved)
    }

    @Test
    fun `filtering preserves normalized search and leading space disables auto launch`() {
        val launched = mutableListOf<AppModel>()
        val camera = app("Camera")
        val maps = app("Google Maps")
        val adapter = adapter { launched.add(it) }
        adapter.setAppList(listOf(camera, maps))
        adapter.filter.filter(" googlemaps")
        await { adapter.currentList == listOf(maps) }
        assertTrue(launched.isEmpty())
        adapter.filter.filter("cam")
        await { launched == listOf(camera) }
        assertEquals(listOf(camera), adapter.currentList)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Timed out waiting for the adapter update", condition())
    }
}
