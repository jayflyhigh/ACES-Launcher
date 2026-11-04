package app.olauncher.ui.search

import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.widget.SwitchCompat
import app.olauncher.R
import app.olauncher.data.ContactCommand
import app.olauncher.data.ContactCommandSettings
import app.olauncher.data.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class ContactSearchSettingsFragmentTest {
    @Test
    fun `all controls persist and restore after activity recreation`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = ContactSearchSettingsFragment()
            activity.supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment).commitNow()
            fragment.requireView().apply {
                findViewById<SwitchCompat>(R.id.automaticCommands).isChecked = false
                findViewById<SwitchCompat>(R.id.rememberNumbers).isChecked = false
                findViewById<SwitchCompat>(R.id.showCommandIndicator).isChecked = false
                findViewById<RadioGroup>(R.id.commandDelay).check(R.id.delayCautious)
                findViewById<RadioGroup>(R.id.commandMinimum).check(R.id.minimumFour)
            }
            assertEquals(ContactCommandSettings(false, false, 750, 4, false), Prefs(activity).contactCommandSettings())
            controller.recreate()
            val restored = controller.get().supportFragmentManager.findFragmentById(android.R.id.content)!!
            restored.requireView().apply {
                assertFalse(findViewById<SwitchCompat>(R.id.automaticCommands).isChecked)
                assertFalse(findViewById<SwitchCompat>(R.id.rememberNumbers).isChecked)
                assertFalse(findViewById<SwitchCompat>(R.id.showCommandIndicator).isChecked)
                assertEquals(R.id.delayCautious, findViewById<RadioGroup>(R.id.commandDelay).checkedRadioButtonId)
                assertEquals(R.id.minimumFour, findViewById<RadioGroup>(R.id.commandMinimum).checkedRadioButtonId)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `clearing preferences requires confirmation`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val prefs = Prefs(activity)
            prefs.rememberContactNumber("john", ContactCommand.PHONE, "111")
            val fragment = ContactSearchSettingsFragment()
            activity.supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment).commitNow()
            fragment.requireView().findViewById<View>(R.id.clearNumbers).performClick()
            assertEquals("111", prefs.rememberedContactNumber("john", ContactCommand.PHONE))
            ShadowDialog.getLatestDialog().findViewById<View>(R.id.tvAction).performClick()
            assertNull(prefs.rememberedContactNumber("john", ContactCommand.PHONE))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
