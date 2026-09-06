package com.example.feature.seat

import com.example.core.item.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Concrete Unit Tests directly targeting Production Feature code in :feature-seat.
 *
 * This test suite protects against specific, high-risk production bugs:
 * 1. IPC Serialization / Deserialization asymmetry in SeatLumbarSupportItem.
 * 2. Unchecked invalid value injection into DriverSeatHeatingItem.
 * 3. Key collisions or ordering regressions in SeatItemRegistry SSOT.
 */
class SeatFeatureLogicTest {

    // ========================================================================
    // Protection 1: IPC Round-Trip Symmetry & Format Regression
    // ========================================================================

    @Test
    fun `SeatLumbarSupportItem preserves exact state across IPC serialization and deserialization round-trip`() {
        val item = SeatLumbarSupportItem()

        // 1. Initial State Check
        assertEquals(50, item.valueFlow.value.heightPercent)
        assertEquals(30, item.valueFlow.value.depthPercent)
        assertEquals("50,30", item.serializedValue)

        // 2. State Mutation
        item.onValueChanged(SeatLumbarSupport(heightPercent = 85, depthPercent = 70))
        val wirePayload = item.serializedValue

        // Regression Guard: If someone changes the format delimiter from ',' to ':' or swaps height/depth,
        // this test catches it before breaking remote ContentProvider IPC consumers.
        assertEquals("85,70", wirePayload)

        // 3. Round-trip Deserialization simulation (remote client sending back mutated value via ContentProvider update)
        val consumerItem = SeatLumbarSupportItem()
        consumerItem.updateFromSerialized(wirePayload)

        assertEquals(85, consumerItem.valueFlow.value.heightPercent)
        assertEquals(70, consumerItem.valueFlow.value.depthPercent)
    }

    @Test
    fun `SeatLumbarSupportItem gracefully falls back to defaults on corrupt IPC payloads`() {
        val item = SeatLumbarSupportItem()

        // Corrupt payloads from malformed IPC calls
        item.updateFromSerialized("invalid,payload")
        assertEquals(50, item.valueFlow.value.heightPercent)
        assertEquals(30, item.valueFlow.value.depthPercent)

        item.updateFromSerialized("not_enough_parts")
        assertEquals(50, item.valueFlow.value.heightPercent)
        assertEquals(30, item.valueFlow.value.depthPercent)
    }

    // ========================================================================
    // Protection 2: Domain Boundary Protection (Preventing Invalid State)
    // ========================================================================

