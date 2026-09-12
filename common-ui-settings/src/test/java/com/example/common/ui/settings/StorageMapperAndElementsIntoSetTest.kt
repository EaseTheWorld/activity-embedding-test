package com.example.common.ui.settings

import com.example.core.item.Item
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.SettingCatalog
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.ValueMapping
import com.example.core.item.VehicleProperty
import com.example.core.item.bindsTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageMapperAndElementsIntoSetTest {

    private val PROP_CHILD_LOCK = VehicleProperty(
        propertyId = 0x11400bc1,
        mapper = ValueMapping.BooleanToInt
    )

    @Test
    fun `Storage handles ValueMapping internally for observe and write`() = runTest {
        val storage = InMemoryHardwareStorage()
        storage.setInitialValue(PROP_CHILD_LOCK, domainValue = false)

        // 1. Observe domain type directly from storage (Mapper is inside Storage!)
        val domainFlow = storage.observe(PROP_CHILD_LOCK)
        assertFalse(domainFlow.value)
        assertEquals(0, storage.observe<Int>(PROP_CHILD_LOCK.propertyId).value)

        // 2. Storage write handles domain -> raw conversion internally
        storage.write(PROP_CHILD_LOCK, true)
        assertTrue(domainFlow.value)
        assertEquals(1, storage.observe<Int>(PROP_CHILD_LOCK.propertyId).value)

        // 3. Simulated hardware raw event is converted to domain automatically
        storage.simulateHardwareEvent(PROP_CHILD_LOCK.propertyId, areaId = 0, rawValue = 0)
        assertFalse(domainFlow.value)
    }

    @Test
    fun `HardwareItemViewModel delegates to Storage without holding manual mapper`() = runTest {
        val testScope = TestScope(testScheduler)
        val storage = InMemoryHardwareStorage().apply {
            setInitialValue(PROP_CHILD_LOCK, domainValue = false)
        }

        val viewModel = HardwareItemViewModel(
            property = PROP_CHILD_LOCK,
            storage = storage,
            scope = testScope
        )

        assertFalse(viewModel.valueFlow.value)

        // UI toggles value
        viewModel.setValue(true)
        testScope.advanceUntilIdle()

        assertTrue(viewModel.valueFlow.value)
        assertEquals(1, storage.observe<Int>(PROP_CHILD_LOCK.propertyId).value)
    }

    @Test
    fun `bindsTo DSL creates ItemViewModelBinding with ZERO raw strings`() {
        val testToggle = UiToggleItem(id = "auto_lock", nameResId = 1)
        val dummyViewModel = InMemoryItemViewModel(true)

        // Declarative infix bindsTo
        val binding: ItemViewModelBinding<Boolean> = testToggle bindsTo dummyViewModel

        assertEquals(testToggle, binding.item)
        assertEquals("auto_lock", binding.itemId)
        assertEquals(dummyViewModel, binding.viewModel)
    }

    @Test
    fun `ItemViewModelRegistry auto-assembles from Set of ItemViewModelBinding (ElementsIntoSet pattern)`() {
        val item1 = UiToggleItem(id = "auto_lock", nameResId = 1)
        val item2 = UiToggleItem(id = "child_lock", nameResId = 2)

        val vm1 = InMemoryItemViewModel(true)
        val vm2 = InMemoryItemViewModel(false)

        // Simulating @ElementsIntoSet collection from multiple feature modules
        val featureBindings: Set<ItemViewModelBinding<*>> = setOf(
            item1 bindsTo vm1,
            item2 bindsTo vm2
        )

        // ItemViewModelRegistry receives Set via constructor (DI / Hilt)
        val registry = ItemViewModelRegistry(featureBindings)

        // Query by String ID (for Recent category & search)
        assertEquals(vm1, registry.getViewModel<Boolean>("auto_lock"))
        assertEquals(vm2, registry.getViewModel<Boolean>("child_lock"))

        // Query by Item object (type-safe)
        assertEquals(vm1, registry.getViewModel<Boolean>(item1))
        assertEquals(vm2, registry.getViewModel<Boolean>(item2))
    }

    @Test
    fun `Zero-Omission check verifies all catalog items are bound without raw strings`() {
        val testCatalog = object : SettingCatalog("test") {
            val itemA = item(UiToggleItem(id = "item_a", nameResId = 1))
            val itemB = item(UiToggleItem(id = "item_b", nameResId = 2))
        }

        // Feature provides its bindings
        fun provideTestBindings(): Set<ItemViewModelBinding<*>> = setOf(
            testCatalog.itemA bindsTo InMemoryItemViewModel(true),
            testCatalog.itemB bindsTo InMemoryItemViewModel(false)
        )

        val catalogItemIds = testCatalog.items.map { it.id }.toSet()
        val boundItemIds = provideTestBindings().map { it.itemId }.toSet()

        // Verifies 100% binding completeness: NO item was forgotten
        assertEquals(catalogItemIds, boundItemIds)
    }
}
