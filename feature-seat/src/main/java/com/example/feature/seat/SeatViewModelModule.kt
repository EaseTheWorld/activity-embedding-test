package com.example.feature.seat

import com.example.common.ui.settings.AppScope
import com.example.common.ui.settings.ChoiceHardwareItemViewModel
import com.example.common.ui.settings.HardwarePropertyStorage
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.common.ui.settings.LocalStorageItemViewModel
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.bindsTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

// ============================================================================
// Layer 3: Assembler / Hilt DI Module Layer
// ============================================================================

@Module
@InstallIn(SingletonComponent::class)
abstract class SeatRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSeatSettingRepository(
        impl: SeatSettingRepositoryImpl
    ): SeatSettingRepository
}

/**
 * Hilt DI module providing Seat [ItemViewModelBinding] multibindings to the global registry.
 */
@Module
@InstallIn(SingletonComponent::class)
object SeatHiltModule {

    @Provides
    @ElementsIntoSet
    fun provideSeatBindings(
        hardwareStorage: HardwarePropertyStorage,
        repository: SeatSettingRepository,
        signals: com.example.common.ui.settings.VehicleSignals = com.example.common.ui.settings.DefaultVehicleSignals(),
        scope: CoroutineScope = AppScope.scope
    ): Set<ItemViewModelBinding<*>> = setOf(
        SeatCatalog.massageMode bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.MASSAGE_MODE,
            supportedOptionIds = SeatCatalog.massageMode.optionIds,
            storage = hardwareStorage,
            disabledOptionIdsFlow = signals.disabledMassageOptionsFlow,
            hiddenOptionIdsFlow = signals.hiddenMassageOptionsFlow,
            scope = scope
        ),
        SeatCatalog.driverSeatVent bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.DRIVER_VENT,
            supportedOptionIds = SeatCatalog.driverSeatVent.optionIds,
            storage = hardwareStorage
        ),
        SeatCatalog.driverSeatHeat bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.DRIVER_HEAT,
            supportedOptionIds = SeatCatalog.driverSeatHeat.optionIds,
            storage = hardwareStorage
        ),
        SeatCatalog.passengerSeatHeat bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.PASSENGER_HEAT,
            supportedOptionIds = SeatCatalog.passengerSeatHeat.optionIds,
            storage = hardwareStorage,
            isVisibleFlow = signals.passengerOccupiedFlow
        ),
        SeatCatalog.easyEntryExit bindsTo LocalStorageItemViewModel(
            repository = repository.easyEntryExitRepository,
            scope = scope
        ),
        SeatCatalog.seatLumbar bindsTo SeatItemRegistry.seatLumbarViewModel
    )
}

/**
 * Provides the complete set of [ItemViewModelBinding] for Seat settings.
 * Bridges UI Catalog items with their corresponding ChoiceHardwareItemViewModels.
 */
object SeatViewModelModule {
    fun provideSeatBindings(
        hardwareStorage: HardwarePropertyStorage,
        disabledMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
        hiddenMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
        passengerSeatHeatVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
        lumbarViewModel: ItemViewModel<SeatLumbarSupport> = SeatItemRegistry.seatLumbarViewModel,
        scope: CoroutineScope = AppScope.scope,
        seatSettingRepository: SeatSettingRepository = SeatSettingRepositoryImpl()
    ): Set<ItemViewModelBinding<*>> = setOf(
        SeatCatalog.massageMode bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.MASSAGE_MODE,
            supportedOptionIds = SeatCatalog.massageMode.optionIds,
            storage = hardwareStorage,
            disabledOptionIdsFlow = disabledMassageOptionsFlow,
            hiddenOptionIdsFlow = hiddenMassageOptionsFlow,
            scope = scope
        ),
        SeatCatalog.driverSeatVent bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.DRIVER_VENT,
            supportedOptionIds = SeatCatalog.driverSeatVent.optionIds,
            storage = hardwareStorage,
            scope = scope
        ),
        SeatCatalog.driverSeatHeat bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.DRIVER_HEAT,
            supportedOptionIds = SeatCatalog.driverSeatHeat.optionIds,
            storage = hardwareStorage,
            scope = scope
        ),
        SeatCatalog.passengerSeatHeat bindsTo ChoiceHardwareItemViewModel(
            property = SeatVehicleProperties.PASSENGER_HEAT,
            supportedOptionIds = SeatCatalog.passengerSeatHeat.optionIds,
            storage = hardwareStorage,
            scope = scope,
            isVisibleFlow = passengerSeatHeatVisibleFlow
        ),
        SeatCatalog.easyEntryExit bindsTo LocalStorageItemViewModel(
            repository = seatSettingRepository.easyEntryExitRepository,
            scope = scope
        ),
        SeatCatalog.seatLumbar bindsTo lumbarViewModel
    )
}

/**
 * Binds ViewModels for Seat settings running within an ApplicationScope.
 */
object SeatViewModelBinder {
    fun bindAll(
        registry: ItemViewModelRegistry,
        hardwareStorage: HardwarePropertyStorage = InMemoryHardwareStorage().apply {
            setInitialValue(SeatVehicleProperties.MASSAGE_MODE, "OFF")
            setInitialValue(SeatVehicleProperties.DRIVER_VENT, "OFF")
            setInitialValue(SeatVehicleProperties.DRIVER_HEAT, "OFF")
            setInitialValue(SeatVehicleProperties.PASSENGER_HEAT, "OFF")
        },
        disabledMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
        hiddenMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
        passengerSeatHeatVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
        lumbarViewModel: ItemViewModel<SeatLumbarSupport> = SeatItemRegistry.seatLumbarViewModel,
        scope: CoroutineScope = AppScope.scope
    ) {
        val bindings = SeatViewModelModule.provideSeatBindings(
            hardwareStorage = hardwareStorage,
            disabledMassageOptionsFlow = disabledMassageOptionsFlow,
            hiddenMassageOptionsFlow = hiddenMassageOptionsFlow,
            passengerSeatHeatVisibleFlow = passengerSeatHeatVisibleFlow,
            lumbarViewModel = lumbarViewModel,
            scope = scope
        )
        registry.registerAll(bindings)
    }
}
