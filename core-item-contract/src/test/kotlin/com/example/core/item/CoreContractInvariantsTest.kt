package com.example.core.item

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests protecting the Core Contract's default implementations and invariants.
 *
 * What production code changes does this test protect against?
 * 1. Accidental modification or omission in Item.type polymorphic default implementations.
 *    (e.g., if a developer refactors ToggleItem or adds an Item without setting ItemType,
 *    IPC clients relying on `item_type` column would receive invalid or unexpected values).
 * 2. CategoryItemRegistry state leakage, casing collisions, and null-safety regressions.
 */
class CoreContractInvariantsTest {

    @Before
    @After
    fun cleanUpRegistry() {
        CategoryItemRegistry.clear()
    }

    // ========================================================================
    // Protection 1: Item.type polymorphic default implementations
    // ========================================================================

    @Test
    fun `verify Item_type default implementations map every sub-interface correctly to ItemType`() {
        val toggleItem = object : ToggleItem {
            override val key = "toggle"
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
            override fun onValueChanged(newValue: Boolean) {}
        }
        val choiceItem = object : ChoiceItem {
            override val key = "choice"
            override val options = listOf("A", "B")
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow("A")
            override fun onValueChanged(newValue: String) {}
        }
        val sliderItem = object : SliderItem {
            override val key = "slider"
            override val min = 0
            override val max = 100
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow(50)
            override fun onValueChanged(newValue: Int) {}
        }
        val actionItem = object : ActionItem {
            override val key = "action"
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow(Unit)
            override fun onValueChanged(newValue: Unit) {}
        }
        val customItem = object : Item<String> {
            override val key = "custom"
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow("custom")
            override fun onValueChanged(newValue: String) {}
        }

        // Protects against: polymorphic contract violation in Item.kt sub-interfaces
        assertEquals(ItemType.TOGGLE, toggleItem.type)
        assertEquals(ItemType.CHOICE, choiceItem.type)
        assertEquals(ItemType.SLIDER, sliderItem.type)
        assertEquals(ItemType.ACTION, actionItem.type)
        assertEquals(ItemType.CUSTOM, customItem.type)
    }

    // ========================================================================
    // Protection 2: CategoryItemProvider.findItem default implementation
    // ========================================================================

    @Test
    fun `verify CategoryItemProvider_findItem default implementation correctly retrieves matching item`() {
        val item1 = object : Item<String> {
            override val key = "key_1"
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow("v1")
            override fun onValueChanged(newValue: String) {}
        }
        val item2 = object : Item<String> {
            override val key = "key_2"
            override val valueFlow = kotlinx.coroutines.flow.MutableStateFlow("v2")
            override fun onValueChanged(newValue: String) {}
        }

        val provider = object : CategoryItemProvider {
            override val categoryId = "test_cat"
            override val authority = "com.test.provider"
            override val titleKey = "title_key"
            override val items = listOf(item1, item2)
        }

        // Protects against: breaking findItem default resolution or NoSuchElementException
        assertEquals(item1, provider.findItem("key_1"))
        assertEquals(item2, provider.findItem("key_2"))
        assertNull(provider.findItem("non_existent_key"))
    }

    // ========================================================================
    // Protection 3: CategoryItemRegistry query resilience
    // ========================================================================

    @Test
    fun `verify CategoryItemRegistry handles case-insensitivity and null lookups safely`() {
        val provider = object : CategoryItemProvider {
            override val categoryId = "Vehicle_Light"
            override val authority = "com.example.light"
            override val titleKey = "title"
            override val items = emptyList<Item<*>>()
        }

        CategoryItemRegistry.register(provider)

        // Protects against: deep link case differences (e.g. "vehicle_light" vs "VEHICLE_LIGHT")
        assertEquals(provider, CategoryItemRegistry.getProvider("vehicle_light"))
        assertEquals(provider, CategoryItemRegistry.getProvider("VEHICLE_LIGHT"))
        assertEquals(provider, CategoryItemRegistry.getProvider("Vehicle_Light"))
        assertNull(CategoryItemRegistry.getProvider(null))
        assertNull(CategoryItemRegistry.getProvider(""))
    }
}
