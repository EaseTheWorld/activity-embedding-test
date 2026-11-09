package com.example.feature.home

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.window.embedding.EmbeddingBounds
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayCreateParams
import androidx.window.embedding.setOverlayCreateParams
import androidx.lifecycle.lifecycleScope
import com.example.core.item.AppConfig

class HomeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_INTENT = "extra_target_intent"
        const val EXTRA_INITIAL_CATEGORY_ID = "extra_initial_category_id"
        const val EXTRA_INITIAL_DEEPLINK_URI = "extra_initial_deeplink_uri"
        const val EXTRA_INITIAL_DEEPLINK_ACTION = "extra_initial_deeplink_action"
        const val EXTRA_INITIAL_DEEPLINK_EXTRAS = "extra_initial_deeplink_extras"
    }

    private val tag = "HomeActivity"

    @Suppress("DEPRECATION")
    private val taskRootLaunchListener: (Intent) -> Unit = { targetIntent ->
        startActivity(targetIntent)
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HomeNavigationBridge.registerTaskRootLaunchListener(taskRootLaunchListener)

        val isNarrowOverlayMode = AppConfig.isOverlayMode(this)
        HomeNavigationBridge.ensureSplitRules(this, isNarrowOverlayMode)
        if (!isNarrowOverlayMode) {
            CategoryStackManager.registerHomeActivity(this)
        }
        Log.d(tag, "HomeActivity created (isNarrowOverlayMode=$isNarrowOverlayMode)")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(tag, "Back pressed at Home (Root of App) -> finishAffinity to exit")
                HomeNavigationBridge.notifyAppExit()
                HomeNavigationBridge.dismissFloatingOverlay()
                finishAffinity()
            }
        })

        setContent {
            HomeScreen(
                onItemClick = { item ->
                    launchTargetSetting(item)
                }
            )
        }

        val initialCategoryId = intent?.getStringExtra(EXTRA_INITIAL_CATEGORY_ID)
        val targetIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_TARGET_INTENT)
        }

        if (targetIntent != null) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    if (initialCategoryId != null && !initialCategoryId.equals("home", ignoreCase = true)) {
                        HomeNavigationBridge.notifyCategorySelected(initialCategoryId)
                    }
                    startActivity(targetIntent)
                    overridePendingTransition(0, 0)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "HomeActivity onStart()")
    }

    override fun onResume() {
        super.onResume()
        if (HomeNavigationBridge.isLaunchingCategory) {
            Log.d(tag, "HomeActivity onResume: skipped bridge notification because target category is being launched")
            return
        }
        Log.d(tag, "HomeActivity onResume: Home truly revealed")
        HomeNavigationBridge.notifyHomeRevealed()
    }

    override fun onPause() {
        super.onPause()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        Log.d(tag, "HomeActivity onPause()")
    }

    override fun onStop() {
        super.onStop()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        Log.d(tag, "HomeActivity onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        HomeNavigationBridge.dismissFloatingOverlay()
        if (isFinishing) {
            HomeNavigationBridge.notifyAppExit()
        }
        HomeNavigationBridge.unregisterTaskRootLaunchListener(taskRootLaunchListener)
        Log.d(tag, "HomeActivity onDestroy()")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "HomeActivity onConfigurationChanged: $newConfig")
        HomeNavigationBridge.handleModeTransition(this, newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d(tag, "HomeActivity onSaveInstanceState()")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTargetLaunch(intent)
    }

    private fun handleTargetLaunch(intent: Intent?) {
        if (intent == null) return

        val targetIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TARGET_INTENT)
        }

        val categoryId = intent.getStringExtra(EXTRA_INITIAL_CATEGORY_ID)

        if (categoryId?.equals("home", ignoreCase = true) == true) {
            Log.d(tag, "HomeActivity revealed via CLEAR_TOP for category 'home'")
            HomeNavigationBridge.notifyHomeRevealed()
            return
        }

        if (targetIntent != null) {
            if (categoryId != null) {
                HomeNavigationBridge.notifyCategorySelected(categoryId)
            }
            try {
                Log.d(tag, "HomeActivity launching target category: $targetIntent")
                startActivity(targetIntent)
                overridePendingTransition(0, 0)
            } catch (e: Exception) {
                Log.e(tag, "Failed to launch target intent: $e")
            }
        }
    }

    private fun launchTargetSetting(item: SearchableSettingItem) {
        val targetComponent = if (item.categoryId.equals("light", ignoreCase = true)) {
            ComponentName("com.example.carsettings.light", "com.example.feature.light.LightSettingsActivity")
        } else {
            ComponentName(packageName, "com.example.lifecycleapp.GenericSettingsActivity")
        }

        val targetUri = Uri.parse(item.deepLinkUri)
        val intent = Intent().apply {
            component = targetComponent
            action = "com.example.carsettings.ACTION_SETTINGS_EMBED"
            data = targetUri
            putExtra("category_id", item.categoryId)
            putExtra("target_item_id", item.itemId)
            putExtra(AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP, AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT)
        }

        Log.d(tag, "Launching target setting from Home search: $targetComponent, uri=$targetUri, categoryId=${item.categoryId}")
        HomeNavigationBridge.notifyCategorySelected(item.categoryId)
        HomeNavigationBridge.launchCategory(this, intent)
    }
}
