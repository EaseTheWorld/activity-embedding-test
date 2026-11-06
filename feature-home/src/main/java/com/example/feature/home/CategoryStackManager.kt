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

    /**
     * Checks if an [ActivityStack] holds [HomeActivity].
     */
    private fun isHomeStack(stack: ActivityStack): Boolean {
        val home = HomeActivity.instance ?: return false
        return stack.contains(home)
    }

    /**
     * Observes [SplitController.splitInfoList] on [activity] to track the active category container.
     */
    fun trackSplitInfo(scope: CoroutineScope, activity: Activity, isNarrowMode: Boolean) {
        scope.launch {
            try {
                SplitController.getInstance(activity)
                    .splitInfoList(activity)
                    .collectLatest { splitInfoList ->
                        val categorySplit = if (isNarrowMode) {
                            // In Narrow mode: HomeActivity is primary, category is secondary
                            splitInfoList.firstOrNull { split ->
                                split.primaryActivityStack.contains(activity) && !isHomeStack(split.secondaryActivityStack)
                            }
                        } else {
                            // In Wide mode: PrimaryActivity is primary, category is secondary (not Home)
                            splitInfoList.firstOrNull { split ->
                                split.primaryActivityStack.contains(activity) && !isHomeStack(split.secondaryActivityStack)
                            }
                        }
                        activeCategoryStack = categorySplit?.secondaryActivityStack
                        Log.d(
                            TAG,
                            "[trackSplitInfo] isNarrowMode=$isNarrowMode, splitCount=${splitInfoList.size}, activeCategoryStack=$activeCategoryStack"
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

    fun clear() {
        activeCategoryStack = null
    }
}
