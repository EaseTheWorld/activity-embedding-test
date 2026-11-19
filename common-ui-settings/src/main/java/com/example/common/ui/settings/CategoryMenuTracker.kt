package com.example.common.ui.settings

import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Tracks the currently active top-level menu category ID across stacked internal Activities
 * (via [com.example.core.item.GetCurrentMenuId] and [android.app.Application.ActivityLifecycleCallbacks])
 * as well as external-process Activities bridged through `BoundaryActivity`.
 */
object CategoryMenuTracker {

    @Volatile
    var activeMenuId: String = "home"
        private set

    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val inheritedMenuIds = WeakHashMap<Any, String>()

    fun registerListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Records an inherited fallback menu ID when an internal content Activity is created,
     * so internal sub-Activities that do not override category ownership preserve the parent menu ID.
     */
    fun onInternalActivityCreated(activityKey: Any, explicitMenuId: String?) {
        val captured = explicitMenuId?.takeIf { it.isNotEmpty() } ?: activeMenuId
        synchronized(inheritedMenuIds) {
            inheritedMenuIds[activityKey] = captured
        }
    }

    /**
     * Updates active menu selection when an internal content Activity resumes (forward or via Back).
     */
    fun onInternalActivityResumed(activityKey: Any, directMenuId: String?) {
        val resolvedId = directMenuId?.takeIf { it.isNotEmpty() }
            ?: synchronized(inheritedMenuIds) { inheritedMenuIds[activityKey] }
        if (!resolvedId.isNullOrEmpty()) {
            updateActiveMenuId(resolvedId)
        }
    }

    fun onInternalActivityDestroyed(activityKey: Any) {
        synchronized(inheritedMenuIds) {
            inheritedMenuIds.remove(activityKey)
        }
    }

    /**
     * Updates active menu selection immediately when launching an external-process Activity
     * whose lifecycle callbacks do not fire in the host process.
     */
    fun onExternalCategoryLaunched(categoryId: String) {
        if (categoryId.isEmpty()) return
        updateActiveMenuId(categoryId)
    }

    /**
     * Restores active menu selection to [boundaryMenuId] when returning via Back from a child
     * category into `BoundaryActivity`.
     */
    fun onReturnedToExternalBoundary(boundaryMenuId: String) {
        if (boundaryMenuId.isEmpty()) return
        updateActiveMenuId(boundaryMenuId)
    }

    /**
     * Restores tracker state after host Process Death (e.g. from `ListActivity`'s `savedInstanceState`).
     */
    fun restoreState(menuId: String?) {
        if (!menuId.isNullOrEmpty()) {
            activeMenuId = menuId
        }
    }

    fun resetForTests() {
        listeners.clear()
        synchronized(inheritedMenuIds) {
            inheritedMenuIds.clear()
        }
        activeMenuId = "home"
    }

    private fun updateActiveMenuId(newMenuId: String) {
        activeMenuId = newMenuId
        for (listener in listeners) {
            listener.invoke(newMenuId)
        }
    }
}
