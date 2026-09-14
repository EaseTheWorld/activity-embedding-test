package com.example.feature.seat

import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.ValueWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates that Choice Items strictly adhere to the Single Source of Truth (SSOT) principle:
 * 1. Item ID ("seat_massage") and Option IDs ("OFF", "WAVE", ...) are declared ONLY ONCE in [SeatCatalog].
 * 2. Hardware layer [SeatVehicleProperties] constructs its ValueMapping directly from Catalog option IDs with ZERO string re-declaration.
 * 3. DI Module [SeatViewModelModule] binds Catalog item to ViewModel using [bindsTo] with ZERO raw strings.
 * 4. Data layer emits pure [ValueWithState] instances (id, isSelected, isEnabled) without UI dependencies.
 * 5. UI layer resolves labels, badges, and icons dynamically via [SeatCatalog.massageMode.getOption].
 */
class ChoiceItemSSOTTest {

    // ========================================================================
    // SSOT Test 1: Zero String Duplication across Layers
    // ========================================================================

    @Test
    fun `Catalog is the Single Source of Truth for Item ID and Option IDs`() {
        val massageItem = SeatCatalog.massageMode

        // 1. Item ID declared once
        assertEquals("seat_massage", massageItem.id)

        // 2. Option IDs declared once
        val expectedOptionIds = listOf("OFF", "WAVE", "LUMBAR", "STRETCH")
        assertEquals(expectedOptionIds, massageItem.optionIds)

        // 3. Hardware ValueMapping automatically maps without duplicate string constants
        val hardwareProp = SeatVehicleProperties.MASSAGE_MODE
        assertEquals(0x11400F00, hardwareProp.propertyId)
        assertEquals(1, hardwareProp.areaId)

        // String (Domain) -> Int (Hardware VHAL)
        assertEquals(0, hardwareProp.mapper.toRaw("OFF"))
        assertEquals(1, hardwareProp.mapper.toRaw("WAVE"))
        assertEquals(2, hardwareProp.mapper.toRaw("LUMBAR"))
        assertEquals(3, hardwareProp.mapper.toRaw("STRETCH"))

        // Int (Hardware VHAL) -> String (Domain)
        assertEquals("OFF", hardwareProp.mapper.toDomain(0))
        assertEquals("WAVE", hardwareProp.mapper.toDomain(1))
        assertEquals("LUMBAR", hardwareProp.mapper.toDomain(2))
        assertEquals("STRETCH", hardwareProp.mapper.toDomain(3))
    }

    // ========================================================================
    // SSOT Test 2: ItemViewModelBinding and Registry Integration
    // ========================================================================

    @Test
    fun `bindsTo DSL binds Choice Item to ChoiceItemViewModel and registers cleanly`() = runTest {
        val hardwareStorage = InMemoryHardwareStorage().apply {
            setInitialValue(SeatVehicleProperties.MASSAGE_MODE, "OFF")
        }
        val drivingRestrictions = MutableStateFlow<Set<String>>(emptySet())

        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)

        // Create bindings via Module
        val bindings = SeatViewModelModule.provideSeatBindings(
            hardwareStorage = hardwareStorage,
            disabledMassageOptionsFlow = drivingRestrictions,
            scope = testScope
        )

        // Registry holds bindings by item.id
        val registry = ItemViewModelRegistry(bindings)

        // Verify lookup by Item object (NO raw string!)
        val choiceVm = registry.getChoiceViewModel<String>(SeatCatalog.massageMode)
        assertNotNull("Choice ViewModel should be registered for SeatCatalog.massageMode", choiceVm)

        // Verify initial state
        assertEquals("OFF", choiceVm!!.valueFlow.value)
    }

    // ========================================================================
    // SSOT Test 3: Dynamic ValueWithState & Driving Lockout
    // ========================================================================

    @Test
    fun `ChoiceItemViewModel produces dynamic ValueWithState list with selection and enablement`() = runTest {
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val hardwareStorage = InMemoryHardwareStorage().apply {
            setInitialValue(SeatVehicleProperties.MASSAGE_MODE, "OFF")
        }
        val drivingRestrictions = MutableStateFlow<Set<String>>(emptySet())

        val registry = ItemViewModelRegistry()
        SeatViewModelBinder.bindAll(
            registry = registry,
            hardwareStorage = hardwareStorage,
            disabledMassageOptionsFlow = drivingRestrictions,
            scope = testScope
        )

        val choiceVm = registry.getMutableChoiceViewModel<String>(SeatCatalog.massageMode)!!

        // 1. Initial State: "OFF" selected, all enabled
        val initialStates = choiceVm.optionStates.value
        assertEquals(4, initialStates.size)

        assertEquals(ValueWithState(id = "OFF", isSelected = true, isEnabled = true), initialStates[0])
        assertEquals(ValueWithState(id = "WAVE", isSelected = false, isEnabled = true), initialStates[1])
        assertEquals(ValueWithState(id = "LUMBAR", isSelected = false, isEnabled = true), initialStates[2])
        assertEquals(ValueWithState(id = "STRETCH", isSelected = false, isEnabled = true), initialStates[3])

        // 2. User selects "WAVE" -> updates hardware storage and option states atomically
        choiceVm.setValue("WAVE")

        assertEquals("WAVE", choiceVm.valueFlow.value)
        val rawAfter = hardwareStorage.observe<Int>(
            SeatVehicleProperties.MASSAGE_MODE.propertyId,
            SeatVehicleProperties.MASSAGE_MODE.areaId
        ).value
        assertEquals(1, rawAfter)

        val waveStates = choiceVm.optionStates.value
        assertFalse(waveStates[0].isSelected) // "OFF" unselected
        assertTrue(waveStates[1].isSelected)  // "WAVE" selected
        assertTrue(waveStates[1].isEnabled)

        // 3. Dynamic Driving Restriction: STRETCH mode disabled during vehicle motion
        drivingRestrictions.value = setOf("STRETCH")
        testScheduler.advanceUntilIdle()

        val restrictedStates = choiceVm.optionStates.value
        val stretchState = restrictedStates.find { it.id == "STRETCH" }!!
        assertFalse("STRETCH should be disabled while driving", stretchState.isEnabled)
        assertFalse("STRETCH should not be selected", stretchState.isSelected)

        // Other options remain enabled
        assertTrue(restrictedStates.find { it.id == "OFF" }!!.isEnabled)
        assertTrue(restrictedStates.find { it.id == "WAVE" }!!.isEnabled)
        assertTrue(restrictedStates.find { it.id == "LUMBAR" }!!.isEnabled)
    }

    // ========================================================================
    // SSOT Test 4: Pure UI Metadata Resolution via Catalog
    // ========================================================================

    @Test
    fun `UI resolves Option presentation metadata from Catalog SSOT using state ID`() {
        val massageItem = SeatCatalog.massageMode

        // Simulate UI receiving ValueWithState list from Data Layer
        val state = ValueWithState(id = "WAVE", isSelected = true, isEnabled = true)

        // UI looks up visual presentation by state.id from item SSOT
        val uiOption = massageItem.getOption(state.id)
        assertNotNull("UiOption must exist for valid state.id", uiOption)
        assertEquals(R.string.massage_wave, uiOption!!.labelRes)
        assertEquals("추천", uiOption.badge)
    }
}
