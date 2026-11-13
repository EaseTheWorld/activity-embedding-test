package com.example.lifecycleapp

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.example.core.item.AppConfig
import com.example.feature.home.DeepLinkResolver
import com.example.feature.home.HomeActivity
import com.example.feature.home.HomeNavigationBridge

/**
 * Trampoline router for all external DeepLink and implicit open intents
 * (`ACTION_VIEW myapp://navigate/...` and `com.example.lifecycleapp.open`).
 *
 * Runs in its own ephemeral task (`android:taskAffinity="com.example.lifecycleapp.deeplink"`)
 * with a translucent theme, finishes immediately, and ensures:
 * 1. Cold Start: Launches [ListActivity] with `ACTION_MAIN` / `CATEGORY_LAUNCHER`, establishing
 *    [ListActivity] as the pristine Task Root of the application task with a consistent `baseIntent`.
 * 2. Warm Start: Brings the existing application task to the foreground and delegates the deep link
 *    target directly via [HomeNavigationBridge] or [HomeActivity] CLEAR_TOP.
 */
class DeepLinkRouterActivity : Activity() {

    private val tag = "DeepLinkRouterActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure WindowManager embedding rules are initialized for the active mode
        SplitInitializer.ensureInitialized(this)

        val menuComponent = ComponentName(this, ListWithTranslucentActivity::class.java)
        val resolved = DeepLinkResolver.resolve(this, intent, menuComponent)
        Log.i(tag, "onCreate: intent=$intent, resolved=$resolved")

        val isWarmStart = HomeNavigationBridge.isSessionActive()

        if (isWarmStart) {
            Log.i(tag, "Warm start detected -> bringing task to front and delegating deep link")
            // Bring main app task to foreground
            val bringToFrontIntent = Intent(this, ListActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(bringToFrontIntent)

            if (resolved != null) {
                if (resolved.categoryId.equals("home", ignoreCase = true)) {
                    val homeMenuIntent = Intent().apply {
                        component = menuComponent
                        putExtra(AppConfig.EXTRA_CATEGORY_ID, "home")
                    }
                    val homeIntent = Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, "home")
                        putExtra(AppConfig.EXTRA_MENU_INTENT, homeMenuIntent)
                        putExtra(
                            AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP,
                            AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT
                        )
                    }
                    startActivity(homeIntent)
                } else {
                    val targetIntent = resolved.targetIntent
                    if (targetIntent != null) {
                        HomeNavigationBridge.notifyCategorySelected(resolved.categoryId)
                        HomeNavigationBridge.launchCategory(this, targetIntent)
                    }
                }
            }
        } else {
            Log.i(tag, "Cold start detected -> launching ListActivity as pristine Task Root")
            val targetIntent = resolved?.targetIntent
            val listMenuIntent = Intent().apply {
                component = menuComponent
                putExtra(AppConfig.EXTRA_CATEGORY_ID, resolved?.categoryId ?: "home")
            }
            val listIntent = Intent(this, ListActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AppConfig.EXTRA_MENU_INTENT, listMenuIntent)
                putExtra(
                    AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP,
                    AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT
                )
                if (resolved != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, resolved.categoryId)
                    if (targetIntent != null) {
                        putExtra(HomeActivity.EXTRA_TARGET_INTENT, targetIntent)
                    }
                }
            }
            startActivity(listIntent)
        }

        overridePendingTransition(0, 0)
        finish()
    }
}
