package com.example.common.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-Level Automotive Sensor and Condition Signals.
 * Decouples feature modules from concrete simulator implementations.
 */
interface VehicleSignals {
    val autoLockVisibleFlow: StateFlow<Boolean>
    val passengerOccupiedFlow: StateFlow<Boolean>
    val disabledMassageOptionsFlow: StateFlow<Set<String>>
    val hiddenMassageOptionsFlow: StateFlow<Set<String>>
}

/**
 * Default reference implementation with standard resting values.
 * Used for unit testing and fallback environments.
 */
class DefaultVehicleSignals(
    override val autoLockVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true),
    override val passengerOccupiedFlow: StateFlow<Boolean> = MutableStateFlow(true),
    override val disabledMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
    override val hiddenMassageOptionsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet())
) : VehicleSignals
