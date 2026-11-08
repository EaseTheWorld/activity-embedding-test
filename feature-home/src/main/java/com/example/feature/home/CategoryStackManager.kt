package com.example.feature.home

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.ActivityStack
import androidx.window.embedding.SplitController
import androidx.window.embedding.SplitInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Clean, reflection-free category stack tracker and lifecycle orchestrator.
 *
 * Utilizes 100% public Jetpack WindowManager APIs:
 * - [SplitController.splitInfoList] to track active category splits.
 * - [ActivityEmbeddingController.finishActivityStacks] to atomically dismiss
 *   category TaskFragments (both internal and external process activities).
 */
object CategoryStackManager {
    private const val TAG = "CategoryStackManager"

    @Volatile
    private var activeCategoryStack: ActivityStack? = null

    @Volatile
    private var activeHomeStack: ActivityStack? = null

    @Volatile
    private var homeActivityRef: java.lang.ref.WeakReference<Activity>? = null

    fun registerHomeActivity(activity: Activity) {
        homeActivityRef = java.lang.ref.WeakReference(activity)
    }

    /**
     * Observes [SplitController.splitInfoList] on [activity] to track the active category container
     * and the root Home content container.
     */
    fun trackSplitInfo(scope: CoroutineScope, activity: Activity, isNarrowMode: Boolean) {
        scope.launch {
            try {
                SplitController.getInstance(activity)
                    .splitInfoList(activity)
                    .collectLatest { splitInfoList ->
                        val categorySplit = splitInfoList.firstOrNull { split ->
                            split.primaryActivityStack.contains(activity)
                        }
                        activeCategoryStack = categorySplit?.secondaryActivityStack

                        activeHomeStack = if (isNarrowMode) {
                            categorySplit?.primaryActivityStack
                                ?: ActivityEmbeddingController.getInstance(activity).getActivityStack(activity)
                        } else {
                            // In Wide Mode (RTL Split), HomeActivity is the primary stack and ListActivity is secondary
                            val homeSplit = splitInfoList.firstOrNull { split ->
                                split.secondaryActivityStack.contains(activity)
                            }
                            homeSplit?.primaryActivityStack
                        }

                        Log.d(
                            TAG,
                            "[trackSplitInfo] isNarrowMode=$isNarrowMode, splitCount=${splitInfoList.size}, activeCategoryStack=$activeCategoryStack, activeHomeStack=$activeHomeStack"
                        )
                    }
            } catch (e: Throwable) {
                Log.w(TAG, "[trackSplitInfo] Failed to collect splitInfoList: $e")
            }
        }
    }

    /**
     * Dismisses the active category [ActivityStack] using [ActivityEmbeddingController.finishActivityStacks].
     * Requires ZERO reflection and works seamlessly for internal and external activities.
     */
    fun dismissCurrentCategory(context: Context): Boolean {
        val stack = activeCategoryStack ?: run {
            Log.d(TAG, "[dismissCurrentCategory] No active category stack found in splitInfo")
            return false
        }
        return try {
            ActivityEmbeddingController.getInstance(context).finishActivityStacks(setOf(stack))
            Log.d(TAG, "[dismissCurrentCategory] Successfully dismissed category stack via ActivityEmbeddingController: $stack")
            activeCategoryStack = null
            true
        } catch (e: Throwable) {
            Log.e(TAG, "[dismissCurrentCategory] Failed to finishActivityStacks: $e", e)
            false
        }
    }

    /**
     * Extracts the TaskFragment [android.os.IBinder] token from the currently active category [ActivityStack].
     * Works identically for in-process and cross-process activities.
     */
    fun getActiveCategoryToken(): android.os.IBinder? {
        return extractToken(activeCategoryStack)
    }

    /**
     * Extracts the TaskFragment [android.os.IBinder] token from the active content pane
     * (either the active Category stack if open, or the Home content stack).
     */
    fun getActiveContentToken(): android.os.IBinder? {
        val tracked = extractToken(activeCategoryStack ?: activeHomeStack)
        if (tracked != null) return tracked
        val homeActivity = homeActivityRef?.get() ?: return null
        return getActivityStackToken(homeActivity)
    }

    /**
     * Extracts the TaskFragment [android.os.IBinder] token for the [ActivityStack] containing [activity]
     * via public [ActivityEmbeddingController.getActivityStack].
     */
    fun getActivityStackToken(activity: Activity): android.os.IBinder? {
        return try {
            val stack = ActivityEmbeddingController.getInstance(activity).getActivityStack(activity)
            extractToken(stack)
        } catch (e: Throwable) {
            Log.w(TAG, "[getActivityStackToken] Failed to resolve stack token for $activity: $e")
            null
        }
    }

    private const val KEY_ACTIVITY_STACK_TOKEN =
        "androidx.window.extensions.embedding.ActivityStack.Token"

    private val stackTokenField by lazy {
        ActivityStack::class.java.getDeclaredField("token").apply { isAccessible = true }
    }

    fun extractToken(stack: ActivityStack?): android.os.IBinder? {
        if (stack == null) return null
        return try {
            val oemToken = stackTokenField.get(stack)
                as? androidx.window.extensions.embedding.ActivityStack.Token
                ?: return null
            oemToken.toBundle().getBinder(KEY_ACTIVITY_STACK_TOKEN)
        } catch (e: Throwable) {
            Log.w(TAG, "[extractToken] Failed to extract IBinder from stack: $e")
            null
        }
    }

    fun clear() {
        activeCategoryStack = null
        activeHomeStack = null
    }
}
