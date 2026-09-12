package com.example.core.item

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Demonstrates how an external Search Manager or indexing service
 * consumes and indexes the pure Item tree with zero Compose UI dependencies.
 */
data class SearchIndexEntry(
    val id: String,
    val path: List<String>,
    val type: ItemType
)

class MockSearchManager {
    private val index = mutableListOf<SearchIndexEntry>()

    fun indexTree(root: Item) {
        traverse(root, emptyList())
    }

    private fun traverse(item: Item, currentPath: List<String>) {
        val path = currentPath + item.id
        index.add(SearchIndexEntry(id = item.id, path = path, type = item.type))
        for (child in item.children) {
            traverse(child, path)
        }
    }

    fun searchById(query: String): List<SearchIndexEntry> {
        return index.filter { it.id.contains(query, ignoreCase = true) }
    }
}

class SearchManagerIntegrationTest {

    @Test
    fun `SearchManager indexes nested Item hierarchy and performs search lookups`() {
        val autoLock = ToggleItem("auto_lock", MutableStateFlow(true))
        val childLock = ToggleItem("child_lock", MutableStateFlow(false))
        val lockGroup = Item("lock_settings", setOf(autoLock, childLock))

        val mirrorFold = ToggleItem("auto_mirror_fold", MutableStateFlow(true))
        val doorCategory = Item("door", setOf(lockGroup, mirrorFold))

        val searchManager = MockSearchManager()
        searchManager.indexTree(doorCategory)

        // Verify search by keyword
        val lockResults = searchManager.searchById("lock")
        assertEquals(3, lockResults.size) // lock_settings, auto_lock, child_lock

        // Verify breadcrumb path traversal
        val autoLockEntry = lockResults.find { it.id == "auto_lock" }
        assertNotNull(autoLockEntry)
        assertEquals(listOf("door", "lock_settings", "auto_lock"), autoLockEntry!!.path)
        assertEquals(ItemType.TOGGLE, autoLockEntry.type)

        // Verify tree findById
        val foundItem = doorCategory.findById("child_lock")
        assertNotNull(foundItem)
        assertEquals("child_lock", foundItem!!.id)
    }
}
