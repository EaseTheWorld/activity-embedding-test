package com.example.lifecycleapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import com.example.feature.home.HomeNavigationBridge

/**
 * Lightweight, UI-less entry router (`@android:style/Theme.NoDisplay`) for all external
 * launches (`MAIN/LAUNCHER`, `ACTION_VIEW myapp://navigate/...`, and `com.example.lifecycleapp.open`).
 *
 * Responsibilities:
 * 1. On-demand initialization of WindowManager embedding rules ONLY for the active mode
 *    ([SplitInitializer.ensureInitialized]).
 * 2. **Warm Start (`MAIN/LAUNCHER`)**: Brings existing Task to the foreground without
 *    recreating or resetting any Activity, restores Secondary focus, and finishes immediately.
 * 3. **Warm Start (`DeepLink`)**: Dispatches the DeepLink directly to the active session
 *    and finishes immediately.
 * 4. **Cold Start — Narrow Overlay Mode**: Directly launches [HomeActivity] (bypassing
 *    [PrimaryActivity] completely) and passes any resolved DeepLink target so [HomeActivity]
 *    can spawn both [PrimaryOverlayActivity] and [com.example.feature.home.CategoryHostActivity]
 *    immediately in parallel.
 * 5. **Cold Start — Wide Split Mode**: Directly launches [PrimaryActivity] (which pairs with
 *    [HomeActivity] via `SplitPairRule` and opens any initial DeepLink category).
 */
class EntryRouterActivity : Activity() {

    companion object {
        private const val TAG = "LifecycleLog"

        data class ResolvedDeepLink(
            val categoryId: String,
            val targetIntent: Intent?
        )

        fun isDeepLinkIntent(intent: Intent?): Boolean {
            if (intent == null) return false
            val dataUri = intent.data
            if (dataUri != null && dataUri.scheme == "myapp" && dataUri.host == "navigate") {
                return true
            }
            return intent.action == "com.example.lifecycleapp.open"
        }

        fun resolveDeepLink(context: Context, intent: Intent?): ResolvedDeepLink? {
            if (intent == null) return null
            val dataUri = intent.data
            val extras = intent.extras

            if (dataUri != null && dataUri.scheme == "myapp" && dataUri.host == "navigate") {
                val segments = dataUri.pathSegments
                val categoryId = when {
                    segments.isEmpty() -> "home"
                    segments[0].equals("home", ignoreCase = true) -> "home"
                    segments[0].equals("dashboard", ignoreCase = true) -> "dashboard"
                    else -> segments[0]
                }
                if (categoryId == "home") {
                    return ResolvedDeepLink(categoryId = "home", targetIntent = null)
                }
                val targetIntent = buildCategoryTargetIntent(context, categoryId, dataUri, extras)
                return ResolvedDeepLink(categoryId = categoryId, targetIntent = targetIntent)
            }

            if (intent.action == "com.example.lifecycleapp.open") {
                @Suppress("DEPRECATION")
                val categoryId = intent.getStringExtra("category") ?: when (extras?.get("id")) {
                    1, "1" -> "light"
                    2, "2" -> "sound"
                    3, "3" -> "display"
                    else -> null
                } ?: return null

                if (categoryId.equals("home", ignoreCase = true)) {
                    return ResolvedDeepLink(categoryId = "home", targetIntent = null)
                }
                val syntheticUri = Uri.parse("myapp://navigate/$categoryId")
                val targetIntent = buildCategoryTargetIntent(context, categoryId, syntheticUri, extras)
                return ResolvedDeepLink(categoryId = categoryId, targetIntent = targetIntent)
            }

            return null
        }

        fun buildCategoryTargetIntent(
            context: Context,
            categoryId: String,
            uri: Uri,
            extras: Bundle?
        ): Intent {
            return if (categoryId.equals("light", ignoreCase = true)) {
                Intent(ACTION_SETTINGS_EMBED).apply {
                    setClassName(
                        "com.example.carsettings.light",
                        "com.example.feature.light.LightSettingsActivity"
                    )
                    data = uri
                    putExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID, "light")
                    if (extras != null) putExtras(extras)
                }
            } else {
                Intent(context, GenericSettingsActivity::class.java).apply {
                    action = ACTION_SETTINGS_EMBED
                    data = uri
                    putExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID, categoryId)
                    if (extras != null) putExtras(extras)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Ensure WindowManager embedding rules are initialized ONLY for the active mode
        SplitInitializer.ensureInitialized(this)

        val isDeepLink = isDeepLinkIntent(intent)
        val activeMenu = BaseCategoryMenuActivity.getActiveMenuActivity()
        val topSecondary = LifecycleApp.getTopSecondaryActivity()
        val isWarmStart = activeMenu != null || topSecondary != null

        // 2. Warm Start handling (Task already alive in foreground or background)
        if (isWarmStart) {
            if (isDeepLink) {
                Log.i(TAG, "[EntryRouterActivity] Warm-start DeepLink -> dispatching to active session")
                if (activeMenu != null) {
                    val forwardedIntent = Intent(intent)
                    activeMenu.window.decorView.post {
                        if (!activeMenu.isFinishing && !activeMenu.isDestroyed) {
                            activeMenu.handleIntent(forwardedIntent)
                        }
                    }
                } else if (topSecondary != null) {
                    val resolved = resolveDeepLink(this, intent)
                    if (resolved != null) {
                        HomeNavigationBridge.notifyCategorySelected(resolved.categoryId)
                        if (resolved.targetIntent != null) {
                            HomeNavigationBridge.launchCategory(topSecondary, resolved.targetIntent)
                        } else {
                            HomeNavigationBridge.notifyDismissCategoryStacks()
                        }
                    }
                }
            } else {
                Log.i(TAG, "[EntryRouterActivity] Warm-start MAIN/LAUNCHER -> preserving existing foreground state")
                val focusHost = activeMenu ?: topSecondary
                focusHost?.window?.decorView?.postDelayed({
                    if (!focusHost.isFinishing && !focusHost.isDestroyed) {
                        OverlayBoundsHelper.requestFocusOnSecondary(focusHost)
                    }
                }, 100L)
            }
            overridePendingTransition(0, 0)
            finish()
            return
        }

        // 3. Cold Start handling by Mode
        if (AppConfig.isOverlayMode(this)) {
            val resolved = if (isDeepLink) resolveDeepLink(this, intent) else null
            Log.i(
                TAG,
                "[EntryRouterActivity] Cold-start Narrow Overlay Mode -> launching HomeActivity directly (deepLink=${resolved?.categoryId})"
            )
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                if (resolved != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, resolved.categoryId)
                    if (resolved.targetIntent != null) {
                        putExtra(HomeActivity.EXTRA_TARGET_INTENT, resolved.targetIntent)
                    }
                }
            }
            startActivity(homeIntent)
        } else {
            Log.i(
                TAG,
                "[EntryRouterActivity] Cold-start Wide Split Mode -> launching PrimaryActivity (isDeepLink=$isDeepLink)"
            )
            val primaryIntent = Intent(this, PrimaryActivity::class.java).apply {
                if (isDeepLink && intent != null) {
                    action = intent.action
                    data = intent.data
                    intent.extras?.let { putExtras(it) }
                }
            }
            startActivity(primaryIntent)
        }

        overridePendingTransition(0, 0)
        finish()
    }
}
