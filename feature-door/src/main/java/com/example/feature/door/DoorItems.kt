package com.example.feature.door

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.common.ui.settings.AppScope
import com.example.common.ui.settings.UiToggleItem
import com.example.common.ui.settings.HardwareItemViewModel
import com.example.common.ui.settings.HardwarePropertyStorage
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.common.ui.settings.LocalStorageItemViewModel
import com.example.core.item.CategoryItemProvider
import com.example.core.item.Item
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.SettingCatalog
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.ValueMapping
import com.example.core.item.VehicleProperty
import com.example.core.item.bindsTo
import kotlinx.coroutines.CoroutineScope

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
}

// ============================================================================
// Layer 2: Hardware / HAL Layer (Pure Infrastructure, Zero UI Dependencies)
// ============================================================================

/**
 * Automotive Vehicle HAL Properties for Door controls.
 * Encapsulates Property ID and ValueMapping without any knowledge of Android UI.
 */
object DoorVehicleProperties {

    /**
     * Factory function creating a standard boolean VHAL property.
     */
    fun booleanProp(propertyId: Int): VehicleProperty<Boolean, Int> =
        VehicleProperty(propertyId = propertyId, mapper = ValueMapping.BooleanToInt)

    val AUTO_LOCK = booleanProp(0x11400bc0)
    val CHILD_LOCK = booleanProp(0x11400bc1)
}

// ============================================================================
// Layer 3: Assembler / DI Module Layer (The Sole Coupling Point)
// ============================================================================

/**
 * Provides the complete set of [ItemViewModelBinding] for Door settings.
 * Bridges UI Catalog items with their corresponding Hardware/Storage ViewModels.
 */
object DoorViewModelModule {
    fun provideDoorBindings(
        hardwareStorage: HardwarePropertyStorage,
        scope: CoroutineScope = AppScope.scope
    ): Set<ItemViewModelBinding<*>> = setOf(
        DoorCatalog.autoDoorLock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_LOCK,
            storage = hardwareStorage,
            scope = scope
        ),
        DoorCatalog.childLock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.CHILD_LOCK,
            storage = hardwareStorage,
            scope = scope
        ),
        DoorCatalog.unlockOnPark bindsTo LocalStorageItemViewModel(
            initialValue = true,
            scope = scope
        )
    )
}

/**
 * Binds ViewModels for Door settings running within an ApplicationScope.
 */
object DoorViewModelBinder {
    fun bindAll(
        registry: ItemViewModelRegistry,
        hardwareStorage: HardwarePropertyStorage = InMemoryHardwareStorage().apply {
            setInitialValue(DoorVehicleProperties.AUTO_LOCK, true)
            setInitialValue(DoorVehicleProperties.CHILD_LOCK, false)
        },
        scope: CoroutineScope = AppScope.scope
    ) {
        val bindings = DoorViewModelModule.provideDoorBindings(hardwareStorage, scope)
        registry.registerAll(bindings)
    }
}

/**
 * Single Source of Truth (SSOT) for Door Feature items.
 * Strictly preserves the display order via List<Item>.
 */
object DoorItemRegistry : com.example.core.item.CategoryItemProvider {
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
