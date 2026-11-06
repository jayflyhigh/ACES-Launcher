package app.olauncher.ui.search

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Telephony
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.*
import app.olauncher.ui.HomeFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class GroupChatSearchTest {
    private fun withPage(
        columns: Int = 1,
        mode: SearchMode = SearchMode.CONTACTS_ONLY,
        smsGranted: Boolean = true,
        action: (ContactCommandTestActivity, UniversalSearchFragment, GroupMessagesProvider) -> Unit,
    ) {
        CommandContactsProvider().install()
        val application = RuntimeEnvironment.getApplication()
        if (smsGranted) shadowOf(application).grantPermissions(Manifest.permission.READ_SMS)
        else shadowOf(application).denyPermissions(Manifest.permission.READ_SMS)
        val provider = GroupMessagesProvider().apply {
            attachInfo(application, ProviderInfo().apply { authority = "mms-sms" })
            ShadowContentResolver.registerProviderInternal("mms-sms", this)
        }
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            Prefs(activity).setSearchTileCount(mode, columns)
            val fragment = UniversalSearchFragment().apply {
                arguments = Bundle().apply { putString(Constants.Key.SEARCH_MODE, mode.name) }
            }
            activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commitNow()
            val vm = ViewModelProvider(activity)[MainViewModel::class.java]
            awaitCommand { vm.contactsLoading.value == false && vm.contacts.value?.size == 2 }
            awaitCommand {
                results(fragment).currentList.count { it is SearchItem.GroupChatItem } == if (smsGranted) 1 else 0
            }
            action(activity, fragment, provider)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun results(fragment: UniversalSearchFragment) =
        fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView).adapter as SearchResultsAdapter

    private fun layout(fragment: UniversalSearchFragment) {
        fragment.requireView().apply {
            measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
            layout(0, 0, 1080, 1920)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `only multi participant threads load using two metadata queries`() {
        withPage { activity, fragment, provider ->
            val chat = results(fragment).currentList.filterIsInstance<SearchItem.GroupChatItem>().single().chat
            assertEquals(42L, chat.threadId)
            assertEquals("John Smith, Jane Doe", chat.name)
            assertEquals(listOf("+15551000", "+15553000"), chat.addresses)
            assertEquals(2, provider.queryCount)
            fragment.onGroupChatClick(chat)
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("content://mms-sms/conversations/42", intent.dataString)
        }
    }

    @Test
    fun `group tiles have label group icon and no shortcuts or long press in either layout`() {
        for (columns in 1..2) withPage(columns) { activity, fragment, _ ->
            layout(fragment)
            val adapter = results(fragment)
            val position = adapter.currentList.indexOfFirst { it is SearchItem.GroupChatItem }
            val list = fragment.requireView().findViewById<RecyclerView>(R.id.recyclerView)
            val holder = adapter.createViewHolder(list, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            val tile = holder.itemView
            assertEquals("Group Chat", tile.findViewById<TextView>(R.id.itemType).text.toString())
            assertEquals(View.VISIBLE, tile.findViewById<View>(R.id.itemType).visibility)
            assertEquals(View.GONE, tile.findViewById<View>(R.id.contactActions).visibility)
            assertEquals(View.GONE, tile.findViewById<View>(R.id.itemMenu).visibility)
            assertFalse(tile.isLongClickable)
            val icon = tile.findViewById<android.widget.ImageView>(R.id.itemIcon)
            assertEquals(R.drawable.ic_group, shadowOf(icon.drawable).createdFromResId)
            tile.performClick()
            assertEquals("content://mms-sms/conversations/42", shadowOf(activity).nextStartedActivity.dataString)
        }
    }

    @Test
    fun `typing and command searches never query messages or apply commands to groups`() {
        withPage { _, fragment, provider ->
            val count = provider.queryCount
            val search = fragment.requireView().findViewById<SearchView>(R.id.search)
            search.setQuery("jane", false)
            awaitCommand { results(fragment).currentList.any { it is SearchItem.GroupChatItem } }
            search.setQuery("p john", false)
            awaitCommand {
                results(fragment).currentList.none { it is SearchItem.GroupChatItem } &&
                    results(fragment).currentList.filterIsInstance<SearchItem.ContactItem>().size == 1
            }
            assertEquals(count, provider.queryCount)
        }
    }

    @Test
    fun `groups also appear in Search Anything`() {
        withPage(mode = SearchMode.ALL_SEARCH) { _, fragment, _ ->
            assertEquals(1, results(fragment).currentList.count { it is SearchItem.GroupChatItem })
        }
    }

    @Test
    fun `declining SMS leaves contacts available and never queries messages`() {
        withPage(smsGranted = false) { _, fragment, provider ->
            awaitCommand { results(fragment).currentList.filterIsInstance<SearchItem.ContactItem>().size == 2 }
            assertTrue(results(fragment).currentList.any { it is SearchItem.Permission && it.messages })
            assertEquals(0, provider.queryCount)
        }
    }

    @Test
    fun `message notifications are coalesced and stop when search stops`() {
        withPage { activity, fragment, provider ->
            val before = provider.queryCount
            repeat(10) { activity.contentResolver.notifyChange(Telephony.MmsSms.CONTENT_URI, null) }
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(199))
            assertEquals(before, provider.queryCount)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
            awaitCommand {
                provider.queryCount == before + 2 &&
                    ViewModelProvider(activity)[MainViewModel::class.java].groupChatsLoading.value == false
            }
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            repeat(10) { activity.contentResolver.notifyChange(Telephony.MmsSms.CONTENT_URI, null) }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(before + 2, provider.queryCount)
        }
    }

    @Test
    fun `holding popup commands opens apps without modifying search or firing a contact command`() {
        withPage { activity, fragment, _ ->
            shadowOf(activity.packageManager).addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.whatsapp"),
                ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply { packageName = "com.whatsapp"; name = "WhatsApp" }
                },
            )
            layout(fragment)
            val search = fragment.requireView().findViewById<SearchView>(R.id.search)
            search.setQuery("john", false)
            for ((index, command) in ContactCommand.entries.withIndex()) {
                fragment.requireView().findViewById<View>(R.id.searchPageMenu).performClick()
                val popup = UniversalSearchFragment::class.java.getDeclaredField("activePopupWindow").run {
                    isAccessible = true
                    get(fragment) as PopupWindow
                }
                popup.contentView.findViewById<LinearLayout>(R.id.contactCommands).getChildAt(index).performLongClick()
                val intent = shadowOf(activity).nextStartedActivity
                assertNotNull("No app intent for $command", intent)
                when (command) {
                    ContactCommand.PHONE -> assertEquals(Intent.ACTION_DIAL, intent.action)
                    ContactCommand.MESSAGE -> assertTrue(intent.categories.contains(Intent.CATEGORY_APP_MESSAGING))
                    ContactCommand.WHATSAPP -> assertEquals("com.whatsapp", intent.component?.packageName)
                    ContactCommand.CONTACT -> assertTrue(intent.categories.contains(Intent.CATEGORY_APP_CONTACTS))
                }
                assertFalse(popup.isShowing)
                assertEquals("john", search.query.toString())
                assertNull(intent.data)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertNull(shadowOf(activity).peekNextStartedActivity())
        }
    }

    @Test
    fun `holding home settings opens Android settings`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = HomeFragment()
            activity.supportFragmentManager.beginTransaction().replace(android.R.id.content, fragment).commitNow()
            assertTrue(fragment.requireView().findViewById<View>(R.id.homeSettings).performLongClick())
            assertEquals(android.provider.Settings.ACTION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

class GroupMessagesProvider : ContentProvider() {
    @Volatile var queryCount = 0
    override fun onCreate() = true
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        queryCount++
        return when (uri.path) {
            "/conversations" -> {
                assertEquals("true", uri.getQueryParameter("simple"))
                assertEquals(listOf("_id", "recipient_ids"), projection?.toList())
                assertEquals("message_count > 0", selection)
                MatrixCursor(projection).apply {
                    addRow(arrayOf<Any>(42L, "1 2"))
                    addRow(arrayOf<Any>(43L, "1"))
                    addRow(arrayOf<Any>(44L, "1 1"))
                    addRow(arrayOf<Any>(45L, "1 99"))
                }
            }
            "/canonical-addresses" -> MatrixCursor(projection).apply {
                addRow(arrayOf<Any>(1L, "+15551000"))
                addRow(arrayOf<Any>(2L, "+15553000"))
            }
            else -> error("Must not query message bodies: $uri")
        }
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        error("Read only")
}
