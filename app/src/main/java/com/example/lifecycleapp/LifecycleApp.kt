package com.example.lifecycleapp

import com.example.feature.home.CategoryStackManager
import com.example.feature.home.HomeNavigationBridge

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
        lateinit var instance: LifecycleApp
            private set
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
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("LifecycleLog", "[LifecycleApp] onCreate()")
        SplitInitializer.ensureInitialized(this)
        HomeNavigationBridge.registerEnsureSplitRulesHandler { context, isNarrowMode ->
            SplitInitializer.ensureInitialized(context, isNarrowMode)
        }
        CategoryItemRegistry.register(DoorItemRegistry)
        CategoryItemRegistry.register(SeatItemRegistry)
        VehicleHardwareSimulator.init()
        VehicleHardwareSimulator.attachRegistry(hiltViewModelRegistry)
        HomeNavigationBridge.registerCategoryDismissHandler { _ ->
            CategoryStackManager.dismissCurrentCategory(this)
        }
        HomeNavigationBridge.registerModeTransitionHandler { activity, newConfig ->
            ModeTransitionManager.checkAndHandleTransition(activity, newConfig)
        }

        registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                val currentActivity = getTopSecondaryActivity()
                    ?: BaseCategoryMenuActivity.getActiveMenuActivity()
                if (currentActivity != null) {
                    Log.d("LifecycleLog", "[LifecycleApp] onConfigurationChanged delegating to ModeTransitionManager")
                    ModeTransitionManager.checkAndHandleTransition(currentActivity, newConfig)
                }
            }
            override fun onLowMemory() {}
            override fun onTrimMemory(level: Int) {}
        })

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                if (activity is ComponentActivity &&
                    activity !is com.example.lifecycleapp.BaseCategoryMenuActivity &&
                    activity !is com.example.feature.home.HomeActivity
                ) {
                    secondaryActivities.removeAll { it.get() === activity || it.get() == null }
                    secondaryActivities.addFirst(WeakReference(activity))
                    Log.d("LifecycleLog", "[LifecycleApp] Top secondary activity registered: ${activity.javaClass.simpleName}")
                }
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
