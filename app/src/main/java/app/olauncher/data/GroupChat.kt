package app.olauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class GroupChat(val threadId: Long, val name: String, val addresses: List<String>)

object GroupChatsLoader {
    val conversationsUri: Uri = Telephony.Threads.CONTENT_URI.buildUpon()
        .appendQueryParameter("simple", "true").build()
    val addressesUri: Uri = Uri.parse("content://mms-sms/canonical-addresses")

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Reads thread metadata only, not message bodies. Call off the main thread. */
    fun load(context: Context, contacts: List<Contact>): List<GroupChat> {
        if (!hasPermission(context)) return emptyList()
        val threads = mutableListOf<Pair<Long, List<Long>>>()
        val resolver = context.contentResolver
        resolver.query(
            conversationsUri,
            arrayOf(Telephony.Threads._ID, Telephony.Threads.RECIPIENT_IDS),
            "${Telephony.Threads.MESSAGE_COUNT} > 0", null, "${Telephony.Threads.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val recipients = cursor.getString(1).orEmpty().trim().split(Regex("\\s+"))
                    .mapNotNull(String::toLongOrNull).distinct()
                if (recipients.size > 1) threads.add(cursor.getLong(0) to recipients)
            }
        } ?: error("Messaging provider returned no conversation cursor")
        if (threads.isEmpty()) return emptyList()

        val addresses = mutableMapOf<Long, String>()
        resolver.query(addressesUri, arrayOf("_id", "address"), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(1)?.takeIf(String::isNotBlank)?.let { addresses[cursor.getLong(0)] = it }
            }
        } ?: error("Messaging provider returned no recipient cursor")
        val names = contacts.filter { !it.number.isNullOrBlank() }.associate {
            addressKey(it.number.orEmpty()) to it.name
        }
        return threads.mapNotNull { (id, recipients) ->
            val participants = recipients.mapNotNull(addresses::get).distinct()
            if (participants.size != recipients.size) null
            else GroupChat(id, participants.joinToString(", ") { names[addressKey(it)] ?: it }, participants)
        }
    }

    private fun addressKey(address: String): String =
        if ('@' in address) address.lowercase() else address.filter(Char::isDigit)
}
