package com.example.lifecycleapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.example.common.ui.settings.BaseMenuContentActivity

/**
 * Translucent host-owned boundary Activity placed immediately above an external-process Activity
 * (either an external category or an external sub-activity launched by an internal category).
 *
 * Extends [BaseMenuContentActivity] so `ListActivity`'s single `ActivityLifecycleCallbacks`
 * handles both internal category returns and external boundary returns uniformly:
 * - Forward launch (`isReturningFromChild == false`): [getCurrentMenuId] returns `null` so
 *   initial creation does not overwrite the upcoming category's menu selection.
 * - Back return (`isReturningFromChild == true`): [nextLauncher] sets `isReturningFromChild = true`,
 *   [onResume] triggers `ListActivity`'s `onActivityResumed` with [boundaryMenuId], and immediately
 *   calls [finish] before SurfaceFlinger draws a window (0 flicker).
 */
class BoundaryActivity : BaseMenuContentActivity() {

    companion object {
        const val EXTRA_BOUNDARY_MENU_ID = "extra_boundary_menu_id"
        const val EXTRA_NEXT_TARGET_INTENT = "extra_next_target_intent"
        const val EXTRA_NEXT_CATEGORY_ID = "extra_next_category_id"
        private const val KEY_HAS_LAUNCHED_NEXT = "key_has_launched_next"
    }

    private var boundaryMenuId: String = ""
    private var hasLaunchedNext: Boolean = false
    private var isReturningFromChild: Boolean = false

    private val nextLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(
            "LifecycleLog",
            "[BoundaryActivity] Returned from child (resultCode=${result.resultCode}) -> " +
                "marking return for external menuId='$boundaryMenuId'"
        )
        isReturningFromChild = true
    }

    override fun getCurrentMenuId(): String? =
        if (isReturningFromChild && boundaryMenuId.isNotEmpty()) boundaryMenuId else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        boundaryMenuId = intent?.getStringExtra(EXTRA_BOUNDARY_MENU_ID).orEmpty()
        hasLaunchedNext = savedInstanceState?.getBoolean(KEY_HAS_LAUNCHED_NEXT, false) ?: false

        if (!hasLaunchedNext) {
            val nextIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_NEXT_TARGET_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_NEXT_TARGET_INTENT)
            }
            val nextCategoryId = intent?.getStringExtra(EXTRA_NEXT_CATEGORY_ID)

            if (nextIntent != null) {
                hasLaunchedNext = true
                try {
                    Log.d(
                        "LifecycleLog",
                        "[BoundaryActivity] Launching next category '$nextCategoryId' " +
                            "above boundary '$boundaryMenuId'"
                    )
                    nextLauncher.launch(nextIntent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                } catch (e: Exception) {
                    Log.e("LifecycleLog", "[BoundaryActivity] Failed to launch next category: $e")
                    isReturningFromChild = true
                }
            } else {
                Log.w("LifecycleLog", "[BoundaryActivity] Missing EXTRA_NEXT_TARGET_INTENT -> finishing")
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isReturningFromChild) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_LAUNCHED_NEXT, hasLaunchedNext)
    }
}
