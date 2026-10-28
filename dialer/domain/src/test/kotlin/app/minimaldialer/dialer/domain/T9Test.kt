package app.minimaldialer.dialer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class T9Test {
    @Test fun encodesNames() {
        assertEquals("26662743", T9.encode("Ann-Marie"))
        assertEquals("66426", T9.encode("Mohan"))
    }

    @Test fun digitsPassThrough() {
        assertEquals("5", T9.digitFor('5').toString())
        assertNull(T9.digitFor('+'))
        assertNull(T9.digitFor('é'))
    }
}