    @Test
    fun `DriverSeatHeatingItem rejects options outside defined domain range`() {
        val heating = DriverSeatHeatingItem()

        // Verify initial state
        assertEquals("OFF", heating.valueFlow.value)
        assertEquals(listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"), heating.options)

        // Valid domain transitions
        heating.onValueChanged("LEVEL 1")
        assertEquals("LEVEL 1", heating.valueFlow.value)

        heating.onValueChanged("LEVEL 3")
        assertEquals("LEVEL 3", heating.valueFlow.value)

        // Regression Guard: If a developer refactors onValueChanged and accidentally removes
        // `if (options.contains(newValue))`, invalid VHAL inputs could poison the system.
        heating.onValueChanged("LEVEL 4") // Exceeds max hardware setting
        assertEquals("LEVEL 3", heating.valueFlow.value) // Must remain LEVEL 3!

        heating.onValueChanged("MAX_HEAT")
        assertEquals("LEVEL 3", heating.valueFlow.value)

        heating.onValueChanged("")
        assertEquals("LEVEL 3", heating.valueFlow.value)
    }

    // ========================================================================
    // Protection 3: Registry SSOT Integrity & Duplicate Key Protection
    // ========================================================================

    @Test
    fun `SeatItemRegistry enforces unique item keys and complete SSOT registration`() {
        val items = SeatItemRegistry.items

        // Regression Guard: ContentProvider queries and Database index mappings require unique keys.
        // If a copy-paste error duplicates an item key, ContentProvider query responses corrupt.
        val keys = items.map { it.key }
        val uniqueKeys = keys.toSet()
        assertEquals("Every setting item in SeatItemRegistry must have a distinct unique key", uniqueKeys.size, keys.size)

        // Verify all items exist in registry
        assertNotNull(SeatItemRegistry.findItem("driver_seat_heat"))
        assertNotNull(SeatItemRegistry.findItem("driver_seat_vent"))
        assertNotNull(SeatItemRegistry.findItem("seat_massage"))
        assertNotNull(SeatItemRegistry.findItem("passenger_seat_heat"))
        assertNotNull(SeatItemRegistry.findItem("easy_entry_exit"))
        assertNotNull(SeatItemRegistry.findItem("seat_lumbar"))

        // Verify correct polymorphic ItemType derivation
        assertEquals(ItemType.CHOICE, SeatItemRegistry.driverSeatHeat.type)
        assertEquals(ItemType.CHOICE, SeatItemRegistry.driverSeatVent.type)
        assertEquals(ItemType.CHOICE, SeatItemRegistry.seatMassage.type)
        assertEquals(ItemType.CHOICE, SeatItemRegistry.passengerSeatHeat.type)
        assertEquals(ItemType.TOGGLE, SeatItemRegistry.easyEntryExit.type)
        assertEquals(ItemType.CUSTOM, SeatItemRegistry.seatLumbar.type)
    }

    // ========================================================================
    // Protection 4: UiItem Presentation Metadata & Value-to-String Mappings
    // ========================================================================

    @Test
    fun `DriverSeatHeatingItem provides compile-time safe UI metadata and dynamic value-to-string mappings`() {
        val heating = DriverSeatHeatingItem()

        // 1. Resource bindings
        assertEquals(R.string.seat_item_driver_heat_title, heating.titleRes)
        assertEquals(R.string.seat_item_driver_heat_subtitle, heating.subtitleRes)
        assertEquals(R.drawable.ic_feature_seat, heating.iconRes)

        // 2. Choice options SSOT & auto-derived options list
        assertEquals(4, heating.choiceOptions.size)
        assertEquals(listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"), heating.options)

        // 3. Value -> String mapping verification via getValueVisual / getValueTextRes
        assertEquals(R.string.seat_heat_off, heating.getValueTextRes("OFF"))
        assertEquals(R.string.seat_heat_level_1, heating.getValueTextRes("LEVEL 1"))
        assertEquals(R.string.seat_heat_level_2, heating.getValueTextRes("LEVEL 2"))
        assertEquals(R.string.seat_heat_level_3, heating.getValueTextRes("LEVEL 3"))
        org.junit.Assert.assertNull(heating.getValueTextRes("UNKNOWN_LEVEL"))
    }

    // ========================================================================
    // Protection 5: VHAL Bound Contract & Bidirectional Conversion
    // ========================================================================

    @Test
    fun `Seat items implement VhalBoundItem with correct property IDs and bidirectional value mapping`() {
        val heating = DriverSeatHeatingItem()
        val massage = SeatMassageModeItem()

        // 1. Property and Area ID correctness
        assertEquals(0x11400503, heating.propertyId)
        assertEquals(1, heating.areaId)

        assertEquals(0x11400F00, massage.propertyId)
        assertEquals(1, massage.areaId)

        // 2. Bidirectional Mapping for Massage (String <-> Int)
        assertEquals(0, massage.toVhalValue("OFF"))
        assertEquals(1, massage.toVhalValue("WAVE"))
        assertEquals(2, massage.toVhalValue("LUMBAR"))
        assertEquals(3, massage.toVhalValue("STRETCH"))

        assertEquals("OFF", massage.toItemValue(0))
        assertEquals("WAVE", massage.toItemValue(1))
        assertEquals("LUMBAR", massage.toItemValue(2))
        assertEquals("STRETCH", massage.toItemValue(3))
    }

    @Test
    fun `SeatPropertyRepository connects seamlessly with VhalBoundItem without breaking domain invariants`() {
        val repository = SeatPropertyRepositoryImpl()
        val massage = SeatMassageModeItem(repository)
        val heating = DriverSeatHeatingItem(repository)

        // 1. Initial State
        assertEquals("OFF", massage.valueFlow.value)
        assertEquals("OFF", heating.valueFlow.value)

        // 2. UI Action Flow (User clicks UI -> Item calls repository.setProperty(this, "WAVE"))
        massage.onValueChanged("WAVE")
        assertEquals("WAVE", massage.valueFlow.value)

        // 3. Hardware Event Flow (Vehicle ECU / Knob sends raw VHAL event -> Repository translates & emits)
        // Massage hardware sends raw 3 (STRETCH)
        repository.onVhalHardwareEvent(propertyId = 0x11400F00, areaId = 1, rawHardwareValue = 3)
        assertEquals("STRETCH", massage.valueFlow.value)

        // Heating hardware sends raw 2 (LEVEL 2)
        repository.onVhalHardwareEvent(propertyId = 0x11400503, areaId = 1, rawHardwareValue = 2)
        assertEquals("LEVEL 2", heating.valueFlow.value)

        // Heating hardware sends raw 0 (OFF)
        repository.onVhalHardwareEvent(propertyId = 0x11400503, areaId = 1, rawHardwareValue = 0)
        assertEquals("OFF", heating.valueFlow.value)
    }
}
