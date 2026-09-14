package com.example.common.ui.settings

import com.example.core.item.Item
import com.example.core.item.SettingCatalog
import com.example.core.item.ItemViewModelRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DualContextRenderingTest {

    @Test
    fun `RecentCategoryManager maintains MRU order and deduplicates keys`() {
        val manager = RecentCategoryManager(maxRecentItems = 3)

        manager.recordUsage("auto_lock")
        manager.recordUsage("seat_lumbar")
        manager.recordUsage("child_lock")

        assertEquals(listOf("child_lock", "seat_lumbar", "auto_lock"), manager.recentKeys.value)

        // Access auto_lock again -> moves to the front
        manager.recordUsage("auto_lock")
        assertEquals(listOf("auto_lock", "child_lock", "seat_lumbar"), manager.recentKeys.value)

        // Add 4th item -> oldest (seat_lumbar) is evicted
        manager.recordUsage("unlock_on_park")
        assertEquals(listOf("unlock_on_park", "auto_lock", "child_lock"), manager.recentKeys.value)
    }

    @Test
    fun `shared ItemViewModel synchronizes state between List row and Grid card`() = runTest {
        val testScope = TestScope(testScheduler)
        val viewModelRegistry = ItemViewModelRegistry()

        // ApplicationScope-backed item view model
        val autoLockViewModel = LocalStorageItemViewModel(initialValue = false, scope = testScope)
        viewModelRegistry.register("auto_lock", autoLockViewModel)

        // 1. Simulating Door Screen (List Row) observing state
        val doorListFlow = viewModelRegistry.getViewModel<Boolean>("auto_lock")!!.valueFlow
        assertEquals(false, doorListFlow.value)

        // 2. User navigates to Recent / Quick Controls screen (Grid Card)
        // User clicks the GridCard switch to toggle it ON
        val recentGridViewModel = viewModelRegistry.getMutableViewModel<Boolean>("auto_lock")!!
        recentGridViewModel.setValue(true)
        testScope.advanceUntilIdle()

        // 3. Both contexts reflect the same updated value without ViewModel coordination!
        assertEquals(true, recentGridViewModel.valueFlow.value)
        assertEquals(true, doorListFlow.value)
    }

    @Test
    fun `polymorphic ItemRendererRegistry resolves renderer for Item subclasses`() {
        val registry = ItemRendererRegistry.defaultListRegistry

        class TestCustomToggle : UiToggleItem(
            id = "custom_toggle",
            nameResId = 1
        )

        val customItem = TestCustomToggle()

        // Should resolve ToggleListRenderer via superclass UiToggleItem
        val renderer = registry.findRenderer(customItem::class)
        assertNotNull("Renderer should be resolved polymorphically for subclasses", renderer)
        assertTrue("Resolved renderer must be ToggleListRenderer", renderer is ToggleListRenderer)
    }

    @Test
    fun `complex UI items without Grid renderer are rejected from Recent display`() {
        val listRegistry = ItemRendererRegistry.defaultListRegistry
        val gridRegistry = ItemRendererRegistry.defaultGridRegistry
        val recentManager = RecentCategoryManager()

        // 1. Simple toggle item: supported in both List and Grid
        val toggleItem = UiToggleItem(id = "auto_lock", nameResId = 1)
        assertTrue("Toggle item must be supported in List", listRegistry.hasRenderer(toggleItem::class))
        assertTrue("Toggle item must be supported in Grid", gridRegistry.hasRenderer(toggleItem::class))
        assertTrue("Toggle item must be eligible for Recent", recentManager.isEligibleForRecent(toggleItem, gridRegistry))

        // 2. Complex UI item (e.g. 10-Band Equalizer or 3D Seat Calibration)
        class ComplexEqualizerItem : UiItem(id = "sound_eq", nameResId = 2)
        val complexItem = ComplexEqualizerItem()

        // Registered ONLY in List registry (has a rich full-page Composable row)
        listRegistry.register(ComplexEqualizerItem::class, object : ItemRenderer<ComplexEqualizerItem, Unit> {
            @androidx.compose.runtime.Composable
            override fun Render(item: ComplexEqualizerItem, viewModel: com.example.core.item.ItemViewModel<Unit>, modifier: androidx.compose.ui.Modifier) {}
        })

        // Complex item is supported in List, but deliberately NOT in Grid registry!
        assertTrue(listRegistry.hasRenderer(complexItem::class))
        org.junit.Assert.assertFalse("Complex item must NOT have a grid renderer", gridRegistry.hasRenderer(complexItem::class))
        org.junit.Assert.assertFalse("Complex item must NOT be eligible for Recent", recentManager.isEligibleForRecent(complexItem, gridRegistry))
    }

    @Test
    fun `Recent category dynamically resolves multi-feature items by key`() = runTest {
        val testScope = TestScope(testScheduler)
        val viewModelRegistry = ItemViewModelRegistry()

        // Catalog items from different features (Door & Seat)
        val doorItem = UiToggleItem(id = "auto_lock", nameResId = 101)
        val seatItem = UiSliderItem(id = "seat_lumbar", nameResId = 201, min = 0, max = 5)

        viewModelRegistry.register("auto_lock", LocalStorageItemViewModel(false, testScope))
        viewModelRegistry.register("seat_lumbar", InMemoryItemViewModel(2))

        val catalogMap = mapOf<String, Item>(
            doorItem.id to doorItem,
            seatItem.id to seatItem
        )

        val recentManager = RecentCategoryManager(initialKeys = listOf("seat_lumbar", "auto_lock"))

        // Resolve in Recent Screen
        val resolvedItems = recentManager.recentKeys.value.mapNotNull { key -> catalogMap[key] }
        assertEquals(2, resolvedItems.size)
        assertEquals("seat_lumbar", resolvedItems[0].id)
        assertEquals("auto_lock", resolvedItems[1].id)

        // Verify state is accessible via viewModelRegistry for both
        assertEquals(2, viewModelRegistry.getViewModel<Int>("seat_lumbar")?.valueFlow?.value)
        assertEquals(false, viewModelRegistry.getViewModel<Boolean>("auto_lock")?.valueFlow?.value)
    }

    @Test
    fun `adding a new item requires only 2 lines of configuration with zero screen changes`() = runTest {
        val testScope = TestScope(testScheduler)
        val viewModelRegistry = ItemViewModelRegistry()

        // Feature Catalog Builder
        val testCatalog = object : SettingCatalog("test_feature") {
            // Existing item
            val existingItem = item(UiToggleItem(id = "item_1", nameResId = 1))

            // [CHANGE POINT 1]: 1 line in Catalog
            val newItem = item(UiToggleItem(id = "item_2", nameResId = 2))
        }

        // Feature ViewModel Binder
        // [CHANGE POINT 2]: 1 line in ViewModel Binder
        viewModelRegistry.register("item_1", InMemoryItemViewModel(true))
        viewModelRegistry.register("item_2", LocalStorageItemViewModel(false, testScope))

        // Verification: The items list is automatically updated via catalog.items
        val allItems = testCatalog.items
        assertEquals(2, allItems.size)
        assertEquals("item_1", allItems[0].id)
        assertEquals("item_2", allItems[1].id)

        // Both items can be rendered in List (Feature Screen) and Grid (Recent Screen)
        // with ZERO lines changed in GenericSettingsScreen or RecentSettingsScreen!
        assertNotNull(viewModelRegistry.getViewModel<Boolean>("item_1"))
        assertNotNull(viewModelRegistry.getViewModel<Boolean>("item_2"))
    }
}
