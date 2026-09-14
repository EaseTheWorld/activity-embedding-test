package com.example.feature.door

import com.example.core.item.CategoryItemProvider
import com.example.core.item.Item

// ============================================================================
// Registry SSOT for Category Discovery & Flat Indexing
// ============================================================================

/**
 * Single Source of Truth (SSOT) for Door Feature items.
 * Strictly preserves the display order via List<Item>.
 */
object DoorItemRegistry : CategoryItemProvider {
    override val categoryId: String = "door"
    override val authority: String = DoorSettingsProvider.AUTHORITY
    override val titleKey: String = "category_door_title"

    val autoDoorLock get() = DoorCatalog.autoDoorLock
    val childLock get() = DoorCatalog.childLock
    val unlockOnPark get() = DoorCatalog.unlockOnPark

    override val items: List<Item> get() = listOf(
        autoDoorLock,
        childLock,
        unlockOnPark
    )
}
