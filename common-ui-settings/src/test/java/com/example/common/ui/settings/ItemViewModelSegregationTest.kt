package com.example.common.ui.settings

import com.example.core.item.ChoiceItemViewModel
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ValueMapping
import com.example.core.item.ValueWithState
import com.example.core.item.VehicleProperty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Interface Segregation Principle (ISP) architectural contract:
 * - [ItemViewModel] provides read-only observation ([ItemViewModel.valueFlow]) for telemetry / gauges.
 * - [MutableItemViewModel] provides write capability ([MutableItemViewModel.setValue]) for interactive controls.
 * - [ChoiceItemViewModel] provides per-option states ([ChoiceItemViewModel.optionStates]) for multi-option selection.
 * - [MutableChoiceItemViewModel] combines [ChoiceItemViewModel] and [MutableItemViewModel].
 * - [ItemViewModelRegistry] cleanly separates read-only retrieval from mutable retrieval.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModelSegregationTest {

    @Test
    fun `ReadOnlyItemViewModel exposes valueFlow but cannot be cast or retrieved as MutableItemViewModel`() {
        val telemetryFlow = MutableStateFlow(72) // e.g. Battery State of Charge %
        val readOnlyVm: ItemViewModel<Int> = ReadOnlyItemViewModel(telemetryFlow.asStateFlow())

        val registry = ItemViewModelRegistry()
        registry.register("battery_soc", readOnlyVm)

        // 1. Can be retrieved as read-only ItemViewModel
        val observedVm = registry.getViewModel<Int>("battery_soc")
        assertNotNull(observedVm)
        assertEquals(72, observedVm!!.valueFlow.value)

        // 2. Registry getMutableViewModel returns null for read-only ViewModel
        val mutableVm = registry.getMutableViewModel<Int>("battery_soc")
        assertNull("ReadOnlyItemViewModel must NOT be returned as MutableItemViewModel", mutableVm)

        // 3. isMutable guard evaluates to false
        val isMutable = observedVm is MutableItemViewModel
        assertFalse(isMutable)

        // 4. Updating the upstream flow reflects in the read-only ViewModel
        telemetryFlow.value = 85
        assertEquals(85, observedVm.valueFlow.value)
    }

    @Test
    fun `ReadOnlyHardwareItemViewModel observes HardwarePropertyStorage without write capability`() = runTest {
        val testScope = TestScope(testScheduler)
        val storage = InMemoryHardwareStorage()
        val speedProperty = VehicleProperty<Int, Int>(
            propertyId = 0x1101,
            areaId = 0,
            mapper = ValueMapping.identity()
        )
        storage.setInitialValue(speedProperty, 60)

        val readOnlyHwVm = ReadOnlyHardwareItemViewModel(speedProperty, storage)
        val registry = ItemViewModelRegistry()
        registry.register("vehicle_speed", readOnlyHwVm)

        // 1. Observable as ItemViewModel
        val speedVm = registry.getViewModel<Int>("vehicle_speed")
        assertNotNull(speedVm)
        assertEquals(60, speedVm!!.valueFlow.value)

        // 2. Not mutable
        assertNull(registry.getMutableViewModel<Int>("vehicle_speed"))
        assertFalse(speedVm is MutableItemViewModel)

        // 3. Low-level storage hardware change propagates reactively
        storage.write(speedProperty, 100)
        testScope.advanceUntilIdle()
        assertEquals(100, speedVm.valueFlow.value)
    }

    @Test
    fun `MutableItemViewModel allows both observation and mutation`() = runTest {
        val testScope = TestScope(testScheduler)
        val mutableVm = LocalStorageItemViewModel(initialValue = false, scope = testScope)

        val registry = ItemViewModelRegistry()
        registry.register("auto_lock", mutableVm)

        // 1. Can be retrieved as ItemViewModel
        val observedVm = registry.getViewModel<Boolean>("auto_lock")
        assertNotNull(observedVm)
        assertEquals(false, observedVm!!.valueFlow.value)

        // 2. Can be retrieved as MutableItemViewModel
        val writableVm = registry.getMutableViewModel<Boolean>("auto_lock")
        assertNotNull(writableVm)
        assertTrue(observedVm is MutableItemViewModel)

        // 3. Mutation propagates
        writableVm!!.setValue(true)
        testScope.advanceUntilIdle()
        assertEquals(true, writableVm.valueFlow.value)
        assertEquals(true, observedVm.valueFlow.value)
    }

    @Test
    fun `ReadOnly Choice ViewModel exposes valueFlow and optionStates but cannot mutate`() {
        // Passive display Choice ViewModel (e.g. Current Driving Mode gauge on cluster)
        val readOnlyChoiceVm = object : ChoiceItemViewModel<String> {
            override val valueFlow: StateFlow<String> = MutableStateFlow("SPORT")
            override val optionStates: StateFlow<List<ValueWithState<String>>> = MutableStateFlow(
                listOf(
                    ValueWithState("ECO", isSelected = false, isEnabled = true),
                    ValueWithState("SPORT", isSelected = true, isEnabled = true)
                )
            )
        }

        val registry = ItemViewModelRegistry()
        registry.register("drive_mode_gauge", readOnlyChoiceVm)

        // 1. Can be retrieved as ChoiceItemViewModel<String> and ItemViewModel<String>
        val choiceVm = registry.getChoiceViewModel<String>("drive_mode_gauge")
        assertNotNull(choiceVm)
        assertEquals("SPORT", choiceVm!!.valueFlow.value)
        assertEquals("SPORT", choiceVm.selectedValue)
        assertEquals(2, choiceVm.optionStates.value.size)

        // 2. getMutableViewModel & getMutableChoiceViewModel return null
        assertNull(registry.getMutableViewModel<String>("drive_mode_gauge"))
        assertNull(registry.getMutableChoiceViewModel<String>("drive_mode_gauge"))
        assertFalse(choiceVm is MutableItemViewModel<*>)
    }

    @Test
    fun `Mutable Choice ViewModel allows dynamic optionStates and user mutation via setValue`() = runTest {
        val testScope = TestScope(testScheduler)
        val mutableChoiceVm = InMemoryChoiceItemViewModel(
            supportedOptionIds = listOf("OFF", "WAVE", "LUMBAR"),
            initialSelectedId = "OFF",
            scope = testScope
        )

        val registry = ItemViewModelRegistry()
        registry.register("massage_mode", mutableChoiceVm)

        val writableChoiceVm = registry.getMutableChoiceViewModel<String>("massage_mode")
        assertNotNull(writableChoiceVm)
        assertEquals("OFF", writableChoiceVm!!.valueFlow.value)
        assertEquals("OFF", writableChoiceVm.selectedValue)

        // Mutate
        writableChoiceVm.setValue("WAVE")
        testScope.advanceUntilIdle()

        assertEquals("WAVE", writableChoiceVm.valueFlow.value)
        assertEquals("WAVE", writableChoiceVm.selectedValue)
        assertTrue(writableChoiceVm.optionStates.value.first { it.id == "WAVE" }.isSelected)
    }
}
