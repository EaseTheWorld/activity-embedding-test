package com.example.feature.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.core.item.AppConfig
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Event bridge between Primary menu (`PrimaryActivity` / `PrimaryOverlayActivity`),
 * `HomeActivity`, and `CategoryHostActivity`.
 *
 * All participants register their listeners in `onCreate()` and unregister in `onDestroy()`,
 * avoiding any static `Activity` or `WeakReference<Activity>` fields.
 */
object HomeNavigationBridge {

    @Volatile
    var onHomeRevealed: (() -> Unit)? = null

    @Volatile
    var onCategorySelected: ((String) -> Unit)? = null

    private val categoryDismissListeners = CopyOnWriteArraySet<() -> Unit>()
    private var taskRootLaunchListener: ((Intent) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearLaunchingRunnable = Runnable { isLaunchingCategory = false }

    @Volatile
    var isLaunchingCategory: Boolean = false
        private set

    fun notifyHomeRevealed() {
        onHomeRevealed?.invoke()
    }

    fun notifyCategorySelected(categoryId: String) {
        onCategorySelected?.invoke(categoryId)
    }

    /**
     * Called by [CategoryHostActivity] in `onCreate()` to subscribe to stack dismissal events.
     */
    fun registerCategoryHostDismissListener(listener: () -> Unit) {
        categoryDismissListeners.add(listener)
    }

    /**
     * Called by [CategoryHostActivity] in `onDestroy()` (or upon dismissal) to unsubscribe.
     */
    fun unregisterCategoryHostDismissListener(listener: () -> Unit) {
        categoryDismissListeners.remove(listener)
    }

    /**
     * Notifies all currently active [CategoryHostActivity] instances to finish their own `ActivityStack`.
     */
    fun notifyDismissCategoryStacks() {
        val snapshot = categoryDismissListeners.toList()
        categoryDismissListeners.clear()
        for (listener in snapshot) {
            listener.invoke()
        }
    }

    /**
     * Called by [HomeActivity] in `onCreate()` to handle category launches from the root Task in Narrow Mode.
     */
    fun registerTaskRootLaunchListener(listener: (Intent) -> Unit) {
        taskRootLaunchListener = listener
    }

    /**
     * Called by [HomeActivity] in `onDestroy()` to unsubscribe its launch listener.
     */
    fun unregisterTaskRootLaunchListener(listener: (Intent) -> Unit) {
        if (taskRootLaunchListener === listener) {
            taskRootLaunchListener = null
        }
    }

    /**
     * Dismisses any currently active Category `ActivityStack` and launches [targetIntent]
     * inside a new [CategoryHostActivity] stack.
     */
    @Suppress("DEPRECATION")
    fun launchCategory(caller: Context, targetIntent: Intent) {
        isLaunchingCategory = true
        mainHandler.removeCallbacks(clearLaunchingRunnable)
        mainHandler.postDelayed(clearLaunchingRunnable, 400L)

        notifyDismissCategoryStacks()

        val rootLauncher = taskRootLaunchListener
        if (AppConfig.isOverlayMode(caller) && rootLauncher != null) {
            rootLauncher.invoke(targetIntent)
        } else {
            val hostIntent = CategoryHostActivity.createIntent(caller, targetIntent)
            caller.startActivity(hostIntent)
            if (caller is Activity) {
                caller.overridePendingTransition(0, 0)
            }
        }
    }
}
