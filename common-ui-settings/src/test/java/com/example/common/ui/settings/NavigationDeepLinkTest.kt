package com.example.common.ui.settings

import com.example.core.item.CategoryItemProvider
import com.example.core.item.CategoryItemRegistry
import com.example.core.item.Item
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

/**
 * Unit tests verifying Jetpack Navigation 2.x route construction,
 * URI patterns, deep link parsing, and intra/inter-category navigation contracts.
 */
class NavigationDeepLinkTest {

    private class MockCategoryProvider(
        override val categoryId: String,
        override val authority: String = "com.example.mock.$categoryId",
        override val titleKey: String = "category_${categoryId}_title",
        override val items: List<Item>
    ) : CategoryItemProvider

    @Before
    fun setUp() {
        CategoryItemRegistry.clear()
    }

    @After
    fun tearDown() {
        CategoryItemRegistry.clear()
    }

    // ========================================================================
    // 1. SettingsNavigation Route and Deep Link Contract Tests
    // ========================================================================

    @Test
    fun `categoryRoute builds expected relative navigation route`() {
        assertEquals("navigate/door", SettingsNavigation.categoryRoute("door"))
        assertEquals("navigate/seat", SettingsNavigation.categoryRoute("seat"))
        assertEquals("navigate/display", SettingsNavigation.categoryRoute("display"))
    }

    @Test
    fun `itemDetailRoute builds expected intra-category relative navigation route`() {
        assertEquals("navigate/door/auto_lock", SettingsNavigation.itemDetailRoute("door", "auto_lock"))
        assertEquals("navigate/seat/seat_lumbar", SettingsNavigation.itemDetailRoute("seat", "seat_lumbar"))
        assertEquals("navigate/seat/driver_seat_heat", SettingsNavigation.itemDetailRoute("seat", "driver_seat_heat"))
    }

    @Test
    fun `deep link URI constants and helper functions conform to myapp-navigate format`() {
        assertEquals("myapp", SettingsNavigation.DEEP_LINK_SCHEME)
        assertEquals("navigate", SettingsNavigation.DEEP_LINK_HOST)
        assertEquals("dashboard", SettingsNavigation.ROUTE_DASHBOARD)

        // Inter-category deep link
        val doorDeepLink = SettingsNavigation.categoryDeepLink("door")
        assertEquals("myapp://navigate/door", doorDeepLink)

        // Intra-category item deep link
        val itemDeepLink = SettingsNavigation.itemDetailDeepLink("seat", "seat_lumbar")
        assertEquals("myapp://navigate/seat/seat_lumbar", itemDeepLink)
    }

    @Test
    fun `URI parsing correctly decomposes deep links into category and item segments`() {
        // 1. Category deep link
        val catUri = URI.create(SettingsNavigation.categoryDeepLink("door"))
        assertEquals("myapp", catUri.scheme)
        assertEquals("navigate", catUri.host)
        val catSegments = catUri.path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        assertEquals(listOf("door"), catSegments)

        // 2. Intra-category item deep link
        val itemUri = URI.create(SettingsNavigation.itemDetailDeepLink("seat", "seat_lumbar"))
        assertEquals("myapp", itemUri.scheme)
        assertEquals("navigate", itemUri.host)
        val itemSegments = itemUri.path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        assertEquals(listOf("seat", "seat_lumbar"), itemSegments)
        assertEquals("seat", itemSegments[0])
        assertEquals("seat_lumbar", itemSegments[1])

        // 3. Dashboard deep link
        val dashUri = URI.create("myapp://navigate/dashboard")
        assertEquals("myapp", dashUri.scheme)
        assertEquals("navigate", dashUri.host)
        val dashSegments = dashUri.path.removePrefix("/").split("/").filter { it.isNotEmpty() }
        assertEquals(listOf("dashboard"), dashSegments)
    }

    // ========================================================================
    // 2. Intra-Category Item Detail Resolution Tests
    // ========================================================================

    @Test
    fun `intra-category route resolves Item from CategoryItemRegistry`() {
        val autoLockToggle = BaseUiToggleItem(id = "auto_lock", nameResId = 101)
        val childRelock = BaseUiToggleItem(id = "auto_relock", nameResId = 102)
        val doorToggleWithChildren = BaseUiToggleItem(
            id = "auto_lock_parent",
            nameResId = 100,
            children = setOf(childRelock)
        )
        val doorProvider = MockCategoryProvider(
            categoryId = "door",
            items = listOf(autoLockToggle, doorToggleWithChildren)
        )
        CategoryItemRegistry.register(doorProvider)

        // Simulate deep link: myapp://navigate/door/auto_lock
        val targetCategory = "door"
        val targetItem = "auto_lock"

        val provider = CategoryItemRegistry.getProvider(targetCategory)
        assertNotNull(provider)
        val resolvedItem = provider?.findItem(targetItem)
        assertNotNull(resolvedItem)
        assertEquals("auto_lock", resolvedItem?.id)

        // Simulate deep link for parent item with children: myapp://navigate/door/auto_lock_parent
        val resolvedParent = provider?.findItem("auto_lock_parent")
        assertNotNull(resolvedParent)
        assertEquals(1, resolvedParent?.children?.size)
        assertEquals("auto_relock", resolvedParent?.children?.first()?.id)
    }

    @Test
    fun `intra-category resolution returns null for unknown items`() {
        val doorProvider = MockCategoryProvider(
            categoryId = "door",
            items = listOf(BaseUiToggleItem(id = "auto_lock", nameResId = 101))
        )
        CategoryItemRegistry.register(doorProvider)

        val provider = CategoryItemRegistry.getProvider("door")
        val nonExistent = provider?.findItem("non_existent_item")
        assertNull(nonExistent)
    }

    // ========================================================================
    // 3. Inter-Category and External Delegation Logic Tests
    // ========================================================================

    @Test
    fun `inter-category navigation routes internally for registered categories and delegates for light`() {
        val navigatedInternal = mutableListOf<String>()
        val navigatedExternal = mutableListOf<String>()

        val onNavigate: (String) -> Unit = { categoryId ->
            if (categoryId.equals("light", ignoreCase = true)) {
                navigatedExternal.add(categoryId)
            } else {
                navigatedInternal.add(SettingsNavigation.categoryRoute(categoryId))
            }
        }

        // Test internal navigation
        onNavigate("door")
        onNavigate("seat")
        assertEquals(listOf("navigate/door", "navigate/seat"), navigatedInternal)
        assertTrue(navigatedExternal.isEmpty())

        // Test external app delegation
        onNavigate("light")
        assertEquals(listOf("light"), navigatedExternal)
    }
}
