package com.example.lifecycleapp

import android.app.Application
import android.util.Log
import com.example.core.item.CategoryItemRegistry
import com.example.core.item.ItemViewModelRegistry
import com.example.feature.door.DoorItemRegistry
import com.example.feature.seat.SeatItemRegistry
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LifecycleApp : Application() {

    @Inject
    lateinit var hiltViewModelRegistry: ItemViewModelRegistry

    override fun onCreate() {
        super.onCreate()
        Log.d("LifecycleLog", "[LifecycleApp] onCreate()")
        CategoryItemRegistry.register(DoorItemRegistry)
        CategoryItemRegistry.register(SeatItemRegistry)
        VehicleHardwareSimulator.init()
        VehicleHardwareSimulator.attachRegistry(hiltViewModelRegistry)
    }
}
