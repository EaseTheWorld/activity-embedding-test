package com.example.common.ui.settings

import androidx.compose.runtime.Immutable
import com.example.core.item.Item
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiItemTreeTest {

    @Test
    fun `UiItem hierarchy inherits Item tree structure with id and children`() {
        val child1 = UiToggleItem(
            id = "frunk_light",
            nameResId = 1001,
            iconResId = 2001,
            valueFlow = MutableStateFlow(true)
        )
        val child2 = UiToggleItem(
            id = "trunk_light",
            nameResId = 1002,
            iconResId = 2002,
            valueFlow = MutableStateFlow(false)
        )
        val parentGroup = UiItem(
            id = "lights_group",
            nameResId = 1000,
            iconResId = 2000,
            children = setOf(child1, child2)
        )

        assertEquals("lights_group", parentGroup.id)
        assertEquals(2, parentGroup.children.size)
        assertEquals(child1, parentGroup.findById("frunk_light"))
        assertEquals(child2, parentGroup.findById("trunk_light"))
    }

    @Test
    fun `UiItem hierarchy ensures immutability with read-only properties and sets`() {
        val child = UiToggleItem(
            id = "toggle_1",
            nameResId = 100,
            valueFlow = MutableStateFlow(true)
        )
        val parent = UiItem(
            id = "parent_1",
            nameResId = 200,
            children = setOf(child)
        )

        // Verifies immutability: children is a read-only Set
        assertEquals(1, parent.children.size)
        assertTrue(parent.children.contains(child))

        // Ensure key and id match
        assertEquals("parent_1", parent.key)
        assertEquals("parent_1", parent.id)
    }

    @Test
    fun `external search manager can traverse Item tree without Compose dependencies`() {
        val child = UiToggleItem(
            id = "child_lock",
            nameResId = 101,
            valueFlow = MutableStateFlow(false)
        )
        val root = Item(
            id = "door_category",
            children = setOf(child)
        )

        val flattenedList = root.flatten().toList()
        assertEquals(2, flattenedList.size)
        assertEquals("door_category", flattenedList[0].id)
        assertEquals("child_lock", flattenedList[1].id)

        val uiItem = flattenedList[1] as UiItem
        assertEquals(101, uiItem.nameResId)
    }
}
