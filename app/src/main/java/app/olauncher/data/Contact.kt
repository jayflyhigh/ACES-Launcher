package app.olauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.Collator

data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val number: String?,
    val normalizedNumber: String?,
)

object ContactsLoader {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Blocking; call off the main thread. */
    fun load(context: Context): List<Contact> {
        if (!hasPermission(context)) return emptyList()
        val resolver = context.contentResolver
        val byId = LinkedHashMap<Long, Contact>()
        val primaryIds = HashSet<Long>()

        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(2) ?: continue
                    val isPrimary = c.getInt(5) != 0
                    if (byId.containsKey(id) && (!isPrimary || id in primaryIds)) continue
                    if (isPrimary) primaryIds.add(id)
                    byId[id] = Contact(id, c.getString(1) ?: "", name, c.getString(3), c.getString(4))
                }
            }

            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (byId.containsKey(id)) continue
                    val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    byId[id] = Contact(id, c.getString(1) ?: "", name, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val collator = Collator.getInstance()
        return byId.values.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
}
