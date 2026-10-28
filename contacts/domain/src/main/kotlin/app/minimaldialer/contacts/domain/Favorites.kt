package app.minimaldialer.contacts.domain

import app.minimaldialer.core.model.Contact
import app.minimaldialer.core.model.PhoneNumbers
import app.minimaldialer.core.model.RecentCall

object Favorites {
    fun of(contacts: List<Contact>): List<Contact> =
        contacts.filter { it.starred && it.numbers.isNotEmpty() }.sortedBy { it.displayName.lowercase() }
}

/** A call-log row with a resolved contact name, if the address book has one. */
data class RecentEntry(val call: RecentCall, val contact: Contact?) {
    val title: String get() = contact?.displayName ?: call.cachedName?.takeIf { it.isNotBlank() } ?: call.number.ifBlank { "Unknown" }
}

object Recents {
    /** Resolves names locally so the list stays correct after a contact is renamed. */
    fun resolve(calls: List<RecentCall>, contacts: List<Contact>): List<RecentEntry> {
        val byDigits = HashMap<String, Contact>()
        contacts.forEach { c -> c.numbers.forEach { n -> byDigits.putIfAbsent(lastDigits(n.number), c) } }
        return calls.map { call ->
            val hit = byDigits[lastDigits(call.number)]?.takeIf { c -> c.numbers.any { PhoneNumbers.looselyEqual(it.number, call.number) } }
            RecentEntry(call, hit)
        }
    }

    private fun lastDigits(n: String) = n.filter { it in '0'..'9' }.takeLast(9)
}
