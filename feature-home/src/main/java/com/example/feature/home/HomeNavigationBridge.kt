package com.example.feature.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Event bridge between Primary menu (`PrimaryActivity` / `PrimaryOverlayActivity`)
 * and `HomeActivity`.
 *
 * All participants register their listeners in `onCreate()` and unregister in `onDestroy()`,
 * avoiding any static `Activity` or `WeakReference<Activity>` fields.
 */
object HomeNavigationBridge {
    private var modeTransitionHandler: ((Activity, android.content.res.Configuration) -> Boolean)? = null
    private var ensureSplitRulesHandler: ((Context, Boolean) -> Unit)? = null

    fun registerEnsureSplitRulesHandler(handler: (Context, Boolean) -> Unit) {
        ensureSplitRulesHandler = handler
    }

    fun unregisterEnsureSplitRulesHandler() {
        ensureSplitRulesHandler = null
    }

    fun ensureSplitRules(context: Context, isNarrowMode: Boolean) {
        ensureSplitRulesHandler?.invoke(context, isNarrowMode)
    }

    fun registerModeTransitionHandler(handler: (Activity, android.content.res.Configuration) -> Boolean) {
        modeTransitionHandler = handler
    }

    fun unregisterModeTransitionHandler() {
        modeTransitionHandler = null
    }

    fun handleModeTransition(activity: Activity, config: android.content.res.Configuration): Boolean {
        return modeTransitionHandler?.invoke(activity, config) ?: false
    }

    private val homeRevealedListeners = CopyOnWriteArraySet<() -> Unit>()
    private val categorySelectedListeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val appExitListeners = CopyOnWriteArraySet<() -> Unit>()

    fun registerAppExitListener(listener: () -> Unit) {
        appExitListeners.add(listener)
    }

    fun unregisterAppExitListener(listener: () -> Unit) {
        appExitListeners.remove(listener)
    }

    fun notifyAppExit() {
        for (listener in appExitListeners) {
            listener.invoke()
        }
    }

    @Volatile
    var onHomeRevealed: (() -> Unit)? = null
        set(value) {
            field = value
        }

    @Volatile
    var onCategorySelected: ((String) -> Unit)? = null
        set(value) {
            field = value
        }

    fun registerHomeRevealedListener(listener: () -> Unit) {
        homeRevealedListeners.add(listener)
    }

    fun unregisterHomeRevealedListener(listener: () -> Unit) {
        homeRevealedListeners.remove(listener)
    }

    fun registerCategorySelectedListener(listener: (String) -> Unit) {
        categorySelectedListeners.add(listener)
    }

    fun unregisterCategorySelectedListener(listener: (String) -> Unit) {
        categorySelectedListeners.remove(listener)
    }

    private val categoryDismissListeners = CopyOnWriteArraySet<() -> Unit>()
    private var categoryDismissHandler: ((Int) -> Unit)? = null
    private var taskRootLaunchListener: ((Intent) -> Unit)? = null
    private var categorySwitchHandler: (() -> Unit)? = null
    private var menuLauncher: (() -> Activity?)? = null

    fun registerMenuLauncher(launcher: () -> Activity?) {
        menuLauncher = launcher
    }

    fun unregisterMenuLauncher() {
        menuLauncher = null
    }

    fun registerCategorySwitchHandler(handler: () -> Unit) {
        categorySwitchHandler = handler
    }

    fun unregisterCategorySwitchHandler(handler: () -> Unit) {
        if (categorySwitchHandler === handler) {
            categorySwitchHandler = null
        }
    }

    fun notifyHomeRevealed() {
        onHomeRevealed?.invoke()
        for (listener in homeRevealedListeners) {
            listener.invoke()
        }
    }

    fun notifyCategorySelected(categoryId: String) {
        onCategorySelected?.invoke(categoryId)
        for (listener in categorySelectedListeners) {
            listener.invoke(categoryId)
        }
    }

    fun registerCategoryDismissHandler(handler: (Int) -> Unit) {
        categoryDismissHandler = handler
    }

