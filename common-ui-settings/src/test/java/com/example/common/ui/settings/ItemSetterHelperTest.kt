package com.example.common.ui.settings

import com.example.core.item.ChoiceItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.SerializedMutableItemViewModel
import com.example.core.item.ValueWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemSetterHelperTest {

    private class MockToggleViewModel(
        initial: Boolean = false
    ) : MutableItemViewModel<Boolean> {
        private val _flow = MutableStateFlow(initial)
        override val valueFlow: StateFlow<Boolean> = _flow.asStateFlow()
        override fun setValue(newValue: Boolean) {
            _flow.value = newValue
        }
    }

    private class MockChoiceViewModel(
        initial: String,
        options: List<String>
    ) : MutableChoiceItemViewModel<String> {
        private val _flow = MutableStateFlow(initial)
        override val valueFlow: StateFlow<String> = _flow.asStateFlow()

        private val _optionStates = MutableStateFlow(
            options.map { ValueWithState(it, isSelected = it == initial, isEnabled = true) }
        )
        override val optionStates: StateFlow<List<ValueWithState<String>>> = _optionStates.asStateFlow()

        override fun setValue(newValue: String) {
            _flow.value = newValue
            _optionStates.value = _optionStates.value.map {
                it.copy(isSelected = it.id == newValue)
            }
        }
    }

    private class MockIntViewModel(
        initial: Int = 0
    ) : MutableItemViewModel<Int> {
        private val _flow = MutableStateFlow(initial)
        override val valueFlow: StateFlow<Int> = _flow.asStateFlow()
        override fun setValue(newValue: Int) {
            _flow.value = newValue
        }
    }

    private data class Coordinate2D(val x: Int, val y: Int)

    private class MockSerializedViewModel(
        initial: Coordinate2D = Coordinate2D(0, 0)
    ) : MutableItemViewModel<Coordinate2D>, SerializedMutableItemViewModel {
        private val _flow = MutableStateFlow(initial)
        override val valueFlow: StateFlow<Coordinate2D> = _flow.asStateFlow()
        override fun setValue(newValue: Coordinate2D) {
            _flow.value = newValue
        }

        override fun updateFromSerialized(raw: String): Boolean {
            val parts = raw.split(",")
            if (parts.size == 2) {
                val x = parts[0].trim().toIntOrNull() ?: return false
                val y = parts[1].trim().toIntOrNull() ?: return false
                setValue(Coordinate2D(x, y))
                return true
            }
            return false
        }
    }

    @Test
    fun `applyValue on boolean toggle parses various affirmative and negative strings`() {
        val registry = ItemViewModelRegistry()
        val toggleVm = MockToggleViewModel(initial = false)
        registry.register("auto_lock", toggleVm)

        // 1. "true"
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", "true"))
        assertTrue(toggleVm.valueFlow.value)

        // 2. "false"
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", "false"))
        assertFalse(toggleVm.valueFlow.value)

        // 3. "1" -> true
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", "1"))
        assertTrue(toggleVm.valueFlow.value)

        // 4. "0" -> false
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", "0"))
        assertFalse(toggleVm.valueFlow.value)

        // 5. "on" -> true
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", "ON"))
        assertTrue(toggleVm.valueFlow.value)

        // 6. "off" -> false
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", "off"))
        assertFalse(toggleVm.valueFlow.value)
    }

    @Test
    fun `applyValue on choice item matches exact, underscore, suffix, and case-insensitive strings`() {
        val registry = ItemViewModelRegistry()
        val choiceVm = MockChoiceViewModel(
            initial = "OFF",
            options = listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3")
        )
        registry.register("driver_seat_heat", choiceVm)

        // 1. Underscore match: "LEVEL_2" -> "LEVEL 2"
        assertTrue(ItemSetterHelper.applyValue(registry, "driver_seat_heat", "LEVEL_2"))
        assertEquals("LEVEL 2", choiceVm.valueFlow.value)

        // 2. Suffix number match: "3" -> "LEVEL 3"
        assertTrue(ItemSetterHelper.applyValue(registry, "driver_seat_heat", "3"))
        assertEquals("LEVEL 3", choiceVm.valueFlow.value)

        // 3. Case-insensitive match: "off" -> "OFF"
        assertTrue(ItemSetterHelper.applyValue(registry, "driver_seat_heat", "off"))
        assertEquals("OFF", choiceVm.valueFlow.value)

        // 4. Exact match: "LEVEL 1"
        assertTrue(ItemSetterHelper.applyValue(registry, "driver_seat_heat", "LEVEL 1"))
        assertEquals("LEVEL 1", choiceVm.valueFlow.value)
    }

    @Test
    fun `applyValue on integer slider parses valid integer`() {
        val registry = ItemViewModelRegistry()
        val intVm = MockIntViewModel(initial = 10)
        registry.register("volume", intVm)

        assertTrue(ItemSetterHelper.applyValue(registry, "volume", "25"))
        assertEquals(25, intVm.valueFlow.value)

        // Non-number returns false
        assertFalse(ItemSetterHelper.applyValue(registry, "volume", "invalid_num"))
        assertEquals(25, intVm.valueFlow.value)
    }

    @Test
    fun `applyValue on SerializedMutableItemViewModel delegates to updateFromSerialized`() {
        val registry = ItemViewModelRegistry()
        val serializedVm = MockSerializedViewModel(initial = Coordinate2D(50, 30))
        registry.register("seat_lumbar", serializedVm)

        assertTrue(ItemSetterHelper.applyValue(registry, "seat_lumbar", "80,70"))
        assertEquals(Coordinate2D(80, 70), serializedVm.valueFlow.value)

        // Invalid format returns false
        assertFalse(ItemSetterHelper.applyValue(registry, "seat_lumbar", "invalid"))
        assertEquals(Coordinate2D(80, 70), serializedVm.valueFlow.value)
    }

    @Test
    fun `applyValue rejects mutation on read-only ViewModels adhering to ISP`() {
        val registry = ItemViewModelRegistry()
        val readOnlyVm = ReadOnlyItemViewModel(MutableStateFlow(50).asStateFlow())
        registry.register("battery_soc", readOnlyVm)

        val result = ItemSetterHelper.applyValue(registry, "battery_soc", "90")
        assertFalse("ReadOnly ViewModel must not accept setter mutations", result)
        assertEquals(50, readOnlyVm.valueFlow.value)
    }

    @Test
    fun `applyValue returns false for unregistered item ID`() {
        val registry = ItemViewModelRegistry()
        val result = ItemSetterHelper.applyValue(registry, "non_existent", "true")
        assertFalse(result)
    }

    @Test
    fun `applyValue handles strongly typed objects directly from Intent Extras`() {
        val registry = ItemViewModelRegistry()
        val toggleVm = MockToggleViewModel(initial = false)
        val intVm = MockIntViewModel(initial = 0)
        val choiceVm = MockChoiceViewModel(initial = "OFF", options = listOf("OFF", "LEVEL 1", "LEVEL 2"))

        registry.register("auto_lock", toggleVm)
        registry.register("volume", intVm)
        registry.register("driver_seat_heat", choiceVm)

        // Boolean direct (e.g. from intent.getBooleanExtra or --ez value true/false)
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", true))
        assertTrue(toggleVm.valueFlow.value)
        assertTrue(ItemSetterHelper.applyValue(registry, "auto_lock", false))
        assertFalse(toggleVm.valueFlow.value)

        // Int direct (e.g. from intent.getIntExtra or --ei value 42)
        assertTrue(ItemSetterHelper.applyValue(registry, "volume", 42))
        assertEquals(42, intVm.valueFlow.value)

        // String choice
        assertTrue(ItemSetterHelper.applyValue(registry, "driver_seat_heat", "LEVEL_2"))
        assertEquals("LEVEL 2", choiceVm.valueFlow.value)
    }

    @Test
    fun `parseValue and applyValue work directly on ViewModel without registry`() {
        val toggleVm = MockToggleViewModel(initial = false)
        val choiceVm = MockChoiceViewModel(initial = "OFF", options = listOf("OFF", "LEVEL 1", "LEVEL 2"))

        // parseValue directly on toggleVm
        assertEquals(true, ItemSetterHelper.parseValue(toggleVm, "true"))
        assertEquals(true, ItemSetterHelper.parseValue(toggleVm, "1"))
        assertEquals(false, ItemSetterHelper.parseValue(toggleVm, "off"))

        // parseValue directly on choiceVm
        assertEquals("LEVEL 2", ItemSetterHelper.parseValue(choiceVm, "LEVEL_2"))
        assertEquals("LEVEL 1", ItemSetterHelper.parseValue(choiceVm, "1"))

        // applyValue directly on ViewModel without registry
        assertTrue(ItemSetterHelper.applyValue(toggleVm, "true"))
        assertTrue(toggleVm.valueFlow.value)

        assertTrue(ItemSetterHelper.applyValue(choiceVm, "LEVEL_2"))
        assertEquals("LEVEL 2", choiceVm.valueFlow.value)
    }
}
