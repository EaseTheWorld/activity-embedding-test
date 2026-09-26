package com.example.common.ui.settings

import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ParameterizedMutableItemViewModel
import com.example.core.item.SerializedMutableItemViewModel
import com.example.core.item.ValueWithState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ParameterizedItemViewModelTest {

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
    fun `InMemoryItemViewModel with Boolean parses various affirmative and negative strings`() {
        val vm = InMemoryItemViewModel(initialValue = false)

        // 1. "true"
        assertTrue(vm.updateFromParameters(mapOf("value" to "true")))
        assertTrue(vm.valueFlow.value)

        // 2. "false"
        assertTrue(vm.updateFromParameters(mapOf("value" to "false")))
        assertFalse(vm.valueFlow.value)

        // 3. "1" -> true
        assertTrue(vm.updateFromParameters(mapOf("value" to "1")))
        assertTrue(vm.valueFlow.value)

        // 4. "0" -> false
        assertTrue(vm.updateFromParameters(mapOf("value" to "0")))
        assertFalse(vm.valueFlow.value)

        // 5. "on" -> true
        assertTrue(vm.updateFromParameters(mapOf("value" to "ON")))
        assertTrue(vm.valueFlow.value)

        // 6. "off" -> false
        assertTrue(vm.updateFromParameters(mapOf("value" to "off")))
        assertFalse(vm.valueFlow.value)

        // 7. Invalid string returns false and does not change state
        assertFalse(vm.updateFromParameters(mapOf("value" to "invalid_bool")))
        assertFalse(vm.valueFlow.value)
    }

    @Test
    fun `InMemoryItemViewModel with Int parses valid integer`() {
        val vm = InMemoryItemViewModel(initialValue = 10)

        assertTrue(vm.updateFromParameters(mapOf("value" to "25")))
        assertEquals(25, vm.valueFlow.value)

        // Non-number returns false and retains current value
        assertFalse(vm.updateFromParameters(mapOf("value" to "invalid_num")))
        assertEquals(25, vm.valueFlow.value)
    }

    @Test
    fun `LocalStorageItemViewModel persists mutated value asynchronously`() = runTest {
        val testScope = TestScope(testScheduler)
        val vm = LocalStorageItemViewModel(initialValue = false, scope = testScope)

        assertTrue(vm.updateFromParameters(mapOf("value" to "true")))
        testScope.advanceUntilIdle()
        assertTrue(vm.valueFlow.value)
    }

    @Test
    fun `InMemoryChoiceItemViewModel matches exact, underscore, suffix, and case-insensitive strings`() {
        val choiceVm = InMemoryChoiceItemViewModel(
            supportedOptionIds = listOf("OFF", "LEVEL 1", "LEVEL 2", "LEVEL 3"),
            initialSelectedId = "OFF"
        )

        // 1. Underscore match: "LEVEL_2" -> "LEVEL 2"
        assertTrue(choiceVm.updateFromParameters(mapOf("value" to "LEVEL_2")))
        assertEquals("LEVEL 2", choiceVm.valueFlow.value)

        // 2. Suffix number match: "3" -> "LEVEL 3"
        assertTrue(choiceVm.updateFromParameters(mapOf("value" to "3")))
        assertEquals("LEVEL 3", choiceVm.valueFlow.value)

        // 3. Case-insensitive match: "off" -> "OFF"
        assertTrue(choiceVm.updateFromParameters(mapOf("value" to "off")))
        assertEquals("OFF", choiceVm.valueFlow.value)

        // 4. Exact match: "LEVEL 1"
        assertTrue(choiceVm.updateFromParameters(mapOf("value" to "LEVEL 1")))
        assertEquals("LEVEL 1", choiceVm.valueFlow.value)

        // 5. Index match: "2" -> 3rd option ("LEVEL 2")
        assertTrue(choiceVm.updateFromParameters(mapOf("value" to "0")))
        assertEquals("OFF", choiceVm.valueFlow.value)

        // 6. Invalid option returns false
        assertFalse(choiceVm.updateFromParameters(mapOf("value" to "NON_EXISTENT")))
        assertEquals("OFF", choiceVm.valueFlow.value)
    }

    @Test
    fun `SerializedMutableItemViewModel delegates default updateFromParameters to updateFromSerialized`() {
        val serializedVm = MockSerializedViewModel(initial = Coordinate2D(50, 30))

        assertTrue(serializedVm.updateFromParameters(mapOf("value" to "80,70")))
        assertEquals(Coordinate2D(80, 70), serializedVm.valueFlow.value)

        // Invalid format returns false
        assertFalse(serializedVm.updateFromParameters(mapOf("value" to "invalid")))
        assertEquals(Coordinate2D(80, 70), serializedVm.valueFlow.value)

        // Missing "value" key returns false
        assertFalse(serializedVm.updateFromParameters(mapOf("other" to "80,70")))
        assertEquals(Coordinate2D(80, 70), serializedVm.valueFlow.value)
    }

    @Test
    fun `ReadOnly ViewModels do not implement ParameterizedMutableItemViewModel adhering to ISP`() {
        val registry = ItemViewModelRegistry()
        val readOnlyVm = ReadOnlyItemViewModel(MutableStateFlow(50).asStateFlow())
        registry.register("battery_soc", readOnlyVm)

        val vm = registry.getViewModel<Any>("battery_soc")
        val isParameterized = vm is ParameterizedMutableItemViewModel
        assertFalse("ReadOnly ViewModel must not implement ParameterizedMutableItemViewModel", isParameterized)
    }

    @Test
    fun `Custom multi-parameter ViewModel parses multiple keys from Map directly`() {
        class MultiParamVm : MutableItemViewModel<Pair<Int, Int>>, ParameterizedMutableItemViewModel {
            private val _flow = MutableStateFlow(0 to 0)
            override val valueFlow: StateFlow<Pair<Int, Int>> = _flow
            override fun setValue(newValue: Pair<Int, Int>) { _flow.value = newValue }
            override fun updateFromParameters(parameters: Map<String, String>): Boolean {
                val x = parameters["x"]?.toIntOrNull() ?: return false
                val y = parameters["y"]?.toIntOrNull() ?: return false
                setValue(x to y)
                return true
            }
        }

        val multiVm = MultiParamVm()
        assertTrue(multiVm.updateFromParameters(mapOf("x" to "10", "y" to "20")))
        assertEquals(10 to 20, multiVm.valueFlow.value)

        // Missing key returns false
        assertFalse(multiVm.updateFromParameters(mapOf("x" to "10")))
        assertEquals(10 to 20, multiVm.valueFlow.value)
    }
}
