package com.example.feature.door

import com.example.common.ui.settings.AppScope
import com.example.common.ui.settings.HardwareItemViewModel
import com.example.common.ui.settings.HardwarePropertyStorage
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.common.ui.settings.LocalStorageItemViewModel
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.bindsTo
import kotlinx.coroutines.CoroutineScope

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
