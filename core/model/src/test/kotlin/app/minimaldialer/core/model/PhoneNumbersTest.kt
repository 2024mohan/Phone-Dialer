package app.minimaldialer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun `normalize strips formatting and keeps leading plus`() {
        assertEquals("+4915112345678", PhoneNumbers.normalize("+49 (151) 123-456 78"))
        assertEquals("*21#", PhoneNumbers.normalize("*21#"))
    }

    @Test fun `normalize drops a plus that is not leading`() {
        assertEquals("123", PhoneNumbers.normalize("1+2+3"))
    }

    @Test fun `loose equality matches national and international forms`() {
        assertTrue(PhoneNumbers.looselyEqual("+91 98450 12345", "098450 12345"))
        assertFalse(PhoneNumbers.looselyEqual("+91 98450 12345", "+91 98450 12346"))
    }

    @Test fun `short codes must match exactly`() {
        assertTrue(PhoneNumbers.looselyEqual("112", "112"))
        assertFalse(PhoneNumbers.looselyEqual("112", "1112"))
        assertFalse(PhoneNumbers.looselyEqual("", ""))
    }
}
