package com.example.lifecycleapp

import android.content.Intent
import android.util.Log
import com.example.common.ui.settings.AppScope
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableChoiceItemViewModel
import com.example.core.item.MutableItemViewModel
import com.example.feature.door.DoorCatalog
import com.example.feature.door.DoorVehicleProperties
import com.example.feature.door.DoorViewModelBinder
import com.example.feature.seat.SeatCatalog
import com.example.feature.seat.SeatVehicleProperties
import com.example.feature.seat.SeatViewModelBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton Vehicle Hardware Simulator for developer diagnostics and adb broadcast testing.
 *
 * Maintains shared vehicle HAL state, option lockout state flows, and item visibility flows.
 */
object VehicleHardwareSimulator : com.example.common.ui.settings.VehicleSignals {
    private const val TAG = "HardwareSimulator"

    val hardwareStorage = InMemoryHardwareStorage()
    var viewModelRegistry: ItemViewModelRegistry = ItemViewModelRegistry()
        internal set

    // High-Level Automotive Sensor Flows
    override val passengerOccupiedFlow = MutableStateFlow(true)
    override val autoLockVisibleFlow = MutableStateFlow(true)
    override val disabledMassageOptionsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val hiddenMassageOptionsFlow = MutableStateFlow<Set<String>>(emptySet())

    var scope: CoroutineScope = AppScope.scope
    private var initialized = false

    /**
     * Attaches the Hilt-managed singleton [ItemViewModelRegistry] to the simulator,
     * ensuring broadcast simulations update the exact ViewModels rendered on screen.
     */
    fun attachRegistry(registry: ItemViewModelRegistry) {
        this.viewModelRegistry = registry
        Log.i(TAG, "VehicleHardwareSimulator attached to unified Hilt ItemViewModelRegistry!")
    }

    @Synchronized
    fun resetForTesting(testScope: CoroutineScope) {
        this.scope = testScope
        initialized = false
        passengerOccupiedFlow.value = true
        autoLockVisibleFlow.value = true
        disabledMassageOptionsFlow.value = emptySet()
        hiddenMassageOptionsFlow.value = emptySet()
        viewModelRegistry = ItemViewModelRegistry()
        init(testScope)
    }

    @Synchronized
    fun init(scope: CoroutineScope = this.scope) {
        if (initialized) return
        this.scope = scope
        initialized = true

        Log.i(TAG, "Initializing VehicleHardwareSimulator...")

        // Default initial values in simulated hardware HAL
        hardwareStorage.setInitialValue(DoorVehicleProperties.AUTO_LOCK, true)
        hardwareStorage.setInitialValue(DoorVehicleProperties.CHILD_LOCK, false)
        hardwareStorage.setInitialValue(DoorVehicleProperties.AUTO_RELOCK, true)
        hardwareStorage.setInitialValue(SeatVehicleProperties.MASSAGE_MODE, "OFF")
        hardwareStorage.setInitialValue(SeatVehicleProperties.DRIVER_VENT, "OFF")
        hardwareStorage.setInitialValue(SeatVehicleProperties.DRIVER_HEAT, "OFF")
        hardwareStorage.setInitialValue(SeatVehicleProperties.PASSENGER_HEAT, "OFF")

        // Bind Door settings with dynamic autoLock visibility
        DoorViewModelBinder.bindAll(
            registry = viewModelRegistry,
            hardwareStorage = hardwareStorage,
            autoLockVisibleFlow = autoLockVisibleFlow,
            scope = scope
        )

        // Bind Seat settings with dynamic option lockout, option hidden, and passenger occupancy visibility
        SeatViewModelBinder.bindAll(
            registry = viewModelRegistry,
            hardwareStorage = hardwareStorage,
            disabledMassageOptionsFlow = disabledMassageOptionsFlow,
            hiddenMassageOptionsFlow = hiddenMassageOptionsFlow,
            passengerSeatHeatVisibleFlow = passengerOccupiedFlow,
            scope = scope
        )

        Log.i(TAG, "VehicleHardwareSimulator initialized successfully with full ViewModels!")
    }

