package com.example.core.item

/**
 * Pure contract for a feature category to expose its settings items
 * and provider authority to the host container without coupling modules together.
 */
interface CategoryItemProvider {
    val categoryId: String
    val authority: String
    val titleKey: String
    val items: List<Item>

    /**
     * Finds an item by its key. Supports recursive searching into [ContainerItem.children].
     */
    fun findItem(key: String): Item? {
        fun search(list: List<Item>): Item? {
            for (item in list) {
                if (item.key == key) return item
                if (item is ContainerItem) {
                    val nested = search(item.children)
                    if (nested != null) return nested
                }
            }
            return null
        }
        return search(items)
    }
}
