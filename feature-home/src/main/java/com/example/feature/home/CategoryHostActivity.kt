package com.example.feature.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.window.embedding.ActivityEmbeddingController
import androidx.window.embedding.setLaunchingActivityStack

/**
 * Lightweight in-process root Activity (`Hist #0`) for a Category `ActivityStack` (`TaskFragment`).
 *
 * Lifecycle & Stack Management:
 * - No static `Activity` or `WeakReference` is stored in `companion object`.
 * - Each instance registers its own [dismissListener] with [HomeNavigationBridge] in [onCreate]
 *   and unregisters it in [onDestroy].
 * - When `Primary` or `Home` notifies [HomeNavigationBridge.notifyDismissCategoryStacks], the active
 *   [CategoryHostActivity] instance receives the callback and finishes its own `ActivityStack`
 *   (including any internal or external sub-activities) via `finishActivityStacks(setOf(stack))`.
 */
class CategoryHostActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_TARGET_CATEGORY_INTENT = "extra_target_category_intent"
        private const val TAG = "LifecycleLog"

        fun createIntent(context: Context, targetIntent: Intent): Intent {
            return Intent(context, CategoryHostActivity::class.java).apply {
                action = "com.example.carsettings.ACTION_CATEGORY_HOST"
                putExtra(EXTRA_TARGET_CATEGORY_INTENT, targetIntent)
            }
        }
    }

    private var hasPausedForChild = false

    private val dismissListener: () -> Unit = {
        finishOwnActivityStack()
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        // Ensure any previous CategoryHostActivity stack is dismissed before registering this instance
        HomeNavigationBridge.notifyDismissCategoryStacks()
        HomeNavigationBridge.registerCategoryHostDismissListener(dismissListener)

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
                window.decorView.post {
                    if (!isFinishing && !isDestroyed) {
                        launchChildCategory(targetIntent)
                    }
                }
            } else {
                Log.w(TAG, "[CategoryHostActivity] No target category intent found, finishing")
                finish()
            }
        }
    }

    private fun finishOwnActivityStack() {
        HomeNavigationBridge.unregisterCategoryHostDismissListener(dismissListener)
        if (!isFinishing && !isDestroyed) {
            try {
                val controller = ActivityEmbeddingController.getInstance(this)
                val stack = controller.getActivityStack(this)
                if (stack != null) {
                    Log.d(TAG, "[CategoryHostActivity] Finishing own category ActivityStack via public API: $stack")
                    controller.finishActivityStacks(setOf(stack))
                } else {
                    Log.d(TAG, "[CategoryHostActivity] ActivityStack is null, finishing host directly")
                    finish()
                }
            } catch (e: Exception) {
                Log.w(TAG, "[CategoryHostActivity] Failed to finish ActivityStack: $e")
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
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

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[CategoryHostActivity] onResume(hasPausedForChild=$hasPausedForChild)")
        if (hasPausedForChild && !isFinishing) {
            Log.d(TAG, "[CategoryHostActivity] All child category activities finished -> finishing CategoryHostActivity to reveal Home")
            HomeNavigationBridge.unregisterCategoryHostDismissListener(dismissListener)
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
        HomeNavigationBridge.unregisterCategoryHostDismissListener(dismissListener)
        Log.d(TAG, "[CategoryHostActivity] onDestroy(hash=${System.identityHashCode(this)})")
    }
}
