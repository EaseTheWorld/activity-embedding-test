package com.example.lifecycleapp

import com.example.common.ui.settings.CommonSettingsModule
import com.example.common.ui.settings.InMemoryHardwareStorage
import com.example.core.item.ItemViewModelBinding
import com.example.core.item.MutableItemViewModel
import com.example.feature.door.DoorHiltModule
import com.example.feature.door.DoorSettingRepositoryImpl
import com.example.feature.seat.SeatHiltModule
import com.example.feature.seat.SeatSettingRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HiltItemViewModelBindingTest {

    @Test
    fun testHiltFeatureModulesAssembleItemViewModelRegistry() = runTest {
        val hardwareStorage = InMemoryHardwareStorage().apply {
            setInitialValue(com.example.feature.door.DoorVehicleProperties.AUTO_LOCK, true)
            setInitialValue(com.example.feature.door.DoorVehicleProperties.CHILD_LOCK, false)
            setInitialValue(com.example.feature.door.DoorVehicleProperties.AUTO_RELOCK, true)
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.MASSAGE_MODE, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.DRIVER_VENT, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.DRIVER_HEAT, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.PASSENGER_HEAT, "OFF")
        }

        val doorRepo = DoorSettingRepositoryImpl()
        val seatRepo = SeatSettingRepositoryImpl()

        val unconfinedScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
        )

        val autoLockVm = DoorHiltModule.provideAutoDoorLockViewModel(
            hardwareStorage = hardwareStorage,
            scope = unconfinedScope
        )

        // 1. Collect multibindings from DoorHiltModule
        val doorBindings: Set<ItemViewModelBinding<*>> = setOf(
            DoorHiltModule.provideUnlockOnParkBinding(doorRepo, unconfinedScope),
            DoorHiltModule.provideAutoDoorLockBinding(autoLockVm),
            DoorHiltModule.provideAutoRelockBinding(hardwareStorage, autoLockVm, unconfinedScope),
            DoorHiltModule.provideChildLockBinding(hardwareStorage)
        )

        // 2. Collect multibindings from SeatHiltModule
        val seatBindings: Set<ItemViewModelBinding<*>> =
            SeatHiltModule.provideSeatBindings(hardwareStorage, seatRepo, scope = unconfinedScope)

        // 3. Assemble all multibindings into CommonSettingsModule
        val allBindings: Set<ItemViewModelBinding<*>> = doorBindings + seatBindings
        val registry = CommonSettingsModule.provideItemViewModelRegistry(allBindings)

        // 4. Verify Door items are resolved
        val unlockOnParkVm = registry.getViewModel<Boolean>("unlock_on_park")
        assertNotNull("unlock_on_park must be registered from Door module", unlockOnParkVm)
        assertEquals(true, unlockOnParkVm!!.valueFlow.value)

        val autoLockResolved = registry.getViewModel<Boolean>("auto_lock")
        assertNotNull("auto_lock must be registered from Door module", autoLockResolved)
        assertEquals(true, autoLockResolved!!.valueFlow.value)

        val autoRelockVm = registry.getViewModel<Boolean>("auto_relock")
        assertNotNull("auto_relock must be registered from Door module", autoRelockVm)
        assertEquals(true, autoRelockVm!!.valueFlow.value)
        assertEquals(true, autoRelockVm.isVisibleFlow.value)

        // 5. Verify Seat items are resolved
        val easyEntryVm = registry.getViewModel<Boolean>(com.example.feature.seat.SeatCatalog.easyEntryExit.id)
        assertNotNull("easy_entry_exit must be registered from Seat module", easyEntryVm)
        assertEquals(true, easyEntryVm!!.valueFlow.value)

        // 6. Verify LocalStorageItemViewModel saves through Repository
        val mutableUnlock = unlockOnParkVm as MutableItemViewModel<Boolean>
        mutableUnlock.setValue(false)
        testScheduler.advanceUntilIdle()

        // Verify Repository was updated by ViewModel mutation
        assertEquals(false, doorRepo.unlockOnParkRepository.valueFlow.value)
        assertEquals(false, unlockOnParkVm.valueFlow.value)

        // 7. Verify Seat LocalStorageItemViewModel saves through Repository
        val mutableEasyEntry = easyEntryVm as MutableItemViewModel<Boolean>
        mutableEasyEntry.setValue(false)
        testScheduler.advanceUntilIdle()
        assertEquals(false, seatRepo.easyEntryExitRepository.valueFlow.value)
        assertEquals(false, easyEntryVm.valueFlow.value)

        // 8. Verify UseCase updates repository and propagates to ItemViewModel
        val setUnlockUseCase = com.example.feature.door.SetUnlockOnParkUseCase(doorRepo)
        setUnlockUseCase(true)
        testScheduler.advanceUntilIdle()
        assertEquals(true, doorRepo.unlockOnParkRepository.valueFlow.value)
        assertEquals(true, unlockOnParkVm.valueFlow.value)
    }

    @Test
    fun testItemVisibilityDependencyAutoRelockDependsOnAutoLock() = runTest {
        val hardwareStorage = InMemoryHardwareStorage().apply {
            setInitialValue(com.example.feature.door.DoorVehicleProperties.AUTO_LOCK, true)
            setInitialValue(com.example.feature.door.DoorVehicleProperties.AUTO_RELOCK, true)
        }
        val unconfinedScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
        )

        val autoLockVm = DoorHiltModule.provideAutoDoorLockViewModel(
            hardwareStorage = hardwareStorage,
            signals = com.example.common.ui.settings.DefaultVehicleSignals(),
            scope = unconfinedScope
        )
        val doorBindings = setOf(
            DoorHiltModule.provideAutoDoorLockBinding(autoLockVm),
            DoorHiltModule.provideAutoRelockBinding(hardwareStorage, autoLockVm, unconfinedScope)
        )
        val registry = CommonSettingsModule.provideItemViewModelRegistry(doorBindings)

        val autoLock = registry.getMutableViewModel<Boolean>("auto_lock")!!
        val autoRelock = registry.getViewModel<Boolean>("auto_relock")!!

        // 1. Initial State: auto_lock is ON -> auto_relock is visible
        assertTrue("auto_lock should be ON initially", autoLock.valueFlow.value)
        assertTrue("auto_relock should be visible when auto_lock is ON", autoRelock.isVisibleFlow.value)

        // 2. User toggles auto_lock OFF -> auto_relock is immediately hidden
        autoLock.setValue(false)
        assertFalse("auto_lock should be OFF", autoLock.valueFlow.value)
        assertFalse("auto_relock must be hidden when auto_lock is OFF", autoRelock.isVisibleFlow.value)

        // 3. User toggles auto_lock back to ON -> auto_relock is restored
        autoLock.setValue(true)
        assertTrue("auto_lock should be ON again", autoLock.valueFlow.value)
        assertTrue("auto_relock must be visible again when auto_lock is ON", autoRelock.isVisibleFlow.value)
    }

    @Test
    fun testHiltBindingsReactToVehicleSignalsSimulation() = runTest {
        val hardwareStorage = InMemoryHardwareStorage().apply {
            setInitialValue(com.example.feature.door.DoorVehicleProperties.AUTO_LOCK, true)
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.MASSAGE_MODE, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.PASSENGER_HEAT, "OFF")
        }
        val doorRepo = DoorSettingRepositoryImpl()
        val seatRepo = SeatSettingRepositoryImpl()

        val unconfinedScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined
        )
        // Reset simulator signals to baseline
        VehicleHardwareSimulator.resetForTesting(unconfinedScope)

        val doorBindings = setOf(
            DoorHiltModule.provideAutoDoorLockBinding(hardwareStorage, VehicleHardwareSimulator),
            DoorHiltModule.provideUnlockOnParkBinding(doorRepo, unconfinedScope)
        )
        val seatBindings = SeatHiltModule.provideSeatBindings(
            hardwareStorage = hardwareStorage,
            repository = seatRepo,
            signals = VehicleHardwareSimulator,
            scope = unconfinedScope
        )

        // Assemble unified registry and attach to VehicleHardwareSimulator
        val registry = CommonSettingsModule.provideItemViewModelRegistry(doorBindings + seatBindings)
        VehicleHardwareSimulator.attachRegistry(registry)

        val autoLockVm = registry.getViewModel<Boolean>("auto_lock")!!
        val passengerHeatVm = registry.getChoiceViewModel<String>(com.example.feature.seat.SeatCatalog.passengerSeatHeat)!!
        val massageVm = registry.getMutableChoiceViewModel<String>(com.example.feature.seat.SeatCatalog.massageMode)!!

        // 1. Initial State
        assertTrue(autoLockVm.isVisibleFlow.value)
        assertTrue(passengerHeatVm.isVisibleFlow.value)

        // 2. Simulate adb: auto_lock visible = false
        VehicleHardwareSimulator.applySimulation(itemId = "auto_lock", visible = false)
        assertEquals(false, autoLockVm.isVisibleFlow.value)

        // 3. Simulate adb: passenger_present = false
        VehicleHardwareSimulator.applySimulation(passengerPresent = false)
        assertEquals(false, passengerHeatVm.isVisibleFlow.value)

        // 4. Simulate adb: speed = 60 km/h (speed lockout)
        VehicleHardwareSimulator.applySimulation(speed = 60)
        val states = massageVm.optionStates.value
        val stretchOption = states.find { it.id == "STRETCH" }
        assertEquals(false, stretchOption?.isEnabled)

        // 5. Simulate adb: direct value mutation
        VehicleHardwareSimulator.applySimulation(itemId = "seat_massage", value = "WAVE")
        assertEquals("WAVE", massageVm.valueFlow.value)
    }
}