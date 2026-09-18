package com.example.feature.home

import com.example.core.item.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeItemSearchTest {

    private val sampleItems = listOf(
        SearchableSettingItem(
            itemId = "driver_seat_heat",
            title = "Driver Seat Heating",
            subtitle = "Adjust heat level",
            categoryId = "seat",
            categoryTitle = "Seats",
            itemType = ItemType.CHOICE,
            deepLinkUri = "myapp://navigate/seat/driver_seat_heat"
        ),
        SearchableSettingItem(
            itemId = "passenger_seat_heat",
            title = "Passenger Seat Heating",
            subtitle = "Adjust passenger heat",
            categoryId = "seat",
            categoryTitle = "Seats",
            itemType = ItemType.CHOICE,
            deepLinkUri = "myapp://navigate/seat/passenger_seat_heat"
        ),
        SearchableSettingItem(
            itemId = "door_child_lock",
            title = "Child Safety Lock",
            subtitle = "Lock rear doors",
            categoryId = "door",
            categoryTitle = "Doors & Locks",
            itemType = ItemType.TOGGLE,
            deepLinkUri = "myapp://navigate/door/door_child_lock"
        ),
        SearchableSettingItem(
            itemId = "ambient_light",
            title = "Ambient Lighting",
            subtitle = "Interior mood lamp color",
            categoryId = "light",
            categoryTitle = "Lighting",
            itemType = ItemType.CHOICE,
            deepLinkUri = "myapp://navigate/light/ambient_light"
        )
    )

    @Test
    fun `empty query returns empty results`() {
        val results = HomeItemSearchIndexer.search("", sampleItems)
        assertTrue(results.isEmpty())
        val whitespaceResults = HomeItemSearchIndexer.search("   ", sampleItems)
        assertTrue(whitespaceResults.isEmpty())
    }

    @Test
    fun `search by title case-insensitive returns matching items`() {
        val results = HomeItemSearchIndexer.search("seat", sampleItems)
        assertEquals(2, results.size)
        assertTrue(results.all { it.categoryId == "seat" })

        val lightResults = HomeItemSearchIndexer.search("LIGHT", sampleItems)
        assertEquals(1, lightResults.size)
        assertEquals("ambient_light", lightResults[0].itemId)
    }

    @Test
    fun `search by subtitle returns matching items`() {
        val results = HomeItemSearchIndexer.search("mood lamp", sampleItems)
        assertEquals(1, results.size)
        assertEquals("ambient_light", results[0].itemId)
    }

    @Test
    fun `search by categoryTitle returns matching items`() {
        val results = HomeItemSearchIndexer.search("Doors", sampleItems)
        assertEquals(1, results.size)
        assertEquals("door_child_lock", results[0].itemId)
    }

    @Test
    fun `deepLinkUri format is valid for cross-activity embedding`() {
        val item = sampleItems[0]
        assertEquals("myapp://navigate/seat/driver_seat_heat", item.deepLinkUri)
    }
}
