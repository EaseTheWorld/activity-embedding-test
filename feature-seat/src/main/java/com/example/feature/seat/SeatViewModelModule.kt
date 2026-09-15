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

// ============================================================================
// Layer 3: Assembler / DI Module Layer (The Sole Coupling Point)
// ============================================================================

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
        scope: CoroutineScope = AppScope.scope
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
            initialValue = true,
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
