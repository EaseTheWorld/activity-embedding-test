package com.example.core.item

import org.junit.Assert.assertEquals
import org.junit.Test

class VhalBoundItemContractTest {

    @Test
    fun `VhalBinding data class encapsulates hardware IDs and bidirectional conversion cleanly`() {
        val mapping = mapOf(
            "OFF" to 0,
            "WAVE" to 1,
            "LUMBAR" to 2
        )

        // Pure immutable composition object
        val binding = VhalBinding<String, Int>(
            propertyId = 0x11400F00,
            areaId = 1,
            toItemValue = { vhal -> mapping.entries.firstOrNull { it.value == vhal }?.key ?: "OFF" },
            toVhalValue = { item -> mapping[item] ?: 0 }
        )

        assertEquals(0x11400F00, binding.propertyId)
        assertEquals(1, binding.areaId)

        // VHAL -> Item conversion
        assertEquals("OFF", binding.toItemValue(0))
        assertEquals("WAVE", binding.toItemValue(1))
        assertEquals("LUMBAR", binding.toItemValue(2))
        assertEquals("OFF", binding.toItemValue(999)) // fallback

        // Item -> VHAL conversion
        assertEquals(0, binding.toVhalValue("OFF"))
        assertEquals(1, binding.toVhalValue("WAVE"))
        assertEquals(2, binding.toVhalValue("LUMBAR"))
        assertEquals(0, binding.toVhalValue("UNKNOWN")) // fallback
    }
}