    /**
     * Dispatches hardware simulation changes from strongly-typed parameters.
     */
    fun applySimulation(
        passengerPresent: Boolean? = null,
        speed: Int? = null,
        itemId: String? = null,
        visible: Boolean? = null,
        disableOptions: String? = null,
        hideOptions: String? = null,
        value: String? = null
    ) {
        init()

        // 1. High-Level Scenario: Passenger Occupancy Sensor
        if (passengerPresent != null) {
            passengerOccupiedFlow.value = passengerPresent
            Log.i(TAG, ">> [Sensor] Passenger occupancy changed: isPresent=$passengerPresent (Passenger Seat Heat visible=$passengerPresent)")
        }

        // 2. High-Level Scenario: Driving Speed Restriction (Lockout)
        if (speed != null) {
            if (speed > 0) {
                // Lock out intensive massage modes while driving
                disabledMassageOptionsFlow.value = setOf("WAVE", "LUMBAR", "STRETCH")
                Log.i(TAG, ">> [Driving Lockout] Speed=$speed km/h -> Disabled massage modes: WAVE, LUMBAR, STRETCH")
            } else {
                disabledMassageOptionsFlow.value = emptySet()
                Log.i(TAG, ">> [Driving Lockout] Vehicle Parked (speed=0) -> All massage modes re-enabled")
            }
        }

        // 3. Granular Item-level controls: itemId
        if (!itemId.isNullOrEmpty()) {
            // (a) Item Visibility: visible
            if (visible != null) {
                when (itemId) {
                    "passenger_seat_heat" -> passengerOccupiedFlow.value = visible
                    "auto_lock" -> autoLockVisibleFlow.value = visible
                }
                Log.i(TAG, ">> [Item Visibility] Item '$itemId' visible set to: $visible")
            }

            // (b) Option Lockout: disableOptions
            if (disableOptions != null) {
                val set = if (disableOptions.isBlank() || disableOptions.equals("none", ignoreCase = true)) {
                    emptySet()
                } else {
                    disableOptions.split(",").map { it.trim() }.toSet()
                }
                if (itemId == "seat_massage") {
                    disabledMassageOptionsFlow.value = set
                }
                Log.i(TAG, ">> [Option Lockout] Item '$itemId' disabled options: $set")
            }

            // (c) Option Visibility: hideOptions
            if (hideOptions != null) {
                val set = if (hideOptions.isBlank() || hideOptions.equals("none", ignoreCase = true)) {
                    emptySet()
                } else {
                    hideOptions.split(",").map { it.trim() }.toSet()
                }
                if (itemId == "seat_massage") {
                    hiddenMassageOptionsFlow.value = set
                }
                Log.i(TAG, ">> [Option Hidden] Item '$itemId' hidden options: $set")
            }

            // (d) Direct Value Mutation: value
            if (value != null) {
                val vm = viewModelRegistry.getViewModel<Any>(itemId)
                val applied = (vm as? MutableItemViewModel<*>)?.updateFromParameters(mapOf("value" to value)) ?: false
                Log.i(TAG, ">> [Value Mutation] Item '$itemId' updateFromParameters(value='$value') result: $applied")
            }
        }
    }

    /**
     * Dispatches hardware simulation changes from broadcast extras.
     */
    fun applySimulation(intent: Intent) {
        val extras = intent.extras
        if (extras == null || extras.isEmpty) {
            Log.w(TAG, "Received broadcast with empty extras")
            return
        }

        applySimulation(
            passengerPresent = if (intent.hasExtra("passenger_present")) intent.getBooleanExtra("passenger_present", true) else null,
            speed = if (intent.hasExtra("speed")) intent.getIntExtra("speed", 0) else null,
            itemId = intent.getStringExtra("item"),
            visible = if (intent.hasExtra("visible")) intent.getBooleanExtra("visible", true) else null,
            disableOptions = intent.getStringExtra("disable_options"),
            hideOptions = intent.getStringExtra("hide_options"),
            value = intent.getStringExtra("value")
        )
    }
}
