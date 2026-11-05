package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import androidx.appcompat.view.ContextThemeWrapper
import app.olauncher.R
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class HomeWidgetLayoutTest {
    @Test
    fun `tiles use padded clock fields and requested date format`() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
        val widget = LayoutInflater.from(context).inflate(R.layout.layout_home_widget, null)

        assertEquals(4, widget.findViewById<LinearLayout>(R.id.widgetTiles).childCount)
        val hourTile = widget.findViewById<ViewGroup>(R.id.widgetHour)
        val minuteTile = widget.findViewById<ViewGroup>(R.id.widgetMinute)
        val periodTile = widget.findViewById<ViewGroup>(R.id.widgetAmPm)
        assertEquals("hh", (hourTile.getChildAt(0) as TextClock).format12Hour.toString())
        assertEquals("hh", (hourTile.getChildAt(0) as TextClock).format24Hour.toString())
        assertEquals("mm", (minuteTile.getChildAt(0) as TextClock).format12Hour.toString())
        assertEquals("mm", (minuteTile.getChildAt(0) as TextClock).format24Hour.toString())
        assertEquals("a", (periodTile.getChildAt(0) as TextClock).format12Hour.toString())
        assertEquals("a", (periodTile.getChildAt(0) as TextClock).format24Hour.toString())
        assertEquals("EEE, MMM dd", widget.findViewById<TextClock>(R.id.widgetDate).format12Hour.toString())
        assertEquals("EEE, MMM dd", widget.findViewById<TextClock>(R.id.widgetDate).format24Hour.toString())
        val batteryIcon = widget.findViewById<View>(R.id.widgetBatteryIcon)
        assertNotNull(batteryIcon)
        assertEquals(24, batteryIcon.layoutParams.width)
        assertEquals(24, batteryIcon.layoutParams.height)
        assertEquals(15f, widget.findViewById<android.widget.TextView>(R.id.widgetBatteryPercent).textSize, 0f)
    }

    @Test
    fun `custom battery app selection is persisted`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app.olauncher", 0).edit().clear().commit()
        val prefs = Prefs(context)

        prefs.batteryAppPackage = "example.battery"
        prefs.batteryAppClassName = "example.battery.Settings"
        prefs.batteryAppUser = "owner"

        val restored = Prefs(context)
        assertEquals("example.battery", restored.batteryAppPackage)
        assertEquals("example.battery.Settings", restored.batteryAppClassName)
        assertEquals("owner", restored.batteryAppUser)
    }
}
