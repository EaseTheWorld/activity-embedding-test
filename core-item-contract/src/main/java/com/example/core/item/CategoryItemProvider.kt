package com.example.core.item

/**
 * Pure contract for a feature category to expose its settings items
 * and provider authority to the host container without coupling modules together.
 */
interface CategoryItemProvider {
    val categoryId: String
    val authority: String
    val titleKey: String
    val items: List<Item<*>>

    fun findItem(key: String): Item<*>? = items.find { it.key == key }
}
