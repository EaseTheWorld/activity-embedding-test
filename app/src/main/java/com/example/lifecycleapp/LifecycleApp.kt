package com.example.lifecycleapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.core.item.CategoryItemRegistry
import com.example.core.item.ItemViewModelRegistry
import com.example.feature.door.DoorItemRegistry
import com.example.feature.seat.SeatItemRegistry
import dagger.hilt.android.HiltAndroidApp
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import javax.inject.Inject

@HiltAndroidApp
class LifecycleApp : Application() {

    @Inject
    lateinit var hiltViewModelRegistry: ItemViewModelRegistry

    companion object {
        private val secondaryActivities = ArrayDeque<WeakReference<ComponentActivity>>()

        fun getTopSecondaryActivity(): ComponentActivity? {
            while (secondaryActivities.isNotEmpty()) {
                val act = secondaryActivities.firstOrNull()?.get()
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    return act
                }
                if (secondaryActivities.isNotEmpty()) {
                    secondaryActivities.removeFirst()
                }
            }
            return null
        }

        private fun pushSecondaryActivity(activity: Activity) {
            if (activity is ComponentActivity &&
                activity !is com.example.lifecycleapp.PrimaryActivity &&
                activity !is com.example.lifecycleapp.PrimaryOverlayActivity
            ) {
                secondaryActivities.removeAll { it.get() === activity || it.get() == null }
                secondaryActivities.addFirst(WeakReference(activity))
                Log.d("LifecycleLog", "[LifecycleApp] Top secondary activity updated: ${activity.javaClass.simpleName}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LifecycleLog", "[LifecycleApp] onCreate()")
        CategoryItemRegistry.register(DoorItemRegistry)
        CategoryItemRegistry.register(SeatItemRegistry)
        VehicleHardwareSimulator.init()
        VehicleHardwareSimulator.attachRegistry(hiltViewModelRegistry)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                pushSecondaryActivity(activity)
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                pushSecondaryActivity(activity)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                secondaryActivities.removeAll { it.get() === activity || it.get() == null }
            }
        })
    }
}
