package com.example.feature.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.ActivityStack
import androidx.window.embedding.EmbeddingBackend
import androidx.window.embedding.SplitInfo

/**
 * Helper that queries Jetpack WindowManager on demand (no manual stack storage)
 * to finish active Category `ActivityStack`s via `ActivityEmbeddingController.finishActivityStacks()`.
 *
 * Why a helper is needed for external plugin apps in Narrow Mode:
 * - Public API `ActivityEmbeddingController.getActivityStack(activity: Activity)` requires an
 *   in-process `Activity` instance. It works directly for `HomeActivity`, `PrimaryActivity`,
 *   `PrimaryOverlayActivity`, and `GenericSettingsActivity`.
 * - However, when an external plugin app (`LightSettingsActivity` in `com.example.carsettings.light`)
 *   is embedded via `ActivityRule(alwaysExpand = true)` in Narrow Mode, it runs in a separate process,
 *   so the host app has no `Activity` reference to pass to `getActivityStack(activity)`.
 * - Internally, `ExtensionEmbeddingBackend` already receives all `ActivityStack`s (including cross-process
 *   ones) from `WindowExtensions.onActivityStackChanged`. We read that live list on demand and exclude
 *   `getActivityStack(homeActivity)` and `getActivityStack(callerActivity)`.
 */
object CategoryStackManager {

    private const val TAG = "LifecycleLog"

    var wideModeCategoryLauncher: ((Intent) -> Unit)? = null

    /**
     * Queries WindowManager for currently active `ActivityStack`s and finishes all Category stacks
     * (excluding `HomeActivity`'s stack and the calling Menu activity's stack).
     */
    fun finishCategoryStacks(caller: Activity) {
        try {
            val controller = ActivityEmbeddingController.getInstance(caller)

            // Query root stacks directly from WindowManager using public getActivityStack(Activity) API
            val homeStack: ActivityStack? = HomeActivity.homeInstance?.let { controller.getActivityStack(it) }
            val callerStack: ActivityStack? = controller.getActivityStack(caller)

            val allStacks = getLiveActivityStacksFromBackend(caller)
            val categoryStacks = allStacks.filterTo(LinkedHashSet()) { stack ->
                stack != homeStack &&
                    stack != callerStack &&
                    (HomeActivity.homeInstance == null || !stack.contains(HomeActivity.homeInstance!!)) &&
                    !stack.contains(caller)
            }

            if (categoryStacks.isEmpty()) {
                Log.d(TAG, "[CategoryStackManager] finishCategoryStacks: no active CategoryStack found")
                return
            }

            // Synchronously finish any in-process category activities so ActivityStarter won't reuse
            // a dying instance via SINGLE_TOP if the same Activity class is immediately relaunched.
            for (stack in categoryStacks) {
                for (act in getInProcessActivities(stack)) {
                    if (!act.isFinishing && act !== HomeActivity.homeInstance && act !== caller) {
                        act.finish()
                    }
                }
            }

            Log.d(TAG, "[CategoryStackManager] Calling finishActivityStacks on ${categoryStacks.size} stack(s): $categoryStacks")
            controller.finishActivityStacks(categoryStacks)
        } catch (e: Exception) {
            Log.e(TAG, "[CategoryStackManager] Failed to finish category stacks: $e", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getInProcessActivities(stack: ActivityStack): List<Activity> {
        return try {
            val method = ActivityStack::class.java.getDeclaredMethod("getActivitiesInProcess\$window_release")
            method.isAccessible = true
            (method.invoke(stack) as? List<Activity>) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getLiveActivityStacksFromBackend(context: Context): Set<ActivityStack> {
        val result = LinkedHashSet<ActivityStack>()
        try {
            val backend = EmbeddingBackend.getInstance(context)
            val callbackField = backend.javaClass.getDeclaredField("embeddingCallback")
            callbackField.isAccessible = true
            val callbackObj = callbackField.get(backend) ?: return result

            val getStacksMethod = callbackObj.javaClass.getDeclaredMethod("getLastActivityStacks")
            getStacksMethod.isAccessible = true
            (getStacksMethod.invoke(callbackObj) as? List<ActivityStack>)?.let { result.addAll(it) }

            val getSplitsMethod = callbackObj.javaClass.getDeclaredMethod("getLastInfo")
            getSplitsMethod.isAccessible = true
            (getSplitsMethod.invoke(callbackObj) as? List<SplitInfo>)?.forEach { split ->
                result.add(split.primaryActivityStack)
                result.add(split.secondaryActivityStack)
            }
        } catch (e: Exception) {
            Log.w(TAG, "[CategoryStackManager] getLiveActivityStacksFromBackend warning: $e")
        }
        return result
    }
}
