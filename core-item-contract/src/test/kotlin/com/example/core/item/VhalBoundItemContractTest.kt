package com.example.core.item

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class VhalBoundItemContractTest {

    private class TestVhalItem(
        override val key: String = "test_massage",
        override val propertyId: Int = 0x11400F00,
        override val areaId: Int = 1
    ) : VhalBoundItem<String, Int> {

        private val _flow = MutableStateFlow("OFF")
        override val valueFlow: StateFlow<String> = _flow.asStateFlow()

        override fun onValueChanged(newValue: String) {
            _flow.value = newValue
        }

        private val mapping = mapOf(
            "OFF" to 0,
            "WAVE" to 1,
            "LUMBAR" to 2
        )

        override fun toItemValue(vhalValue: Int): String {
            return mapping.entries.firstOrNull { it.value == vhalValue }?.key ?: "OFF"
        }

        override fun toVhalValue(itemValue: String): Int {
            return mapping[itemValue] ?: 0
        }
    }

    @Test
    fun `VhalBoundItem preserves propertyId, areaId and bidirectional conversion`() {
        val item = TestVhalItem()

        assertEquals(0x11400F00, item.propertyId)
        assertEquals(1, item.areaId)

        // VHAL -> Item conversion
        assertEquals("OFF", item.toItemValue(0))
        assertEquals("WAVE", item.toItemValue(1))
        assertEquals("LUMBAR", item.toItemValue(2))
        assertEquals("OFF", item.toItemValue(999)) // fallback

        // Item -> VHAL conversion
        assertEquals(0, item.toVhalValue("OFF"))
        assertEquals(1, item.toVhalValue("WAVE"))
        assertEquals(2, item.toVhalValue("LUMBAR"))
        assertEquals(0, item.toVhalValue("UNKNOWN")) // fallback
    }
}
