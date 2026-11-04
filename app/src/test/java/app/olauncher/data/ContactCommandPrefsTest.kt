package app.olauncher.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
class ContactCommandPrefsTest {
    @Test
    fun `defaults and all settings persist through new prefs instances`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = Prefs(context)
        assertEquals(ContactCommandSettings(), prefs.contactCommandSettings())
        val settings = ContactCommandSettings(false, false, 750, 4, false)
        prefs.setContactCommandSettings(settings)
        assertEquals(settings, Prefs(context).contactCommandSettings())
        for (delay in listOf(250, 500, 750)) {
            for (minimum in 2..4) {
                val changed = settings.copy(delayMillis = delay, minimumCharacters = minimum)
                prefs.setContactCommandSettings(changed)
                assertEquals(changed, Prefs(context).contactCommandSettings())
            }
        }
    }

    @Test
    fun `number memory is independent per contact and command and clear touches nothing else`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = Prefs(context)
        val settings = ContactCommandSettings(delayMillis = 500)
        prefs.setContactCommandSettings(settings)
        prefs.setSearchTileCount(SearchMode.CONTACTS_ONLY, 2)
        prefs.rememberContactNumber("john", ContactCommand.PHONE, "111")
        prefs.rememberContactNumber("john", ContactCommand.MESSAGE, "222")
        prefs.rememberContactNumber("john", ContactCommand.WHATSAPP, "333")
        prefs.rememberContactNumber("jane", ContactCommand.PHONE, "444")
        assertEquals("111", Prefs(context).rememberedContactNumber("john", ContactCommand.PHONE))
        assertEquals("222", prefs.rememberedContactNumber("john", ContactCommand.MESSAGE))
        assertEquals("333", prefs.rememberedContactNumber("john", ContactCommand.WHATSAPP))
        assertEquals("444", prefs.rememberedContactNumber("jane", ContactCommand.PHONE))
        prefs.rememberContactNumber("john", ContactCommand.PHONE, null)
        assertNull(prefs.rememberedContactNumber("john", ContactCommand.PHONE))
        assertEquals("222", prefs.rememberedContactNumber("john", ContactCommand.MESSAGE))
        prefs.clearRememberedContactNumbers()
        for (command in ContactCommand.entries) {
            assertNull(prefs.rememberedContactNumber("john", command))
            assertNull(prefs.rememberedContactNumber("jane", command))
        }
        assertEquals(settings, prefs.contactCommandSettings())
        assertEquals(2, prefs.searchTileCount(SearchMode.CONTACTS_ONLY))
    }
}
