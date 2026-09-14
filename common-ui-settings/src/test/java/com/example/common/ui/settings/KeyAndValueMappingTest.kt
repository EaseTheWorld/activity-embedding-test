package com.example.common.ui.settings

import com.example.core.item.HardwareKey
import com.example.core.item.PropertyBinding
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.ValueMapping
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyAndValueMappingTest {

    private val PROP_AUTO_LOCK = 0x11400bc0
    private val PROP_CHILD_LOCK = 0x11400bc1

    @Test
    fun `Key Mapping isolates hardware properties by propertyId`() = runTest {
        val testScope = TestScope(testScheduler)
        val storage = InMemoryHardwareStorage().apply {
            setInitialValue(PROP_AUTO_LOCK, 0, 0)
            setInitialValue(PROP_CHILD_LOCK, 0, 0)
        }
        val registry = ItemViewModelRegistry()

        // Key Mapping:
        // "auto_lock"  -> PROP_AUTO_LOCK  (0x11400bc0)
        // "child_lock" -> PROP_CHILD_LOCK (0x11400bc1)
        registry.bindHardware(
            itemId = "auto_lock",
            propertyId = PROP_AUTO_LOCK,
            valueMapping = ValueMapping.BooleanToInt,
            storage = storage,
            scope = testScope
        )
        registry.bindHardware(
            itemId = "child_lock",
            propertyId = PROP_CHILD_LOCK,
            valueMapping = ValueMapping.BooleanToInt,
            storage = storage,
            scope = testScope
        )

        // Mutate auto_lock from UI
        val autoLockViewModel = registry.getMutableViewModel<Boolean>("auto_lock")!!
        autoLockViewModel.setValue(true)
        testScope.advanceUntilIdle()

        // Key Mapping verification:
        // Only PROP_AUTO_LOCK was changed in hardware storage; PROP_CHILD_LOCK remains 0
        assertEquals(1, storage.observe<Int>(PROP_AUTO_LOCK).value)
        assertEquals(0, storage.observe<Int>(PROP_CHILD_LOCK).value)
    }

    @Test
    fun `Value Mapping converts UI domain boolean to raw hardware integer on write`() = runTest {
        val testScope = TestScope(testScheduler)
        val storage = InMemoryHardwareStorage().apply {
            setInitialValue(PROP_AUTO_LOCK, 0, 0)
        }
        val registry = ItemViewModelRegistry()

        registry.bindHardware(
            itemId = "auto_lock",
            propertyId = PROP_AUTO_LOCK,
            valueMapping = ValueMapping.BooleanToInt,
            storage = storage,
            scope = testScope
        )

        val viewModel = registry.getMutableViewModel<Boolean>("auto_lock")!!

        // Initially domain is false, raw is 0
        assertFalse(viewModel.valueFlow.value)
        assertEquals(0, storage.observe<Int>(PROP_AUTO_LOCK).value)

        // UI sets true -> ValueMapping converts true to raw integer 1
        viewModel.setValue(true)
        testScope.advanceUntilIdle()

        assertTrue(viewModel.valueFlow.value)
        assertEquals(1, storage.observe<Int>(PROP_AUTO_LOCK).value)
    }

    @Test
    fun `Value Mapping converts incoming raw hardware event to UI domain boolean`() = runTest {
        val testScope = TestScope(testScheduler)
        val storage = InMemoryHardwareStorage().apply {
            setInitialValue(PROP_AUTO_LOCK, 0, 0)
        }
        val registry = ItemViewModelRegistry()

        registry.bindHardware(
            itemId = "auto_lock",
            propertyId = PROP_AUTO_LOCK,
            valueMapping = ValueMapping.BooleanToInt,
            storage = storage,
            scope = testScope
        )

        val viewModel = registry.getViewModel<Boolean>("auto_lock")!!
        assertFalse(viewModel.valueFlow.value)

        // Simulate external ECU / VHAL event writing raw integer 1
        storage.simulateHardwareEvent(PROP_AUTO_LOCK, areaId = 0, rawValue = 1)
        testScope.advanceUntilIdle()

        // UI domain flow receives mapped boolean true!
        assertTrue(viewModel.valueFlow.value)
    }

    @Test
    fun `KeyValue binding maps logical itemId to preference key and converts value`() = runTest {
        val testScope = TestScope(testScheduler)
        val registry = ItemViewModelRegistry()
        val persistedMap = mutableMapOf<String, String>()

        // Key Mapping: "unlock_on_park" -> "pref_unlock_on_park_v2"
        // Value Mapping: Boolean -> String ("true" / "false")
        registry.bindStorage(
            itemId = "unlock_on_park",
            storageKey = "pref_unlock_on_park_v2",
            valueMapping = ValueMapping.BooleanToString,
            initialRawValue = "false",
            scope = testScope,
            onPersist = { key, rawValue ->
                persistedMap[key] = rawValue
            }
        )

        val viewModel = registry.getMutableViewModel<Boolean>("unlock_on_park")!!
        assertFalse(viewModel.valueFlow.value)

        viewModel.setValue(true)
        testScope.advanceUntilIdle()

        assertTrue(viewModel.valueFlow.value)
        assertEquals("true", persistedMap["pref_unlock_on_park_v2"])
    }

    @Test
    fun `Dual Context (Recent Grid vs Category List) shares mapped hardware state`() = runTest {
        val testScope = TestScope(testScheduler)
        val storage = InMemoryHardwareStorage().apply {
            setInitialValue(PROP_AUTO_LOCK, 0, 0)
        }
        val registry = ItemViewModelRegistry()

        registry.bindHardware(
            itemId = "auto_lock",
            propertyId = PROP_AUTO_LOCK,
            valueMapping = ValueMapping.BooleanToInt,
            storage = storage,
            scope = testScope
        )

        // Category Screen (List) observes auto_lock
        val categoryFlow = registry.getViewModel<Boolean>("auto_lock")!!.valueFlow

        // Recent Screen (Grid) accesses the same handler by key "auto_lock"
        val recentViewModel = registry.getMutableViewModel<Boolean>("auto_lock")!!

        // User clicks toggle in Recent Grid Card
        recentViewModel.setValue(true)
        testScope.advanceUntilIdle()

        // 1. Hardware storage receives mapped raw integer 1
        assertEquals(1, storage.observe<Int>(PROP_AUTO_LOCK).value)

        // 2. Both Category Screen and Recent Screen show true in real time
        assertTrue(recentViewModel.valueFlow.value)
        assertTrue(categoryFlow.value)
    }
}
