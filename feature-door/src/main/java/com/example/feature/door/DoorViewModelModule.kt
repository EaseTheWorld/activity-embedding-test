package com.example.feature.door

import com.example.common.ui.settings.AppScope
import com.example.common.ui.settings.HardwareItemViewModel
import com.example.common.ui.settings.HardwarePropertyStorage
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.common.ui.settings.LocalStorageItemViewModel
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.bindsTo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton

// ============================================================================
// Layer 3: Assembler / Hilt DI Module Layer
// ============================================================================

@Module
@InstallIn(SingletonComponent::class)
abstract class DoorRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDoorSettingRepository(
        impl: DoorSettingRepositoryImpl
    ): DoorSettingRepository
}

/**
 * Hilt DI module providing Door [ItemViewModelBinding] multibindings to the global registry.
 */
@Module
@InstallIn(SingletonComponent::class)
object DoorHiltModule {

    @Provides
    @IntoSet
    fun provideUnlockOnParkBinding(
        repository: DoorSettingRepository,
        scope: CoroutineScope = AppScope.scope
    ): ItemViewModelBinding<*> =
        DoorCatalog.unlockOnPark bindsTo LocalStorageItemViewModel(
            repository = repository.unlockOnParkRepository,
            scope = scope
        )

    @Provides
    @IntoSet
    fun provideAutoDoorLockBinding(
        hardwareStorage: HardwarePropertyStorage
    ): ItemViewModelBinding<*> =
        DoorCatalog.autoDoorLock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_LOCK,
            storage = hardwareStorage
        )

    @Provides
    @IntoSet
    fun provideChildLockBinding(
        hardwareStorage: HardwarePropertyStorage
    ): ItemViewModelBinding<*> =
        DoorCatalog.childLock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.CHILD_LOCK,
            storage = hardwareStorage
        )
}

/**
 * Provides the complete set of [ItemViewModelBinding] for Door settings.
 * Bridges UI Catalog items with their corresponding Hardware/Storage ViewModels.
 */
object DoorViewModelModule {
    fun provideDoorBindings(
        hardwareStorage: HardwarePropertyStorage,
        autoLockVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
        scope: CoroutineScope = AppScope.scope,
        doorSettingRepository: DoorSettingRepository = DoorSettingRepositoryImpl()
    ): Set<ItemViewModelBinding<*>> = setOf(
        DoorCatalog.autoDoorLock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_LOCK,
            storage = hardwareStorage,
            scope = scope,
            isVisibleFlow = autoLockVisibleFlow
        ),
        DoorCatalog.childLock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.CHILD_LOCK,
            storage = hardwareStorage,
            scope = scope
        ),
        DoorCatalog.unlockOnPark bindsTo LocalStorageItemViewModel(
            repository = doorSettingRepository.unlockOnParkRepository,
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
        autoLockVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
        scope: CoroutineScope = AppScope.scope,
        doorSettingRepository: DoorSettingRepository = DoorSettingRepositoryImpl()
    ) {
        val bindings = DoorViewModelModule.provideDoorBindings(hardwareStorage, autoLockVisibleFlow, scope, doorSettingRepository)
        registry.registerAll(bindings)
    }
}
