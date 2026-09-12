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

    val rootItem: Item get() = Item(id = categoryId, children = items.toSet())

    fun findItem(key: String): Item? = rootItem.findById(key) ?: items.find { it.key == key }
}
