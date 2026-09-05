package com.example.lifecycleapp

import android.app.Application
import android.util.Log
import com.example.core.item.CategoryItemRegistry
import com.example.feature.door.DoorItemRegistry
import com.example.feature.seat.SeatItemRegistry

class LifecycleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("LifecycleLog", "[LifecycleApp] onCreate()")
        CategoryItemRegistry.register(DoorItemRegistry)
        CategoryItemRegistry.register(SeatItemRegistry)
    }
}
