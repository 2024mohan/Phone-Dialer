package app.minimaldialer.contacts.domain

import app.minimaldialer.core.model.Contact
import app.minimaldialer.dialer.domain.T9
import java.text.Normalizer

/** A search hit and why it matched, so the UI can show the matched number. */
data class ContactMatch(val contact: Contact, val matchedNumber: String?, val rank: Int)

/**
 * Matches the dialer's typed digits (number substring and T9 on names) or free text
 * (accent-insensitive name substring). Lower rank sorts first.
 */
object ContactSearch {

    fun search(contacts: List<Contact>, query: String, limit: Int = 50): List<ContactMatch> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val digitQuery = q.all { it in '0'..'9' || it == '+' || it == '*' || it == '#' }
        return contacts.asSequence()
            .mapNotNull { if (digitQuery) matchDigits(it, q.filter { c -> c in '0'..'9' }) else matchText(it, q) }
            .sortedWith(compareBy<ContactMatch> { it.rank }.thenBy { it.contact.displayName.lowercase() })
            .take(limit)
            .toList()
    }

    private fun matchDigits(contact: Contact, digits: String): ContactMatch? {
        if (digits.isEmpty()) return null
        contact.numbers.forEach { n ->
            val nd = n.number.filter { it in '0'..'9' }
            if (nd.startsWith(digits)) return ContactMatch(contact, n.number, rank = 0)
        }
        contact.numbers.forEach { n ->
            if (n.number.filter { it in '0'..'9' }.contains(digits)) return ContactMatch(contact, n.number, rank = 2)
        }
        val words = words(contact.displayName)
        // T9 on the start of any word ("666" finds "Mohan" via "mo", and "Ravi Mohan").
        if (words.any { T9.encode(it).startsWith(digits) }) return ContactMatch(contact, contact.primaryNumber?.number, rank = 1)
        // T9 on initials: "62" finds "Mohan Anand".
        val initials = words.mapNotNull { w -> w.firstOrNull()?.let { T9.digitFor(it) } }.joinToString("")
        if (digits.length >= 2 && initials.startsWith(digits)) return ContactMatch(contact, contact.primaryNumber?.number, rank = 1)
        return null
    }

    private fun matchText(contact: Contact, text: String): ContactMatch? {
        val needle = fold(text)
        val name = fold(contact.displayName)
        return when {
            name.startsWith(needle) -> ContactMatch(contact, contact.primaryNumber?.number, 0)
            words(name).any { it.startsWith(needle) } -> ContactMatch(contact, contact.primaryNumber?.number, 1)
            name.contains(needle) -> ContactMatch(contact, contact.primaryNumber?.number, 2)
            else -> null
        }
    }

    private fun words(s: String) = s.split(' ', '-', '.', ',', '_').filter { it.isNotBlank() }

    /** Lower-case and strip combining marks so "José" matches "jose". */
    internal fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
}
