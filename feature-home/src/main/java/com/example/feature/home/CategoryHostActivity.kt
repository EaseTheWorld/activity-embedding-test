package com.example.feature.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.setLaunchingActivityStack
import java.util.Collections
import java.util.WeakHashMap

/**
 * Lightweight in-process root Activity (`Hist #0`) for a Category `ActivityStack` (`TaskFragment`).
 *
 * Why this exists:
 * - `ActivityEmbeddingController.getActivityStack(activity)` requires an in-process [ android.app.Activity ] reference.
 * - By placing [CategoryHostActivity] at the root (`Hist #0`) of each Category `TaskFragment` (via `ActivityRule(alwaysExpand = true)`
 *   in Narrow Mode or `SplitPairRule` in Wide Mode), both internal (`GenericSettingsActivity`, `GenericSubActivity`) and
 *   external (`LightSettingsActivity`, external sub-activities) categories run on top of [CategoryHostActivity] in the same `ActivityStack`.
 * - When switching categories or returning to Home, [finishActiveCategoryStacks] queries `getActivityStack(host)` via the
 *   official public API and calls `finishActivityStacks(setOf(stack))`, atomically finishing the entire category stack
 *   (including any sub-activities) without touching `HomeActivity` or `PrimaryOverlayActivity`.
 * - When the user presses Back until the last category activity above [CategoryHostActivity] finishes, [CategoryHostActivity]
 *   is resumed (`hasPausedForChild == true`) and immediately finishes itself to reveal `HomeActivity`.
 */
class CategoryHostActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_CATEGORY_INTENT = "extra_target_category_intent"
        private const val TAG = "LifecycleLog"

        private val activeHosts: MutableSet<CategoryHostActivity> =
            Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

        fun createLaunchIntent(context: Context, targetIntent: Intent): Intent {
            return Intent(context, CategoryHostActivity::class.java).apply {
                action = "com.example.carsettings.ACTION_CATEGORY_HOST"
                putExtra(EXTRA_TARGET_CATEGORY_INTENT, targetIntent)
            }
        }

        fun finishActiveCategoryStacks() {
            val hosts = synchronized(activeHosts) { activeHosts.toList() }
            activeHosts.clear()
            for (host in hosts) {
                if (!host.isFinishing && !host.isDestroyed) {
                    try {
                        val controller = ActivityEmbeddingController.getInstance(host)
                        val stack = controller.getActivityStack(host)
                        if (stack != null) {
                            Log.d(TAG, "[CategoryHostActivity] Finishing category ActivityStack via public API: $stack")
                            controller.finishActivityStacks(setOf(stack))
                        } else {
                            Log.d(TAG, "[CategoryHostActivity] ActivityStack is null, finishing host directly")
                            host.finish()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "[CategoryHostActivity] Failed to finish ActivityStack: $e")
                        host.finish()
                    }
                }
            }
        }
    }

    private var hasPausedForChild = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        activeHosts.add(this)
        Log.d(TAG, "[CategoryHostActivity] onCreate(savedInstanceState=$savedInstanceState, hash=${System.identityHashCode(this)})")

        if (savedInstanceState != null) {
            hasPausedForChild = savedInstanceState.getBoolean("has_paused_for_child", false)
        } else {
            val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_TARGET_CATEGORY_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_TARGET_CATEGORY_INTENT)
            }

            if (targetIntent != null) {
                launchChildCategory(targetIntent)
            } else {
                Log.w(TAG, "[CategoryHostActivity] No target category intent found, finishing")
                finish()
            }
        }
    }

    private fun launchChildCategory(targetIntent: Intent) {
        try {
            val options = try {
                val stack = ActivityEmbeddingController.getInstance(this).getActivityStack(this)
                if (stack != null) {
                    Bundle().apply {
                        setLaunchingActivityStack(this@CategoryHostActivity, stack)
                    }
                } else null
            } catch (e: Exception) {
                null
            }
            Log.d(TAG, "[CategoryHostActivity] Launching child category into host stack (options=$options): $targetIntent")
            startActivity(targetIntent, options)
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            Log.e(TAG, "[CategoryHostActivity] Failed to launch child category: $e", e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[CategoryHostActivity] onResume(hasPausedForChild=$hasPausedForChild)")
        if (hasPausedForChild && !isFinishing) {
            Log.d(TAG, "[CategoryHostActivity] All child category activities finished -> finishing CategoryHostActivity to reveal Home")
            activeHosts.remove(this)
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onPause() {
        super.onPause()
        hasPausedForChild = true
        Log.d(TAG, "[CategoryHostActivity] onPause() -> hasPausedForChild=true")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("has_paused_for_child", hasPausedForChild)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeHosts.remove(this)
        Log.d(TAG, "[CategoryHostActivity] onDestroy(hash=${System.identityHashCode(this)})")
    }
}
