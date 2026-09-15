package com.example.feature.door

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.common.ui.settings.UiToggleItem
import com.example.core.item.SettingCatalog

// ============================================================================
// Layer 1: UI Presentation Layer (Knows only UI strings, icons, and layout)
// ============================================================================

/**
 * Type-Safe Canonical Catalog for Door Settings using the SettingCatalog builder.
 * Uses a lightweight factory function to declare items without boilerplate subclassing.
 */
object DoorCatalog : SettingCatalog("door") {

    /**
     * Factory function creating a standard Door toggle item.
     * Centralizes UI defaults (e.g. icon) and eliminates boilerplate subclasses.
     */
    fun createToggle(
        id: String,
        @StringRes nameResId: Int,
        @StringRes descriptionResId: Int,
        @DrawableRes iconResId: Int = R.drawable.ic_feature_door,
        badgeKey: String? = null
    ): UiToggleItem = item(
        UiToggleItem(
            id = id,
            nameResId = nameResId,
            descriptionResId = descriptionResId,
            iconResId = iconResId,
            badgeKey = badgeKey
        )
    )

    val autoDoorLock = createToggle(
        id = "auto_lock",
        nameResId = R.string.door_item_autolock_title,
        descriptionResId = R.string.door_item_autolock_subtitle
    )

    val childLock = createToggle(
        id = "child_lock",
        nameResId = R.string.door_item_childlock_title,
        descriptionResId = R.string.door_item_childlock_subtitle
    )

    val unlockOnPark = createToggle(
        id = "unlock_on_park",
        nameResId = R.string.door_item_unlockpark_title,
        descriptionResId = R.string.door_item_unlockpark_subtitle,
        badgeKey = "SAFE"
    )

    val autoRelock = createToggle(
        id = "auto_relock",
        nameResId = R.string.door_item_autorelock_title,
        descriptionResId = R.string.door_item_autorelock_subtitle
    )
}
