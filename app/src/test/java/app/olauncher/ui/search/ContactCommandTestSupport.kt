package app.olauncher.ui.search

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.ActivityNotFoundException
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.util.concurrent.TimeUnit

class ContactCommandTestActivity : FragmentActivity() {
    var whatsappAvailable = true
    var dialerAvailable = true
    var playStoreAvailable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(app.olauncher.R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = android.R.id.content })
    }

    override fun startActivity(intent: Intent) {
        if ((!whatsappAvailable && intent.`package`?.startsWith("com.whatsapp") == true) ||
            (!dialerAvailable && intent.action == Intent.ACTION_DIAL) ||
            (!playStoreAvailable && intent.`package` == "com.android.vending")
        ) throw ActivityNotFoundException("Test app unavailable")
        super.startActivity(intent)
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (!playStoreAvailable && intent.`package` == "com.android.vending")
            throw ActivityNotFoundException("Test Play Store unavailable")
        super.startActivityForResult(intent, requestCode, options)
    }
}

class CommandContactsProvider : ContentProvider() {
    data class Person(val id: Long, val key: String, val name: String, val photoUri: String? = null)
    data class Number(val contactId: Long, val number: String, val type: Int, val preferred: Boolean = false)

    val people = mutableListOf(Person(1, "john", "John Smith"), Person(2, "jane", "Jane Doe"))
    val numbers = mutableListOf(
        Number(1, "+15551000", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE),
        Number(2, "+15553000", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE),
    )
    var denyAccess = false
    var queryCount = 0
    val lookupRedirects = mutableMapOf<String, Long>()
    var beforeQuery: (() -> Unit)? = null

    fun install() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        attachInfo(application, android.content.pm.ProviderInfo().apply { authority = ContactsContract.AUTHORITY })
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, this)
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        queryCount++
        beforeQuery?.invoke()
        if (denyAccess) throw SecurityException("Test permission revoked")
        val columns = projection ?: arrayOf("_id", "lookup", "display_name")
        val cursor = MatrixCursor(columns)
        if (uri.path.orEmpty().contains("phones")) {
            numbers.filter { number ->
                people.any { it.id == number.contactId } &&
                    (selectionArgs == null || selectionArgs.first() == number.contactId.toString())
            }.forEach { number ->
                val person = people.first { it.id == number.contactId }
                val data = mapOf(
                    "contact_id" to person.id, "lookup" to person.key, "display_name" to person.name,
                    "data1" to number.number, "data4" to number.number,
                    "data2" to number.type, "data3" to null, "is_super_primary" to if (number.preferred) 1 else 0,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI to person.photoUri,
                )
                cursor.addRow(columns.map { data[it] })
            }
        } else {
            val lookupIndex = uri.pathSegments.indexOf("lookup")
            val redirected = if (lookupIndex >= 0)
                lookupRedirects[uri.pathSegments[lookupIndex + 1]] else null
            val id = redirected ?: uri.lastPathSegment?.toLongOrNull()
            people.filter { id == null || it.id == id }.forEach { person ->
                val data = mapOf("_id" to person.id, "lookup" to person.key, "display_name" to person.name,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI to person.photoUri)
                cursor.addRow(columns.map { data[it] })
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Commands must not write contacts")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        error("Commands must not delete contacts")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        error("Commands must not update contacts")
}

fun awaitCommand(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition() && System.nanoTime() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
    }
    assertTrue(
        "Timed out waiting for contact command: " +
            org.robolectric.shadows.ShadowLog.getLogs().filter { it.tag == "ContactCommands" }
                .joinToString { "${it.msg}: ${it.throwable}" },
        condition(),
    )
}
