package app.olauncher.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class ContactCommandTarget(val uri: Uri, val lookupKey: String, val numbers: List<ContactPhoneNumber>)

object ContactCommandTargets {
    /** Resolve the lookup URI afresh so removed numbers and merged contacts are never trusted from cache. */
    fun load(context: Context, contact: Contact, includeNumbers: Boolean = true): ContactCommandTarget? {
        val resolver = context.contentResolver
        val uri = if (contact.lookupKey.isNotEmpty()) {
            ContactsContract.Contacts.lookupContact(
                resolver, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
            ) ?: return null
        } else ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
        val lookupKey = resolver.query(
            uri, arrayOf(ContactsContract.Contacts.LOOKUP_KEY), null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getString(0).orEmpty()
        } ?: return null
        if (!includeNumbers) return ContactCommandTarget(uri, lookupKey, emptyList())
        val id = ContentUris.parseId(uri)
        val numbers = mutableListOf<ContactPhoneNumber>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id.toString()), null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val phone = ContactPhoneNumber(
                    number, cursor.getString(1),
                    ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources, cursor.getInt(2), cursor.getString(3)
                    ).toString(),
                    cursor.getInt(4) != 0,
                )
                if (phone.identity.any(Char::isDigit)) numbers.add(phone)
            }
        }
        val unique = numbers.groupBy { it.identity }.values.map { group ->
            group.firstOrNull { it.preferred } ?: group.first()
        }
        return ContactCommandTarget(uri, lookupKey, unique)
    }
}
