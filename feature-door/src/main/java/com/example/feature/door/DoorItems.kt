package com.example.feature.door

import com.example.common.ui.settings.UiToggleItem
import com.example.core.item.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutoDoorLockItem : UiToggleItem {
    override val key: String = "auto_lock"
    override val titleRes: Int = R.string.door_item_autolock_title
    override val subtitleRes: Int = R.string.door_item_autolock_subtitle
    override val iconRes: Int = R.drawable.ic_feature_door

    private val _value = MutableStateFlow(true)
    override val valueFlow: StateFlow<Boolean> = _value.asStateFlow()

    override fun onValueChanged(newValue: Boolean) {
        _value.value = newValue
    }
}

class ChildLockItem : UiToggleItem {
    override val key: String = "child_lock"
    override val titleRes: Int = R.string.door_item_childlock_title
    override val subtitleRes: Int = R.string.door_item_childlock_subtitle
    override val iconRes: Int = R.drawable.ic_feature_door

    private val _value = MutableStateFlow(false)
    override val valueFlow: StateFlow<Boolean> = _value.asStateFlow()

    override fun onValueChanged(newValue: Boolean) {
        _value.value = newValue
    }
}

class UnlockOnParkItem : UiToggleItem {
    override val key: String = "unlock_on_park"
    override val titleRes: Int = R.string.door_item_unlockpark_title
    override val subtitleRes: Int = R.string.door_item_unlockpark_subtitle
    override val iconRes: Int = R.drawable.ic_feature_door
    // Explicitly demonstrating badge decoration on a ToggleItem
    override val badgeKey: String? = "SAFE"

    private val _value = MutableStateFlow(true)
    override val valueFlow: StateFlow<Boolean> = _value.asStateFlow()

    override fun onValueChanged(newValue: Boolean) {
        _value.value = newValue
    }
}

/**
 * Single Source of Truth (SSOT) for Door Feature items.
 * Strictly preserves the display order via List<Item<*>>.
 */
object DoorItemRegistry : com.example.core.item.CategoryItemProvider {
    override val categoryId: String = "door"
    override val authority: String = DoorSettingsProvider.AUTHORITY
    override val titleKey: String = "category_door_title"

    val autoDoorLock = AutoDoorLockItem()
    val childLock = ChildLockItem()
    val unlockOnPark = UnlockOnParkItem()

    override val items: List<Item<*>> = listOf(
        autoDoorLock,
        childLock,
        unlockOnPark
    )
}