    fun unregisterCategoryDismissHandler() {
        categoryDismissHandler = null
    }

    /**
     * Optional listener for stack dismissal events.
     */
    fun registerCategoryHostDismissListener(listener: () -> Unit) {
        categoryDismissListeners.add(listener)
    }

    /**
     * Unsubscribes stack dismissal listener.
     */
    fun unregisterCategoryHostDismissListener(listener: () -> Unit) {
        categoryDismissListeners.remove(listener)
    }

    /**
     * Notifies stack dismissal handlers to finish the active category `ActivityStack` / `TaskFragment`.
     */
    fun notifyDismissCategoryStacks(taskId: Int = -1) {
        val snapshot = categoryDismissListeners.toList()
        categoryDismissListeners.clear()
        for (listener in snapshot) {
            listener.invoke()
        }
        categoryDismissHandler?.invoke(taskId)
    }

    /**
     * Called by [HomeActivity] in `onCreate()` to handle category launches from the root Task in Narrow Mode.
     */
    fun registerTaskRootLaunchListener(listener: (Intent) -> Unit) {
        taskRootLaunchListener = listener
    }

    /**
     * Returns true if the [HomeActivity] Task Root session is currently active.
     */
    fun isSessionActive(): Boolean = taskRootLaunchListener != null

    /**
     * Called by [HomeActivity] in `onDestroy()` to unsubscribe its launch listener.
     */
    fun unregisterTaskRootLaunchListener(listener: (Intent) -> Unit) {
        if (taskRootLaunchListener === listener) {
            taskRootLaunchListener = null
        }
    }

    /**
     * Notifies [HomeActivity] to invalidate any in-flight category launch generation
     * right before a `CLEAR_TOP` switch or return to Home.
     */
    fun prepareCategorySwitch() {
        categorySwitchHandler?.invoke()
    }

    /**
     * Launches [targetIntent] via unified CLEAR_TOP | SINGLE_TOP on [HomeActivity].
     * Works identically and cleanly in both Wide Mode and Narrow Mode!
     */
    @Suppress("DEPRECATION")
    fun launchCategory(caller: Context, targetIntent: Intent) {
        if (caller is HomeActivity) {
            caller.launchCategoryForResult(targetIntent)
        } else {
            prepareCategorySwitch()
            val categoryId = targetIntent.getStringExtra("category_id")
            val homeIntent = Intent(caller, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(HomeActivity.EXTRA_TARGET_INTENT, targetIntent)
                if (categoryId != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, categoryId)
                }
            }
            caller.startActivity(homeIntent)
            if (caller is Activity) {
                caller.overridePendingTransition(0, 0)
            }
        }
    }
}

/**
 * Tracks active category launches on [HomeActivity] using monotonically increasing generation
 * numbers (`category_result_gen_<N>`). Only a result matching both [currentGen] and [activeLaunchGen]
 * is treated as a genuine Back return to Home; results from superseded generations (`CLEAR_TOP`
 * switches, early/late callback arrivals, or dropped results) are ignored without counter drift.
 */
internal class CategoryResultTracker(
    var currentGen: Int = 0,
    var activeLaunchGen: Int = 0
) {
    companion object {
        private const val KEY_PREFIX = "category_result_gen_"
        fun keyOf(gen: Int): String = "$KEY_PREFIX$gen"
    }

    val isCategoryRunning: Boolean
        get() = activeLaunchGen > 0 && activeLaunchGen == currentGen

    fun onPrepareSwitch(): Int {
        activeLaunchGen = 0
        return ++currentGen
    }

    fun nextLaunchGen(): Int {
        val gen = ++currentGen
        activeLaunchGen = gen
        return gen
    }

    fun onLaunchFailed(gen: Int) {
        if (activeLaunchGen == gen) {
            activeLaunchGen = 0
        }
        if (currentGen == gen) {
            currentGen++
        }
    }

    fun onResultDelivered(gen: Int): Boolean {
        if (gen <= 0 || gen != currentGen || gen != activeLaunchGen) {
            return false
        }
        activeLaunchGen = 0
        currentGen++
        return true
    }
}
