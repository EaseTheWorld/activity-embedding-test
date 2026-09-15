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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    @Singleton
    fun provideAutoDoorLockViewModel(
        hardwareStorage: HardwarePropertyStorage,
        signals: com.example.common.ui.settings.VehicleSignals = com.example.common.ui.settings.DefaultVehicleSignals(),
        scope: CoroutineScope = AppScope.scope
    ): HardwareItemViewModel<Boolean, Int> =
        HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_LOCK,
            storage = hardwareStorage,
            isVisibleFlow = signals.autoLockVisibleFlow,
            scope = scope
        )

    @Provides
    @IntoSet
    fun provideAutoDoorLockBinding(
        autoLockVm: HardwareItemViewModel<Boolean, Int>
    ): ItemViewModelBinding<*> =
        DoorCatalog.autoDoorLock bindsTo autoLockVm

    /**
     * Convenience factory for tests and direct callers.
     */
    fun provideAutoDoorLockBinding(
        hardwareStorage: HardwarePropertyStorage,
        signals: com.example.common.ui.settings.VehicleSignals = com.example.common.ui.settings.DefaultVehicleSignals(),
        scope: CoroutineScope = AppScope.scope
    ): ItemViewModelBinding<*> =
        DoorCatalog.autoDoorLock bindsTo provideAutoDoorLockViewModel(hardwareStorage, signals, scope)

    /**
     * Item Visibility Dependency Example:
     * [DoorCatalog.autoRelock] is only visible when [DoorCatalog.autoDoorLock] is ON (true)
     * AND when [DoorCatalog.autoDoorLock] itself is visible.
     */
    @Provides
    @IntoSet
    fun provideAutoRelockBinding(
        hardwareStorage: HardwarePropertyStorage,
        autoLockVm: HardwareItemViewModel<Boolean, Int>,
        scope: CoroutineScope = AppScope.scope
    ): ItemViewModelBinding<*> {
        val visibleFlow = combine(
            autoLockVm.valueFlow,
            autoLockVm.isVisibleFlow
        ) { isAutoLockOn, isAutoLockVisible ->
            isAutoLockOn && isAutoLockVisible
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            autoLockVm.valueFlow.value && autoLockVm.isVisibleFlow.value
        )

        return DoorCatalog.autoRelock bindsTo HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_RELOCK,
            storage = hardwareStorage,
            isVisibleFlow = visibleFlow,
            scope = scope
        )
    }

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
 * Binds ViewModels for Door settings running within an ApplicationScope.
 * Used by [VehicleHardwareSimulator] for simulator runtime and tests.
 */
object DoorViewModelBinder {
    fun bindAll(
        registry: ItemViewModelRegistry,
        hardwareStorage: HardwarePropertyStorage = InMemoryHardwareStorage().apply {
            setInitialValue(DoorVehicleProperties.AUTO_LOCK, true)
            setInitialValue(DoorVehicleProperties.CHILD_LOCK, false)
            setInitialValue(DoorVehicleProperties.AUTO_RELOCK, true)
        },
        autoLockVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
        scope: CoroutineScope = AppScope.scope,
        doorSettingRepository: DoorSettingRepository = DoorSettingRepositoryImpl()
    ) {
        val autoLockVm = HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_LOCK,
            storage = hardwareStorage,
            scope = scope,
            isVisibleFlow = autoLockVisibleFlow
        )

        val autoRelockVisibleFlow = combine(
            autoLockVm.valueFlow,
            autoLockVm.isVisibleFlow
        ) { isAutoLockOn, isAutoLockVisible ->
            isAutoLockOn && isAutoLockVisible
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            autoLockVm.valueFlow.value && autoLockVm.isVisibleFlow.value
        )

        val autoRelockVm = HardwareItemViewModel(
            property = DoorVehicleProperties.AUTO_RELOCK,
            storage = hardwareStorage,
            scope = scope,
            isVisibleFlow = autoRelockVisibleFlow
        )

        val bindings = setOf(
            DoorCatalog.autoDoorLock bindsTo autoLockVm,
            DoorCatalog.autoRelock bindsTo autoRelockVm,
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
        registry.registerAll(bindings)
    }
}
