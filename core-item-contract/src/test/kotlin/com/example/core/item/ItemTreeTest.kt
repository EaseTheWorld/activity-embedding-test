package com.example.core.item

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemTreeTest {

    @Test
    fun `tree hierarchy builds correctly with id and children Set`() {
        val child1 = Item("child_1")
        val child2 = Item("child_2")
        val root = Item("root", setOf(child1, child2))

        assertEquals("root", root.id)
        assertEquals(2, root.children.size)
        assertTrue(root.children.contains(child1))
        assertTrue(root.children.contains(child2))
    }

    @Test
    fun `findById finds items recursively at any depth`() {
        val leaf = Item("deep_leaf")
        val mid = Item("mid_node", setOf(leaf))
        val root = Item("root_node", setOf(mid))

        assertNotNull(root.findById("root_node"))
        assertNotNull(root.findById("mid_node"))
        assertNotNull(root.findById("deep_leaf"))
        assertNull(root.findById("non_existent"))

        assertEquals("deep_leaf", root.findById("deep_leaf")?.id)
    }

    @Test
    fun `flatten returns depth-first traversal of all nodes for search indexing`() {
        val child1 = Item("child_1")
        val child2 = Item("child_2")
        val grandChild = Item("grand_child")
        val branch = Item("branch", setOf(grandChild))
        val root = Item("root", setOf(child1, branch, child2))

        val allIds = root.flatten().map { it.id }.toList()

        assertTrue(allIds.contains("root"))
        assertTrue(allIds.contains("child_1"))
        assertTrue(allIds.contains("branch"))
        assertTrue(allIds.contains("grand_child"))
        assertTrue(allIds.contains("child_2"))
        assertEquals(5, allIds.size)
    }

    @Test
    fun `ValueItem subclasses hold stateflow and handle value changes`() {
        val flow = MutableStateFlow(true)
        val toggle = ToggleItem("auto_lock", flow)

        assertEquals("auto_lock", toggle.id)
        assertEquals(ItemType.TOGGLE, toggle.type)
        assertEquals(true, toggle.valueFlow.value)
        assertEquals("true", toggle.serializedValue)
    }
}
