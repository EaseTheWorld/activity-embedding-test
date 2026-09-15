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
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.MASSAGE_MODE, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.DRIVER_VENT, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.DRIVER_HEAT, "OFF")
            setInitialValue(com.example.feature.seat.SeatVehicleProperties.PASSENGER_HEAT, "OFF")
        }

        val doorRepo = DoorSettingRepositoryImpl()
        val seatRepo = SeatSettingRepositoryImpl()

        // 1. Collect multibindings from DoorHiltModule
        val doorBindings: Set<ItemViewModelBinding<*>> = setOf(
            DoorHiltModule.provideUnlockOnParkBinding(doorRepo, this),
            DoorHiltModule.provideAutoDoorLockBinding(hardwareStorage),
            DoorHiltModule.provideChildLockBinding(hardwareStorage)
        )

        // 2. Collect multibindings from SeatHiltModule
        val seatBindings: Set<ItemViewModelBinding<*>> =
            SeatHiltModule.provideSeatBindings(hardwareStorage, seatRepo, this)

        // 3. Assemble all multibindings into CommonSettingsModule
        val allBindings: Set<ItemViewModelBinding<*>> = doorBindings + seatBindings
        val registry = CommonSettingsModule.provideItemViewModelRegistry(allBindings)

        // 4. Verify Door items are resolved
        val unlockOnParkVm = registry.getViewModel<Boolean>("unlock_on_park")
        assertNotNull("unlock_on_park must be registered from Door module", unlockOnParkVm)
        assertEquals(true, unlockOnParkVm!!.valueFlow.value)

        val autoLockVm = registry.getViewModel<Boolean>("auto_lock")
        assertNotNull("auto_lock must be registered from Door module", autoLockVm)
        assertEquals(true, autoLockVm!!.valueFlow.value)

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
}