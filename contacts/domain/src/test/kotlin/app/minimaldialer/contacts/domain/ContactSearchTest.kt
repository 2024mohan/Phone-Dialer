package app.minimaldialer.contacts.domain

import app.minimaldialer.core.model.CallDirection
import app.minimaldialer.core.model.Contact
import app.minimaldialer.core.model.ContactNumber
import app.minimaldialer.core.model.RecentCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {
    private val mohan = contact(1, "Mohan Anand", "+91 98450 12345", starred = true)
    private val jose = contact(2, "José Ramírez", "+34 600 111 222")
    private val priya = contact(3, "Priya", "080 2222 3333")
    private val all = listOf(mohan, jose, priya)

    @Test fun `number prefix ranks before T9`() {
        val hits = ContactSearch.search(all, "080")
        assertEquals(listOf(priya), hits.map { it.contact })
        assertEquals(0, hits.single().rank)
    }

    @Test fun `T9 on word start`() {
        assertEquals(listOf(mohan), ContactSearch.search(all, "664").map { it.contact })   // "moh"
        assertEquals(listOf(jose), ContactSearch.search(all, "726").map { it.contact })    // "ram"
    }

    @Test fun `T9 on initials`() {
        assertTrue(ContactSearch.search(all, "62").any { it.contact == mohan })
    }

    @Test fun `text search ignores accents and case`() {
        assertEquals(listOf(jose), ContactSearch.search(all, "jose").map { it.contact })
        assertEquals(listOf(jose), ContactSearch.search(all, "RAMIR").map { it.contact })
    }

    @Test fun `empty query returns nothing`() {
        assertTrue(ContactSearch.search(all, "  ").isEmpty())
    }

    @Test fun `favourites are starred contacts with numbers`() {
        assertEquals(listOf(mohan), Favorites.of(all + contact(4, "No Number", null, starred = true)))
    }

    @Test fun `recents resolve names by number`() {
        val calls = listOf(
            RecentCall(1, "09845012345", null, CallDirection.MISSED, 0, 0),
            RecentCall(2, "5550100", "Cached", CallDirection.INCOMING, 0, 10),
            RecentCall(3, "", null, CallDirection.INCOMING, 0, 10),
        )
        val resolved = Recents.resolve(calls, all)
        assertEquals("Mohan Anand", resolved[0].title)
        assertEquals("Cached", resolved[1].title)
        assertNull(resolved[1].contact)
        assertEquals("Unknown", resolved[2].title)
    }

    private fun contact(id: Long, name: String, number: String?, starred: Boolean = false) = Contact(
        id = id, lookupKey = "k$id", displayName = name,
        numbers = listOfNotNull(number?.let { ContactNumber(it, isPrimary = true) }), starred = starred,
    )
}
