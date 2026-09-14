package com.example.core.item

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests protecting the Core Contract's default implementations and invariants.
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
    fun `verify Item_type default implementations map correctly to ItemType`() {
        val customItem = Item("custom")
        assertEquals(ItemType.CUSTOM, customItem.type)
    }

    // ========================================================================
    // Protection 2: CategoryItemProvider.findItem default implementation
    // ========================================================================

    @Test
    fun `verify CategoryItemProvider_findItem default implementation correctly retrieves matching item`() {
        val item1 = Item("key_1")
        val item2 = Item("key_2")

        val provider = object : CategoryItemProvider {
            override val categoryId = "test_cat"
            override val authority = "com.test.provider"
            override val titleKey = "title_key"
            override val items = listOf(item1, item2)
        }

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
            override val items = emptyList<Item>()
        }

        CategoryItemRegistry.register(provider)

        assertEquals(provider, CategoryItemRegistry.getProvider("vehicle_light"))
        assertEquals(provider, CategoryItemRegistry.getProvider("VEHICLE_LIGHT"))
        assertEquals(provider, CategoryItemRegistry.getProvider("Vehicle_Light"))
        assertNull(CategoryItemRegistry.getProvider(null))
        assertNull(CategoryItemRegistry.getProvider(""))
    }
}

