package app.olauncher.ui.search

import android.os.Looper
import android.widget.FrameLayout
import android.widget.RadioGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class GlobalAppearanceFragmentTest {
    class TestActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: android.os.Bundle?) {
            setTheme(R.style.AppTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = android.R.id.content })
        }
    }

    @Test
    fun `global appearance opens and renders every preview page in both layouts`() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = GlobalAppearanceFragment()
            activity.supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment).commitNow()
            val root = fragment.requireView()
            val pages = root.findViewById<RadioGroup>(R.id.previewPage)
            for (page in listOf(R.id.previewApps, R.id.previewContacts, R.id.previewEngines, R.id.previewEverything)) {
                pages.check(page)
                for (columns in 1..2) {
                    root.findViewById<android.view.View>(
                        if (columns == 1) R.id.decreaseLayout else R.id.increaseLayout
                    ).performClick()
                    root.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, 1080, 1920)
                    shadowOf(Looper.getMainLooper()).idle()
                    val results = root.findViewById<RecyclerView>(R.id.previewResults)
                    assertEquals(if (page == R.id.previewEverything) 9 else 2, results.adapter!!.itemCount)
                    assertTrue("Preview rows must actually be rendered", results.childCount > 0)
                }
            }
            controller.recreate()
            shadowOf(Looper.getMainLooper()).idle()
            val restored = controller.get().supportFragmentManager.findFragmentById(android.R.id.content)
            assertNotNull(restored)
            assertEquals(
                R.id.previewEverything,
                restored!!.requireView().findViewById<RadioGroup>(R.id.previewPage).checkedRadioButtonId,
            )
        } finally {
            controller.pause().stop().destroy()
        }

    }

    @Test
    fun `app search settings remains an empty placeholder`() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = AppSearchSettingsFragment()
            activity.supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment).commitNow()
            assertEquals(0, (fragment.requireView() as android.view.ViewGroup).childCount)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
