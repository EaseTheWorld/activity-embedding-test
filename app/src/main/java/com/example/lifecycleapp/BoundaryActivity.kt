package com.example.lifecycleapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.common.ui.settings.CategoryMenuTracker
import com.example.core.item.GetCurrentMenuId

/**
 * Translucent host-owned boundary Activity placed immediately above an external-process category
 * (e.g. `LightSettingsActivity`) whenever switching from an external category to another category.
 *
 * Implements [GetCurrentMenuId] with the underlying external category's menu ID so that when the
 * stacked child category finishes via Back, [nextLauncher]'s result callback restores the menu
 * selection to [boundaryMenuId] (with `isExternalCategoryOnTop = true`) and immediately finishes
 * itself before exposing the underlying external Activity.
 */
class BoundaryActivity : AppCompatActivity(), GetCurrentMenuId {

    companion object {
        const val EXTRA_BOUNDARY_MENU_ID = "extra_boundary_menu_id"
        const val EXTRA_NEXT_TARGET_INTENT = "extra_next_target_intent"
        const val EXTRA_NEXT_CATEGORY_ID = "extra_next_category_id"
        const val EXTRA_NEXT_IS_EXTERNAL = "extra_next_is_external"
        private const val KEY_HAS_LAUNCHED_NEXT = "key_has_launched_next"
    }

    private var boundaryMenuId: String = ""
    private var hasLaunchedNext: Boolean = false

    private val nextLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(
            "LifecycleLog",
            "[BoundaryActivity] Returned from child (resultCode=${result.resultCode}) -> " +
                "restoring external menuId='$boundaryMenuId' and finishing"
        )
        if (boundaryMenuId.isNotEmpty()) {
            CategoryMenuTracker.onReturnedToExternalBoundary(boundaryMenuId)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun getCurrentMenuId(): String = boundaryMenuId

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
            val nextIsExternal = intent?.getBooleanExtra(EXTRA_NEXT_IS_EXTERNAL, false) ?: false

            if (nextIntent != null) {
                hasLaunchedNext = true
                if (nextIsExternal && !nextCategoryId.isNullOrEmpty()) {
                    CategoryMenuTracker.onExternalCategoryLaunched(nextCategoryId)
                }
                try {
                    Log.d(
                        "LifecycleLog",
                        "[BoundaryActivity] Launching next category '$nextCategoryId' " +
                            "(isExternal=$nextIsExternal) above boundary '$boundaryMenuId'"
                    )
                    nextLauncher.launch(nextIntent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                } catch (e: Exception) {
                    Log.e("LifecycleLog", "[BoundaryActivity] Failed to launch next category: $e")
                    if (boundaryMenuId.isNotEmpty()) {
                        CategoryMenuTracker.onReturnedToExternalBoundary(boundaryMenuId)
                    }
                    finish()
                }
            } else {
                Log.w("LifecycleLog", "[BoundaryActivity] Missing EXTRA_NEXT_TARGET_INTENT -> finishing")
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_LAUNCHED_NEXT, hasLaunchedNext)
    }
}
