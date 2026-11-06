package com.example.lifecycleapp

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import com.example.common.ui.settings.GenericSettingsActivity
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import com.example.feature.home.HomeNavigationBridge
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages runtime dynamic transition between Wide Split Mode and Narrow Overlay Mode.
 *
 * Primary Responsibilities:
 * 1. Observes configuration/screen width changes and compares previous mode with new mode.
 * 2. When a mode transition occurs:
 *    a. Captures current active category state (selected categoryId, targetIntent if in detail).
 *    b. Synchronously updates WindowManager embedding rules via [SplitInitializer.ensureInitialized].
 *    c. Re-launches [EntryRouterActivity] with [Intent.FLAG_ACTIVITY_NEW_TASK] or [Intent.FLAG_ACTIVITY_CLEAR_TASK]
 *       to cleanly dismantle previous TaskFragment hierarchy and construct the new mode stack atomically.
 */
object ModeTransitionManager {

    private const val TAG = "ModeTransitionManager"
    const val EXTRA_IS_MODE_TRANSITION = "extra_is_mode_transition"
    const val EXTRA_RESTORE_CATEGORY_ID = "extra_restore_category_id"
    const val EXTRA_RESTORE_TARGET_INTENT = "extra_restore_target_intent"

    private val isTransitioning = AtomicBoolean(false)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun resetTransitionState() {
        isTransitioning.set(false)
    }

    /**
     * Measures the full display width in DP rather than the Activity's local sub-window bounds.
     */
    fun getFullDisplayWidthDp(activity: Activity, newConfig: Configuration? = null): Int {
        // 1. If newConfig explicitly reports wide screen (>= 840dp), it is definitely Wide mode
        if (newConfig != null && newConfig.screenWidthDp >= AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT) {
            return newConfig.screenWidthDp
        }

        // 2. Query Display.getRealMetrics for true physical/virtual display bounds
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val display = activity.display
                if (display != null) {
                    val realMetrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    display.getRealMetrics(realMetrics)
                    if (realMetrics.widthPixels > 0 && realMetrics.density > 0f) {
                        return (realMetrics.widthPixels / realMetrics.density).toInt()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inspect Display.getRealMetrics", e)
            }
        }

        // 3. Query Application WindowManager maximumWindowMetrics
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val appWm = activity.applicationContext.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
                val maxBounds = appWm?.maximumWindowMetrics?.bounds
                val density = activity.applicationContext.resources.displayMetrics.density
                if (maxBounds != null && maxBounds.width() > 0 && density > 0f) {
                    return (maxBounds.width() / density).toInt()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to inspect application maximumWindowMetrics", e)
            }
        }

        // 4. Fallback to Application displayMetrics
        val appMetrics = activity.applicationContext.resources.displayMetrics
        return if (appMetrics.density > 0f) (appMetrics.widthPixels / appMetrics.density).toInt() else 0
    }

    /**
     * Called by BaseLoggingActivity / activities when configuration changes (e.g. wm size changed or screen rotated).
     * Returns true if a mode transition was detected and handled.
     */
    fun checkAndHandleTransition(activity: Activity, newConfig: Configuration): Boolean {
        // Use maximumWindowMetrics or applicationContext so we measure the full display width, not the split sub-window width!
        val fullDisplayWidthDp = getFullDisplayWidthDp(activity, newConfig)
        val newIsOverlayMode = AppConfig.OVERRIDE_OVERLAY_MODE ?: (fullDisplayWidthDp < AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT)
        val currentRuleMode = SplitInitializer.getLastInitializedOverlayMode()

        Log.i(
            TAG,
            "checkAndHandleTransition: fullDisplayWidthDp=$fullDisplayWidthDp (newConfig.wDp=${newConfig.screenWidthDp}), newIsOverlayMode=$newIsOverlayMode, currentRuleMode=$currentRuleMode, isTransitioning=${isTransitioning.get()}, activity=${activity.javaClass.simpleName}"
        )

        if (currentRuleMode == null) {
            // Not initialized yet
            return false
        }

        if (currentRuleMode == newIsOverlayMode) {
            // Mode unchanged, normal configuration change
            return false
        }

        if (!isTransitioning.compareAndSet(false, true)) {
            Log.w(TAG, "Mode transition already in progress, skipping duplicate trigger")
            return false
        }

        Log.i(
            TAG,
            "Mode transition detected: currentRuleMode=$currentRuleMode -> newIsOverlayMode=$newIsOverlayMode (fullDisplayWidthDp=$fullDisplayWidthDp)"
        )

        try {
            // 1. Capture current navigation state
            val activeMenu = BaseCategoryMenuActivity.getActiveMenuActivity()
            var currentCategoryId = activeMenu?.currentSelectedCategoryId
            val topSecondary = LifecycleApp.getTopSecondaryActivity()

            // Check if a category detail screen is currently shown in top secondary
            var restoreTargetIntent: Intent? = null
            if (topSecondary != null && !topSecondary.javaClass.simpleName.contains("HomeActivity")) {
                topSecondary.intent?.let { secIntent ->
                    restoreTargetIntent = Intent(secIntent).apply { flags = 0 }
                }
            }

            // Derive category ID from restoreTargetIntent if not already set or if it was "home"
            val catFromTargetIntent = restoreTargetIntent?.let {
                it.getStringExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID)
                    ?: it.data?.pathSegments?.firstOrNull()
            }
            if (!catFromTargetIntent.isNullOrEmpty() && (currentCategoryId == null || currentCategoryId.equals("home", ignoreCase = true))) {
                currentCategoryId = catFromTargetIntent
            }

            // Fallback: If no topSecondary intent was found, but categoryId is selected
            if (restoreTargetIntent == null && currentCategoryId != null && !currentCategoryId.equals("home", ignoreCase = true)) {
                restoreTargetIntent = activeMenu?.getSettingCategoryById(currentCategoryId)?.let { cat ->
                    if (!cat.targetAction.isNullOrEmpty()) {
                        Intent(cat.targetAction)
                    } else if (!cat.targetPackage.isNullOrEmpty() && !cat.targetActivity.isNullOrEmpty()) {
                        Intent().apply {
                            setClassName(cat.targetPackage!!, cat.targetActivity!!)
                        }
                    } else null
                }
            }

            Log.i(
                TAG,
                "Captured state: categoryId=$currentCategoryId, restoreTargetIntent=$restoreTargetIntent, topSecondary=${topSecondary?.javaClass?.simpleName}"
            )

            // 2. Sync WindowManager embedding rules immediately for the new mode
            if (AppConfig.OVERRIDE_OVERLAY_MODE != null) {
                AppConfig.OVERRIDE_OVERLAY_MODE = newIsOverlayMode
            }
            SplitInitializer.ensureInitialized(activity, newIsOverlayMode)

            // 3. Dismantle any active CategoryHostActivity in the old stack
            HomeNavigationBridge.notifyDismissCategoryStacks()

            // 4. Atomically reset and construct new mode stack via EntryRouterActivity
            val routerIntent = Intent(activity, EntryRouterActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_IS_MODE_TRANSITION, true)
                if (currentCategoryId != null) {
                    putExtra(EXTRA_RESTORE_CATEGORY_ID, currentCategoryId)
                }
                if (restoreTargetIntent != null) {
                    putExtra(EXTRA_RESTORE_TARGET_INTENT, restoreTargetIntent)
                }
            }

            activity.startActivity(routerIntent)
            activity.overridePendingTransition(0, 0)
            return true
        } finally {
            mainHandler.postDelayed({
                isTransitioning.set(false)
            }, 600L)
        }
    }
}
