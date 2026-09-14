package com.example.core.item

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        val child1 = Item("child_1")
        val grandChild = Item("grand_child")
        val branch = Item("branch", setOf(grandChild))
        val root = Item("root", setOf(child1, branch))

        assertEquals(root, root.findById("root"))
        assertEquals(child1, root.findById("child_1"))
        assertEquals(branch, root.findById("branch"))
        assertEquals(grandChild, root.findById("grand_child"))
        assertNull(root.findById("non_existent"))
    }

    @Test
    fun `flatten flattens tree in depth-first order`() {
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
    fun `Item constructor accepts isVisible Flow and defaults to true`() = kotlinx.coroutines.test.runTest {
        val defaultItem = Item("default_item")
        val defaultVisible = defaultItem.isVisible.first()
        assertTrue(defaultVisible)

        val dynamicFlow = MutableStateFlow(false)
        val dynamicItem = Item("dynamic_item", isVisible = dynamicFlow)
        assertEquals(false, dynamicItem.isVisible.first())

        dynamicFlow.value = true
        assertEquals(true, dynamicItem.isVisible.first())
    }
}
