package app.minimaldialer.settings

import app.minimaldialer.recording.domain.CapabilityPolicy
import app.minimaldialer.recording.domain.DeviceFacts
import app.minimaldialer.recording.domain.Limitation
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityTextTest {
    @Test fun `every limitation is explained`() {
        Limitation.entries.forEach { assertTrue(CapabilityText.limitation(it).length > 20) }
    }

    @Test fun `ordinary install never claims two-way recording`() {
        val cap = CapabilityPolicy.evaluate(DeviceFacts(34, "Google", "Pixel 8", false, true, true))
        assertTrue(CapabilityText.headline(cap).contains("not available"))
    }
}
