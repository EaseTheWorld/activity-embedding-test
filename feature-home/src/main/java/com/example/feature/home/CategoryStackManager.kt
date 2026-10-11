package com.example.feature.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.ActivityStack
import androidx.window.embedding.EmbeddingBackend
import androidx.window.embedding.SplitInfo
import java.util.Collections
import java.util.WeakHashMap

/**
 * Unified manager for Category `ActivityStack`s across both Wide Mode (Split) and Narrow Mode (Overlay).
 *
 * Stack Hierarchy:
 * - Protected Root Stacks (never finished on category switch):
 *   1. `HomeStack`: Contains `HomeActivity` at the bottom of the content area.
 *   2. `PrimaryStack` (Wide Mode): Contains `PrimaryActivity` on the left 35% split pane.
 *   3. `OverlayStack` (Narrow Mode): Contains `PrimaryOverlayActivity` as an Always-On-Top Pinned Overlay.
 * - `CategoryStack`:
 *   Contains the currently active category root (`GenericSettingsActivity` or external `LightSettingsActivity`)
 *   plus any sub-activities (`GenericSubActivity`, etc.) pushed on top of it.
 *
 * Calling [finishCategoryStacks] atomically finishes all active `CategoryStack`s via
 * `ActivityEmbeddingController.finishActivityStacks()`, clearing both root category activities
 * and any stacked sub-activities (including external plugin activities) while keeping
 * `HomeStack`, `PrimaryStack`, and `OverlayStack` intact.
 */
object CategoryStackManager {

    private const val TAG = "LifecycleLog"

    private val protectedRootActivities: MutableSet<Activity> =
        Collections.newSetFromMap(WeakHashMap())

    private val protectedRootClassNames = setOf(
        "com.example.feature.home.HomeActivity",
        "com.example.lifecycleapp.PrimaryActivity",
        "com.example.lifecycleapp.PrimaryOverlayActivity"
    )

    var wideModeCategoryLauncher: ((Intent) -> Unit)? = null

    fun registerRootActivity(activity: Activity) {
        protectedRootActivities.add(activity)
    }

    fun unregisterRootActivity(activity: Activity) {
        protectedRootActivities.remove(activity)
    }

    /**
     * Finishes all active Category `ActivityStack`s (including any sub-activities stacked inside them)
     * without touching `HomeStack`, `PrimaryStack`, or `OverlayStack`.
     */
    fun finishCategoryStacks(context: Context) {
        try {
            val allStacks = collectAllActivityStacks(context)
            val categoryStacks = allStacks.filterTo( LinkedHashSet() ) { stack ->
                !isProtectedRootStack(stack)
            }

            if (categoryStacks.isEmpty()) {
                Log.d(TAG, "[CategoryStackManager] finishCategoryStacks: no active CategoryStack to finish")
                return
            }

            // 1. Synchronously mark any in-process category/sub-activities as finishing
            //    so Android ActivityStarter never delivers onNewIntent to a dying instance.
            for (stack in categoryStacks) {
                for (act in getInProcessActivities(stack)) {
                    if (!act.isFinishing && !protectedRootClassNames.contains(act.javaClass.name)) {
                        Log.d(TAG, "[CategoryStackManager] Synchronously finishing in-process activity: ${act.javaClass.simpleName}")
                        act.finish()
                    }
                }
            }

            // 2. Finish the ActivityStack containers via WindowManager Extensions
            //    (destroys the TaskFragment container and finishes any cross-process plugin activities).
            Log.d(TAG, "[CategoryStackManager] Calling finishActivityStacks on ${categoryStacks.size} stack(s): $categoryStacks")
            ActivityEmbeddingController.getInstance(context).finishActivityStacks(categoryStacks)
        } catch (e: Exception) {
            Log.e(TAG, "[CategoryStackManager] Failed to finish category stacks: $e", e)
        }
    }

    private fun isProtectedRootStack(stack: ActivityStack): Boolean {
        if (protectedRootActivities.any { rootAct ->
                try {
                    stack.contains(rootAct)
                } catch (_: Exception) {
                    false
                }
            }) {
            return true
        }
        val inProcess = getInProcessActivities(stack)
        return inProcess.any { act -> protectedRootClassNames.contains(act.javaClass.name) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getInProcessActivities(stack: ActivityStack): List<Activity> {
        return try {
            val method = ActivityStack::class.java.getDeclaredMethod("getActivitiesInProcess\$window_release")
            method.isAccessible = true
            (method.invoke(stack) as? List<Activity>) ?: emptyList()
        } catch (_: Exception) {
            try {
                val field = ActivityStack::class.java.getDeclaredField("activitiesInProcess")
                field.isAccessible = true
                (field.get(stack) as? List<Activity>) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectAllActivityStacks(context: Context): Set<ActivityStack> {
        val result = LinkedHashSet<ActivityStack>()
        try {
            val backend = EmbeddingBackend.getInstance(context)
            val callbackField = backend.javaClass.getDeclaredField("embeddingCallback")
            callbackField.isAccessible = true
            val callbackObj = callbackField.get(backend)

            if (callbackObj != null) {
                try {
                    val getStacksMethod = callbackObj.javaClass.getDeclaredMethod("getLastActivityStacks")
                    getStacksMethod.isAccessible = true
                    val stacks = getStacksMethod.invoke(callbackObj) as? List<ActivityStack>
                    if (stacks != null) {
                        result.addAll(stacks)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[CategoryStackManager] getLastActivityStacks reflection warning: $e")
                }

                try {
                    val getSplitsMethod = callbackObj.javaClass.getDeclaredMethod("getLastInfo")
                    getSplitsMethod.isAccessible = true
                    val splits = getSplitsMethod.invoke(callbackObj) as? List<SplitInfo>
                    if (splits != null) {
                        for (split in splits) {
                            result.add(split.primaryActivityStack)
                            result.add(split.secondaryActivityStack)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[CategoryStackManager] getLastInfo reflection warning: $e")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[CategoryStackManager] collectAllActivityStacks reflection warning: $e")
        }
        return result
    }
}
