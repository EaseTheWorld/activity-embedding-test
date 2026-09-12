package com.example.core.item

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueMappingTest {

    @Test
    fun `ValueMapping BooleanToInt converts bidirectionally`() {
        val mapping = ValueMapping.BooleanToInt

        // UI Domain to Hardware Raw
        assertEquals(1, mapping.toRaw(true))
        assertEquals(0, mapping.toRaw(false))

        // Hardware Raw to UI Domain
        assertTrue(mapping.toDomain(1))
        assertFalse(mapping.toDomain(0))
    }

    @Test
    fun `ValueMapping BooleanToString converts bidirectionally`() {
        val mapping = ValueMapping.BooleanToString

        assertEquals("true", mapping.toRaw(true))
        assertEquals("false", mapping.toRaw(false))

        assertTrue(mapping.toDomain("true"))
        assertTrue(mapping.toDomain("TRUE"))
        assertFalse(mapping.toDomain("false"))
    }

    @Test
    fun `PropertyBinding encapsulates both key mapping and value mapping`() {
        val binding = PropertyBinding(
            itemId = "auto_door_lock",
            storageKey = HardwareKey(propertyId = 0x11400bc0, areaId = 0),
            valueMapping = ValueMapping.BooleanToInt
        )

        // Key Mapping
        assertEquals("auto_door_lock", binding.itemId)
        assertEquals(0x11400bc0, binding.storageKey.propertyId)
        assertEquals(0, binding.storageKey.areaId)

        // Value Mapping
        assertEquals(1, binding.valueMapping.toRaw(true))
        assertTrue(binding.valueMapping.toDomain(1))
    }
}
