package app.minimaldialer.contacts.domain

import app.minimaldialer.core.model.Contact
import app.minimaldialer.core.model.RecentCall
import kotlinx.coroutines.flow.Flow

/** Read access to the device address book. Implementations re-emit when the provider changes. */
interface ContactsRepository {
    fun contacts(): Flow<List<Contact>>
    suspend fun findByNumber(number: String): Contact?
    /** Toggles the system "starred" flag, which is what favourites are. Needs WRITE_CONTACTS. */
    suspend fun setStarred(contactId: Long, starred: Boolean): Boolean
}

interface CallLogRepository {
    fun recentCalls(limit: Int = 200): Flow<List<RecentCall>>
}
