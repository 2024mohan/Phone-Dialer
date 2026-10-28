package app.minimaldialer.dialer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialInputTest {
    @Test fun `typing digits appends`() {
        val input = listOf(DialKey.ONE, DialKey.TWO, DialKey.STAR, DialKey.POUND)
            .fold(DialInput.EMPTY) { acc, k -> acc.press(k) }
        assertEquals("12*#", input.text)
    }

    @Test fun `long press zero gives plus only at start`() {
        assertEquals("+", DialInput.EMPTY.longPress(DialKey.ZERO).text)
        assertEquals("1", DialInput("1").longPress(DialKey.ZERO).text)
    }

    @Test fun `long press star and pound insert pause and wait`() {
        assertEquals("1,", DialInput("1").longPress(DialKey.STAR).text)
        assertEquals("1;", DialInput("1").longPress(DialKey.POUND).text)
        assertEquals("1", DialInput("1").longPress(DialKey.FIVE).text)
    }

    @Test fun `backspace and clear`() {
        assertEquals("12", DialInput("123").backspace().text)
        assertTrue(DialInput.EMPTY.backspace().isEmpty)
        assertTrue(DialInput("123").clear().isEmpty)
    }

    @Test fun `length is capped`() {
        var input = DialInput.EMPTY
        repeat(100) { input = input.press(DialKey.NINE) }
        assertEquals(DialInput.MAX_LENGTH, input.text.length)
    }

    @Test fun `replaceWith sanitises pasted text`() {
        assertEquals("+4930123456", DialInput.EMPTY.replaceWith("tel:+49 (30) 123-456").text)
    }

    @Test fun `dialString is empty when only separators`() {
        assertEquals("", DialInput("+,").dialString())
        assertEquals("+1,2", DialInput("+1,2").dialString())
    }
}
