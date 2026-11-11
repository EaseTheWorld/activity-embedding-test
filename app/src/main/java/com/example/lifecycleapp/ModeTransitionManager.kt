package com.example.lifecycleapp

import android.app.Activity
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.core.item.AppConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages runtime dynamic transition between Wide Split Mode and Narrow Overlay Mode
 * without activity destruction or state loss.
 *
 * Responsibilities:
 * 1. Observes configuration/screen width changes and compares previous mode with new mode.
 * 2. When a mode transition occurs:
 *    a. Synchronously updates WindowManager embedding rules via [SplitInitializer.ensureInitialized].
 *    b. Updates [ListActivity] view binding when entering Wide Mode:
 *       - Narrow Mode: Category main screens render their own top-left menu button directly.
 *       - Wide Mode: Ensures [ListActivity] side-by-side 35% sidebar view binding.
 *    c. Preserves all existing Activity instances, scroll positions, search queries,
 *       and back stack depths completely intact (zero restart / zero state loss).
 */
object ModeTransitionManager {

    private const val TAG = "ModeTransitionManager"

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
     * Seamlessly updates embedding rules and floating menu without restarting activities.
     */
    fun checkAndHandleTransition(activity: Activity, newConfig: Configuration): Boolean {
        // If an explicit override is active (e.g. during fixed-mode unit/ui tests),
        // display configuration changes must NEVER trigger a mode transition.
        if (AppConfig.OVERRIDE_OVERLAY_MODE != null) {
            return false
        }

        val fullDisplayWidthDp = getFullDisplayWidthDp(activity, newConfig)
        val newIsOverlayMode = AppConfig.OVERRIDE_OVERLAY_MODE ?: (fullDisplayWidthDp < AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT)
        val currentRuleMode = SplitInitializer.getLastInitializedOverlayMode()

        Log.i(
            TAG,
            "checkAndHandleTransition: fullDisplayWidthDp=$fullDisplayWidthDp (newConfig.wDp=${newConfig.screenWidthDp}), newIsOverlayMode=$newIsOverlayMode, currentRuleMode=$currentRuleMode, isTransitioning=${isTransitioning.get()}, activity=${activity.javaClass.simpleName}"
        )

        if (currentRuleMode == null) {
            return false
        }

        if (currentRuleMode == newIsOverlayMode) {
            return false
        }

        if (!isTransitioning.compareAndSet(false, true)) {
            Log.w(TAG, "Mode transition already in progress, skipping duplicate trigger")
            return false
        }

        Log.i(
            TAG,
            "Mode transition detected: currentRuleMode=$currentRuleMode -> newIsOverlayMode=$newIsOverlayMode (fullDisplayWidthDp=$fullDisplayWidthDp) [No-Restart Seamless Transition]"
        )

        try {
            // 1. Sync WindowManager embedding rules immediately for the new mode
            if (AppConfig.OVERRIDE_OVERLAY_MODE != null) {
                AppConfig.OVERRIDE_OVERLAY_MODE = newIsOverlayMode
            }
            SplitInitializer.ensureInitialized(activity, newIsOverlayMode)

            // 2. Update ListActivity sidebar UI when transitioning between Narrow and Wide modes
            if (newIsOverlayMode) {
                Log.i(TAG, "Transitioning to Narrow Mode: category screens render inline top-left menu button")
            } else {
                Log.i(TAG, "Transitioning to Wide Mode: ensuring ListActivity content view")
                val targetListActivity = ListActivity.getActiveInstance()
                    ?: (BaseCategoryMenuActivity.getActiveMenuActivity() as? ListActivity)
                targetListActivity?.ensureWideContentView()
            }

            return true
        } finally {
            mainHandler.postDelayed({
                isTransitioning.set(false)
            }, 300L)
        }
    }
}
