package com.example.lifecycleapp

import com.example.feature.seat.SeatCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleHardwareSimulatorTest {

    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        VehicleHardwareSimulator.resetForTesting(testScope)
    }

    @Test
    fun testInitRegistersAllViewModels() {
        val massageVm = VehicleHardwareSimulator.viewModelRegistry.getChoiceViewModel<String>(SeatCatalog.massageMode)
        assertNotNull("SeatCatalog.massageMode must be registered", massageVm)

        val heatVm = VehicleHardwareSimulator.viewModelRegistry.getChoiceViewModel<String>(SeatCatalog.driverSeatHeat)
        assertNotNull("SeatCatalog.driverSeatHeat must be registered", heatVm)

        val passengerHeatVm = VehicleHardwareSimulator.viewModelRegistry.getChoiceViewModel<String>(SeatCatalog.passengerSeatHeat)
        assertNotNull("SeatCatalog.passengerSeatHeat must be registered", passengerHeatVm)
    }

    @Test
    fun testPassengerOccupancyTogglesVisibility() = runTest {
        val passengerHeatVm = VehicleHardwareSimulator.viewModelRegistry.getChoiceViewModel<String>(SeatCatalog.passengerSeatHeat)!!

        // Initially passenger is present
        VehicleHardwareSimulator.applySimulation(passengerPresent = true)
        assertTrue(passengerHeatVm.isVisibleFlow.value)
        assertTrue(VehicleHardwareSimulator.passengerOccupiedFlow.value)

        // Passenger departs
        VehicleHardwareSimulator.applySimulation(passengerPresent = false)
        assertFalse(passengerHeatVm.isVisibleFlow.value)
        assertFalse(VehicleHardwareSimulator.passengerOccupiedFlow.value)

        // Passenger returns
        VehicleHardwareSimulator.applySimulation(passengerPresent = true)
        assertTrue(passengerHeatVm.isVisibleFlow.value)
        assertTrue(VehicleHardwareSimulator.passengerOccupiedFlow.value)
    }

    @Test
    fun testDrivingSpeedLockout() = runTest {
        val massageVm = VehicleHardwareSimulator.viewModelRegistry.getMutableChoiceViewModel<String>(SeatCatalog.massageMode)!!

        // Vehicle Parked: speed = 0
        VehicleHardwareSimulator.applySimulation(speed = 0)
        assertTrue(massageVm.optionStates.value.all { it.isEnabled })

        // Driving: speed = 60
        VehicleHardwareSimulator.applySimulation(speed = 60)
        val states = massageVm.optionStates.value
        assertFalse(states.find { it.id == "WAVE" }!!.isEnabled)
        assertFalse(states.find { it.id == "LUMBAR" }!!.isEnabled)
        assertFalse(states.find { it.id == "STRETCH" }!!.isEnabled)
        assertTrue(states.find { it.id == "OFF" }!!.isEnabled)

        // Parked again: speed = 0
        VehicleHardwareSimulator.applySimulation(speed = 0)
        assertTrue(massageVm.optionStates.value.all { it.isEnabled })
    }

    @Test
    fun testHiddenOptionSimulation() = runTest {
        val massageVm = VehicleHardwareSimulator.viewModelRegistry.getMutableChoiceViewModel<String>(SeatCatalog.massageMode)!!

        // Hide STRETCH option
        VehicleHardwareSimulator.applySimulation(itemId = "seat_massage", hideOptions = "STRETCH")
        val states = massageVm.optionStates.value
        assertFalse(states.find { it.id == "STRETCH" }!!.isVisible)
        assertTrue(states.find { it.id == "WAVE" }!!.isVisible)

        // Restore all options
        VehicleHardwareSimulator.applySimulation(itemId = "seat_massage", hideOptions = "none")
        assertTrue(massageVm.optionStates.value.all { it.isVisible })
    }

    @Test
    fun testDirectValueMutationSimulation() = runTest {
        val driverHeatVm = VehicleHardwareSimulator.viewModelRegistry.getMutableChoiceViewModel<String>(SeatCatalog.driverSeatHeat)!!

        // Mutate to LEVEL 3
        VehicleHardwareSimulator.applySimulation(itemId = "driver_seat_heat", value = "LEVEL 3")
        assertEquals("LEVEL 3", driverHeatVm.selectedValue)
        val level3State = driverHeatVm.optionStates.value.find { it.id == "LEVEL 3" }!!
        assertTrue(level3State.isSelected)

        // Mutate back to OFF
        VehicleHardwareSimulator.applySimulation(itemId = "driver_seat_heat", value = "OFF")
        assertEquals("OFF", driverHeatVm.selectedValue)
    }
}
