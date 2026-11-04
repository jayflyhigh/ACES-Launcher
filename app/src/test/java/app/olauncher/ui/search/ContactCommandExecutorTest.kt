package app.olauncher.ui.search

import android.content.Intent
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class ContactCommandExecutorTest {
    private val john = Contact(1, "john", "John Smith", "+15551000", "+15551000")

    private fun withExecutor(
        action: (ContactCommandTestActivity, ContactCommandExecutor, CommandContactsProvider) -> Unit,
    ) {
        val provider = CommandContactsProvider().apply { install() }
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        val activity = controller.get()
        val executor = ContactCommandExecutor(activity, activity)
        try {
            action(activity, executor, provider)
        } finally {
            executor.cancel()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `explicit memory overrides Android preference and commands keep independent choices`() {
        withExecutor { activity, executor, provider ->
            provider.numbers.add(CommandContactsProvider.Number(1, "+15552000", Phone.TYPE_WORK, true))
            val prefs = Prefs(activity)
            prefs.rememberContactNumber("john", ContactCommand.PHONE, "+15551000")
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15551000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
            executor.execute(john, ContactCommand.MESSAGE)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15552000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
            prefs.setContactCommandSettings(ContactCommandSettings(rememberNumbers = false))
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15552000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
        }
    }

    @Test
    fun `number picker labels choices remembers selection and ignores double taps`() {
        withExecutor { activity, executor, provider ->
            provider.numbers.add(CommandContactsProvider.Number(1, "+15552000", Phone.TYPE_WORK))
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { ShadowDialog.getLatestDialog()?.isShowing == true }
            assertNull(shadowOf(activity).peekNextStartedActivity())
            val work = findText(ShadowDialog.getLatestDialog().window!!.decorView, "Work\n+15552000")
            assertNotNull(work)
            work!!.performClick()
            work.performClick()
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15552000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
            assertNull(shadowOf(activity).peekNextStartedActivity())
            assertEquals("+15552000", Prefs(activity).rememberedContactNumber("john", ContactCommand.PHONE))
            assertNull(Prefs(activity).rememberedContactNumber("john", ContactCommand.MESSAGE))
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15552000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
        }
    }

    @Test
    fun `remember off asks again and never saves picker selection`() {
        withExecutor { activity, executor, provider ->
            provider.numbers.add(CommandContactsProvider.Number(1, "+15552000", Phone.TYPE_WORK))
            Prefs(activity).setContactCommandSettings(ContactCommandSettings(rememberNumbers = false))
            executor.execute(john, ContactCommand.MESSAGE)
            awaitCommand { ShadowDialog.getLatestDialog()?.isShowing == true }
            findText(ShadowDialog.getLatestDialog().window!!.decorView, "Mobile\n+15551000")!!.performClick()
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            shadowOf(activity).nextStartedActivity
            assertNull(Prefs(activity).rememberedContactNumber("john", ContactCommand.MESSAGE))
            val previous = ShadowDialog.getLatestDialog()
            executor.execute(john, ContactCommand.MESSAGE)
            awaitCommand { ShadowDialog.getLatestDialog() !== previous }
            assertNull(shadowOf(activity).peekNextStartedActivity())
        }
    }

    @Test
    fun `removed numbers are never used even when edited during selection`() {
        withExecutor { activity, executor, provider ->
            provider.numbers.add(CommandContactsProvider.Number(1, "+15552000", Phone.TYPE_WORK))
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { ShadowDialog.getLatestDialog()?.isShowing == true }
            val work = findText(ShadowDialog.getLatestDialog().window!!.decorView, "Work\n+15552000")!!
            provider.numbers.removeAll { it.number == "+15552000" }
            work.performClick()
            awaitCommand {
                ShadowToast.getTextOfLatestToast() == activity.getString(R.string.contact_command_number_changed)
            }
            assertNull(shadowOf(activity).peekNextStartedActivity())
            Prefs(activity).rememberContactNumber("john", ContactCommand.PHONE, "+15552000")
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15551000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
            assertNull(Prefs(activity).rememberedContactNumber("john", ContactCommand.PHONE))
        }
    }

    @Test
    fun `deleted contacts permission errors and missing apps are surfaced without launching`() {
        withExecutor { activity, executor, provider ->
            provider.people.removeAll { it.id == 1L }
            executor.execute(john, ContactCommand.CONTACT)
            awaitCommand { ShadowToast.getTextOfLatestToast() == activity.getString(R.string.contact_command_missing) }
            assertNull(shadowOf(activity).peekNextStartedActivity())
            provider.people.add(CommandContactsProvider.Person(1, "john", "John Smith"))
            provider.denyAccess = true
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { ShadowToast.getTextOfLatestToast() == activity.getString(R.string.contacts_permission_denied) }
            provider.denyAccess = false
            activity.dialerAvailable = false
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { ShadowToast.getTextOfLatestToast() == activity.getString(R.string.unable_to_open_app) }
            assertNull(shadowOf(activity).peekNextStartedActivity())
        }
    }

    @Test
    fun `WhatsApp failure offers contact details but never substitutes another action silently`() {
        withExecutor { activity, executor, _ ->
            activity.whatsappAvailable = false
            executor.execute(john, ContactCommand.WHATSAPP)
            awaitCommand { ShadowDialog.getLatestDialog()?.isShowing == true }
            assertNotNull(findText(
                ShadowDialog.getLatestDialog().window!!.decorView,
                activity.getString(R.string.contact_command_whatsapp_failed),
            ))
            assertNull(shadowOf(activity).peekNextStartedActivity())
            ShadowDialog.getLatestDialog().findViewById<View>(R.id.tvAction).performClick()
            assertEquals(Intent.ACTION_VIEW, shadowOf(activity).nextStartedActivity.action)
        }
    }

    @Test
    fun `canceling a pending resolve never launches and contact details need no phone number`() {
        withExecutor { activity, executor, provider ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            provider.beforeQuery = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            try {
                executor.execute(john, ContactCommand.PHONE)
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                executor.cancel()
            } finally {
                provider.beforeQuery = null
                release.countDown()
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(shadowOf(activity).peekNextStartedActivity())
            provider.numbers.clear()
            executor.execute(john, ContactCommand.CONTACT)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("content://com.android.contacts/contacts/1", shadowOf(activity).nextStartedActivity.dataString)
        }

    }

    @Test
    fun `merged contact lookup resolves the current record and never reuses a removed number`() {
        withExecutor { activity, executor, provider ->
            Prefs(activity).rememberContactNumber("john", ContactCommand.PHONE, "+15551000")
            provider.people.removeAll { it.id == 1L }
            provider.people.add(CommandContactsProvider.Person(3, "merged-john", "John Smith"))
            provider.numbers.add(CommandContactsProvider.Number(3, "+15554000", Phone.TYPE_MOBILE))
            provider.lookupRedirects["john"] = 3
            executor.execute(john, ContactCommand.PHONE)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("+15554000", shadowOf(activity).nextStartedActivity.data?.schemeSpecificPart)
            executor.execute(john, ContactCommand.CONTACT)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals("content://com.android.contacts/contacts/3", shadowOf(activity).nextStartedActivity.dataString)
        }
    }

    @Test
    fun `WhatsApp rejects missing country codes and supports international double zero prefixes`() {
        withExecutor { activity, executor, provider ->
            provider.numbers.clear()
            provider.numbers.add(CommandContactsProvider.Number(1, "5551000", Phone.TYPE_MOBILE))
            executor.execute(john, ContactCommand.WHATSAPP)
            awaitCommand { ShadowDialog.getLatestDialog()?.isShowing == true }
            assertNull(shadowOf(activity).peekNextStartedActivity())
            provider.numbers.clear()
            provider.numbers.add(CommandContactsProvider.Number(1, "0015551000", Phone.TYPE_MOBILE))
            executor.execute(john, ContactCommand.WHATSAPP)
            awaitCommand { shadowOf(activity).peekNextStartedActivity() != null }
            assertEquals(
                "https://api.whatsapp.com/send?phone=15551000",
                shadowOf(activity).nextStartedActivity.dataString,
            )
        }
    }

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findText(view.getChildAt(index), text)?.let { return it }
        }
        return null
    }
}
