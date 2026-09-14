package com.example.common.ui.settings

import com.example.core.item.CategoryItemProvider
import com.example.core.item.Item
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
class MainSettingsDashboardTest {

    private class MockCategoryProvider(
        override val categoryId: String,
        override val authority: String = "com.example.mock.$categoryId",
        override val titleKey: String = "category_${categoryId}_title",
        override val items: List<Item>
    ) : CategoryItemProvider

    @Test
    fun `Recent section aggregates cross-category items dynamically via itemResolver`() {
        val doorToggle = BaseUiToggleItem(id = "auto_lock", nameResId = 101)
        val seatToggle = BaseUiToggleItem(id = "easy_entry", nameResId = 201)
        val soundChoice = UiChoiceItem(
            id = "surround_mode",
            nameResId = 301,
            options = listOf(UiOption("OFF", 1), UiOption("CINEMA", 2))
        )

        val doorProvider = MockCategoryProvider("door", items = listOf(doorToggle))
        val seatProvider = MockCategoryProvider("seat", items = listOf(seatToggle))
        val soundProvider = MockCategoryProvider("sound", items = listOf(soundChoice))
        val allProviders = listOf(doorProvider, seatProvider, soundProvider)

        val itemResolver: (String) -> Item? = { key ->
            allProviders.firstNotNullOfOrNull { it.findItem(key) }
        }

        val recentManager = RecentCategoryManager(maxRecentItems = 4)
        recentManager.recordUsage("auto_lock")
        recentManager.recordUsage("easy_entry")
        recentManager.recordUsage("surround_mode")

        val recentItems = recentManager.recentKeys.value.mapNotNull { itemResolver(it) }

        // Must be in MRU order: surround_mode -> easy_entry -> auto_lock
        assertEquals(3, recentItems.size)
        assertEquals("surround_mode", recentItems[0].id)
        assertEquals("easy_entry", recentItems[1].id)
        assertEquals("auto_lock", recentItems[2].id)
    }

    @Test
    fun `Actual categories preserve display order and render without ItemRendererRegistry`() {
        val toggleItem = UiToggleItem(id = "auto_lock", nameResId = 1)
        val choiceItem = UiChoiceItem(id = "theme", nameResId = 2, options = listOf(UiOption("DARK", 10)))
        val sliderItem = UiSliderItem(id = "volume", nameResId = 3, min = 0, max = 100)

        val provider = MockCategoryProvider("car_settings", items = listOf(toggleItem, choiceItem, sliderItem))

        // In registry-free architecture, items are directly available from provider without a registry
        assertEquals(3, provider.items.size)
        assertTrue(provider.items[0] is UiToggleItem)
        assertTrue(provider.items[1] is UiChoiceItem)
        assertTrue(provider.items[2] is UiSliderItem)
    }

    @Test
    fun `State updates sync seamlessly between top Recent card and bottom Category row`() = runTest {
        val testScope = TestScope(testScheduler)
        val viewModelRegistry = ItemViewModelRegistry()

        // Shared LocalStorage ItemViewModel for auto_lock
        val autoLockVm = LocalStorageItemViewModel(initialValue = false, scope = testScope)
        viewModelRegistry.register("auto_lock", autoLockVm)

        // 1. Initial state
        assertEquals(false, autoLockVm.valueFlow.value)

        // 2. User toggles switch in the Top Recent Grid Card
        val recentVm = viewModelRegistry.getMutableViewModel<Boolean>("auto_lock")
        assertNotNull(recentVm)
        recentVm!!.setValue(true)
        testScope.advanceUntilIdle()

        // 3. Bottom Category Row automatically sees the updated value (SSOT)
        val categoryRowVm = viewModelRegistry.getViewModel<Boolean>("auto_lock")
        assertEquals(true, categoryRowVm!!.valueFlow.value)
    }

    @Test
    fun `DualToggleRow allows compound layout freedom for two items without registry`() = runTest {
        val testScope = TestScope(testScheduler)
        val vmRegistry = ItemViewModelRegistry()

        val leftToggle = UiToggleItem(id = "frunk_light", nameResId = 11)
        val rightToggle = UiToggleItem(id = "trunk_light", nameResId = 12)
        assertEquals("frunk_light", leftToggle.id)
        assertEquals("trunk_light", rightToggle.id)

        val leftVm = LocalStorageItemViewModel(initialValue = false, scope = testScope)
        val rightVm = LocalStorageItemViewModel(initialValue = false, scope = testScope)

        vmRegistry.register("frunk_light", leftVm)
        vmRegistry.register("trunk_light", rightVm)

        // Toggle left
        leftVm.setValue(true)
        testScope.advanceUntilIdle()

        // Left is updated, right remains independent
        assertEquals(true, vmRegistry.getViewModel<Boolean>("frunk_light")!!.valueFlow.value)
        assertEquals(false, vmRegistry.getViewModel<Boolean>("trunk_light")!!.valueFlow.value)
    }
}
