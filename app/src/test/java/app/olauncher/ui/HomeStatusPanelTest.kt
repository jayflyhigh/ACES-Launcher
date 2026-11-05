package app.olauncher.ui

import android.os.Process
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.LayoutQuickActionsBinding
import app.olauncher.ui.search.ContactCommandTestActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class HomeStatusPanelTest {
    @Test
    fun `fixed status panel matches expanded quick menu geometry and date time formats`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val host = FrameLayout(activity)
            val binding = LayoutQuickActionsBinding.inflate(activity.layoutInflater, host, false)
            host.addView(binding.root)
            host.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 400, 800)
            val fixed = binding.homeStatusPanel
            val menu = binding.quickPanel
            val fixedMargins = fixed.layoutParams as android.view.ViewGroup.MarginLayoutParams
            val menuMargins = menu.layoutParams as android.view.ViewGroup.MarginLayoutParams
            assertEquals(menuMargins.leftMargin, fixedMargins.leftMargin)
            assertEquals(menuMargins.rightMargin, fixedMargins.rightMargin)
            assertEquals(menuMargins.topMargin, fixedMargins.topMargin)
            assertEquals(menuMargins.bottomMargin, fixedMargins.bottomMargin)

            val row = fixed.getChildAt(0) as LinearLayout
            assertEquals(4, row.childCount)
            assertEquals("hh", (row.findViewById<android.view.ViewGroup>(R.id.statusHour).getChildAt(0) as TextClock).format12Hour.toString())
            assertEquals("mm", (row.findViewById<android.view.ViewGroup>(R.id.statusMinute).getChildAt(0) as TextClock).format12Hour.toString())
            assertEquals("a", (row.findViewById<android.view.ViewGroup>(R.id.statusPeriod).getChildAt(0) as TextClock).format12Hour.toString())
            assertEquals("EEE, MMM dd", binding.homeStatusDate.format12Hour.toString())
            assertTrue(binding.batteryLevel.text.toString().contains("%"))
            assertTrue(binding.batteryCharging.visibility == android.view.View.GONE)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `battery app selection persists independently`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val model = MainViewModel(activity.application)
            val app = AppModel.App("Settings", null, "com.example.settings", "SettingsActivity",
                user = Process.myUserHandle())
            model.selectedApp(app, Constants.FLAG_SET_BATTERY_APP)
            val prefs = Prefs(activity)
            assertEquals(app.appPackage, prefs.batteryAppPackage)
            assertEquals(app.user.toString(), prefs.batteryAppUser)
            assertEquals(app.activityClassName, prefs.batteryAppClassName)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}