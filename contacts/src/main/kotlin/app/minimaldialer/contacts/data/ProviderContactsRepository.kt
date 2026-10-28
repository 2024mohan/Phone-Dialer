package app.minimaldialer.contacts.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import app.minimaldialer.contacts.domain.CallLogRepository
import app.minimaldialer.contacts.domain.ContactsRepository
import app.minimaldialer.core.model.CallDirection
import app.minimaldialer.core.model.Contact
import app.minimaldialer.core.model.ContactNumber
import app.minimaldialer.core.model.RecentCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Emits a value now and again whenever [uri] changes. */
private fun ContentResolver.changes(uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    trySend(Unit)
    val registered = runCatching { registerContentObserver(uri, true, observer) }.isSuccess
    awaitClose { if (registered) unregisterContentObserver(observer) }
}.conflate()

class ProviderContactsRepository(context: Context) : ContactsRepository {
    private val resolver = context.applicationContext.contentResolver
    private val resources = context.applicationContext.resources

    override fun contacts(): Flow<List<Contact>> =
        resolver.changes(ContactsContract.Contacts.CONTENT_URI)
            .map { queryContacts() }
            .flowOn(Dispatchers.IO)

    /** Missing READ_CONTACTS yields an empty list; the UI shows the permission card. */
    private fun queryContacts(): List<Contact> = try {
        val projection = arrayOf(
            Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER,
            Phone.TYPE, Phone.LABEL, Phone.IS_SUPER_PRIMARY, Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED,
        )
        val byId = LinkedHashMap<Long, Contact>()
        resolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val iKey = c.getColumnIndexOrThrow(Phone.LOOKUP_KEY)
            val iName = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val iNum = c.getColumnIndexOrThrow(Phone.NUMBER)
            val iType = c.getColumnIndexOrThrow(Phone.TYPE)
            val iLabel = c.getColumnIndexOrThrow(Phone.LABEL)
            val iPrimary = c.getColumnIndexOrThrow(Phone.IS_SUPER_PRIMARY)
            val iPhoto = c.getColumnIndexOrThrow(Phone.PHOTO_THUMBNAIL_URI)
            val iStar = c.getColumnIndexOrThrow(Phone.STARRED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val number = c.getString(iNum)?.takeIf { it.isNotBlank() } ?: continue
                val label = runCatching {
                    Phone.getTypeLabel(resources, c.getInt(iType), c.getString(iLabel)).toString()
                }.getOrNull()
                val entry = ContactNumber(number, label, c.getInt(iPrimary) != 0)
                val existing = byId[id]
                if (existing == null) {
                    byId[id] = Contact(
                        id = id,
                        lookupKey = c.getString(iKey).orEmpty(),
                        displayName = c.getString(iName)?.takeIf { it.isNotBlank() } ?: number,
                        numbers = listOf(entry),
                        photoUri = c.getString(iPhoto),
                        starred = c.getInt(iStar) != 0,
                    )
                } else if (existing.numbers.none { it.normalized == entry.normalized }) {
                    byId[id] = existing.copy(numbers = existing.numbers + entry)
                }
            }
        }
        byId.values.toList()
    } catch (e: SecurityException) {
        emptyList()
    }

    override suspend fun findByNumber(number: String): Contact? = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.STARRED),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                Contact(
                    id = c.getLong(0), lookupKey = c.getString(1).orEmpty(),
                    displayName = c.getString(2).orEmpty(), numbers = listOf(ContactNumber(number, isPrimary = true)),
                    starred = c.getInt(3) != 0,
                )
            }
        } catch (e: SecurityException) {
            null
        }
    }

    override suspend fun setStarred(contactId: Long, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
            resolver.update(
                ContactsContract.Contacts.CONTENT_URI, values,
                "${ContactsContract.Contacts._ID}=?", arrayOf(contactId.toString()),
            ) > 0
        } catch (e: SecurityException) {
            false
        }
    }
}

class ProviderCallLogRepository(context: Context) : CallLogRepository {
    private val resolver = context.applicationContext.contentResolver

    override fun recentCalls(limit: Int): Flow<List<RecentCall>> =
        resolver.changes(CallLog.Calls.CONTENT_URI)
            .map { query(limit) }
            .flowOn(Dispatchers.IO)

    private fun query(limit: Int): List<RecentCall> = try {
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        val uri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        resolver.query(uri, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            buildList {
                while (c.moveToNext() && size < limit) {
                    add(
                        RecentCall(
                            id = c.getLong(0),
                            number = c.getString(1).orEmpty(),
                            cachedName = c.getString(2),
                            direction = direction(c.getInt(3)),
                            timestampMillis = c.getLong(4),
                            durationSeconds = c.getLong(5),
                        ),
                    )
                }
            }
        }.orEmpty()
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun direction(type: Int) = when (type) {
        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallDirection.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallDirection.BLOCKED
        CallLog.Calls.VOICEMAIL_TYPE -> CallDirection.VOICEMAIL
        else -> CallDirection.UNKNOWN
    }
}
