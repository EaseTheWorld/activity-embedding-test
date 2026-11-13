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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.window.embedding.EmbeddingBounds
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayCreateParams
import androidx.window.embedding.setOverlayCreateParams
import androidx.lifecycle.lifecycleScope
import com.example.common.ui.settings.extractMenuIntent
import com.example.common.ui.settings.launchMenuDrawer
import com.example.core.item.AppConfig

class HomeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_INTENT = "extra_target_intent"
        const val EXTRA_INITIAL_CATEGORY_ID = "extra_initial_category_id"
        const val EXTRA_INITIAL_DEEPLINK_URI = "extra_initial_deeplink_uri"
        const val EXTRA_INITIAL_DEEPLINK_ACTION = "extra_initial_deeplink_action"
        const val EXTRA_INITIAL_DEEPLINK_EXTRAS = "extra_initial_deeplink_extras"
        private const val KEY_IS_CATEGORY_RUNNING = "key_is_category_running"
        private const val KEY_SUPPRESSED_RESULT_COUNT = "key_suppressed_result_count"
    }

    private val tag = "HomeActivity"
    private val showCategoryHeader = mutableStateOf(true)
    private val menuIntentState = mutableStateOf<Intent?>(null)
    private var headerBreakpointDp: Int = AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT
    private val categoryResultTracker = CategoryResultTracker()

    private val categorySwitchHandler: () -> Unit = {
        categoryResultTracker.onPrepareSwitch()
        Log.d(
            tag,
            "HomeActivity prepared for category switch: suppressedResultCount=${categoryResultTracker.suppressedResultCount}"
        )
    }

    private val categoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val homeRevealed = categoryResultTracker.onResultDelivered()
        Log.d(
            tag,
            "HomeActivity onActivityResult from category: resultCode=${result.resultCode}, " +
                "homeRevealed=$homeRevealed, isCategoryRunning=${categoryResultTracker.isCategoryRunning}, " +
                "suppressedResultCount=${categoryResultTracker.suppressedResultCount}"
        )
        if (homeRevealed) {
            HomeNavigationBridge.notifyHomeRevealed()
        }
    }

    @Suppress("DEPRECATION")
    private val taskRootLaunchListener: (Intent) -> Unit = { targetIntent ->
        launchCategoryForResult(targetIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            categoryResultTracker.isCategoryRunning = savedInstanceState.getBoolean(KEY_IS_CATEGORY_RUNNING, false)
            categoryResultTracker.suppressedResultCount = savedInstanceState.getInt(KEY_SUPPRESSED_RESULT_COUNT, 0)
        }
        HomeNavigationBridge.registerTaskRootLaunchListener(taskRootLaunchListener)
        HomeNavigationBridge.registerCategorySwitchHandler(categorySwitchHandler)
        updateHeaderAndMenuState(intent)

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
                finishAffinity()
            }
        })

        setContent {
            val activeMenuIntent = menuIntentState.value
            HomeScreen(
                showCategoryHeader = showCategoryHeader.value,
                onMenuClick = activeMenuIntent?.let { menuIntent ->
                    { launchMenuDrawer(this@HomeActivity, menuIntent) }
                },
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
            if (!targetIntent.hasExtra(AppConfig.EXTRA_MENU_INTENT)) {
                menuIntentState.value?.let { baseMenuIntent ->
                    val targetMenuIntent = Intent(baseMenuIntent).apply {
                        if (!initialCategoryId.isNullOrEmpty()) {
                            putExtra(AppConfig.EXTRA_CATEGORY_ID, initialCategoryId)
                        }
                    }
                    targetIntent.putExtra(AppConfig.EXTRA_MENU_INTENT, targetMenuIntent)
                }
            }
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    if (initialCategoryId != null && !initialCategoryId.equals("home", ignoreCase = true)) {
                        HomeNavigationBridge.notifyCategorySelected(initialCategoryId)
                    }
                    launchCategoryForResult(targetIntent)
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
        Log.d(tag, "HomeActivity onResume()")
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
        if (isFinishing) {
            HomeNavigationBridge.notifyAppExit()
        }
        HomeNavigationBridge.unregisterCategorySwitchHandler(categorySwitchHandler)
        HomeNavigationBridge.unregisterTaskRootLaunchListener(taskRootLaunchListener)
        Log.d(tag, "HomeActivity onDestroy()")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "HomeActivity onConfigurationChanged: $newConfig")
        HomeNavigationBridge.handleModeTransition(this, newConfig)
        showCategoryHeader.value = resolveShowCategoryHeader(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_CATEGORY_RUNNING, categoryResultTracker.isCategoryRunning)
        outState.putInt(KEY_SUPPRESSED_RESULT_COUNT, categoryResultTracker.suppressedResultCount)
        Log.d(tag, "HomeActivity onSaveInstanceState()")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateHeaderAndMenuState(intent)
        handleTargetLaunch(intent)
    }

    private fun updateHeaderAndMenuState(sourceIntent: Intent?) {
        if (sourceIntent?.hasExtra(AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP) == true) {
            headerBreakpointDp = sourceIntent.getIntExtra(
                AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP,
                AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT
            )
        }
        extractMenuIntent(sourceIntent)?.let { menuIntentState.value = it }
        showCategoryHeader.value = resolveShowCategoryHeader(sourceIntent)
    }

    private fun resolveShowCategoryHeader(sourceIntent: Intent?): Boolean {
        val evalIntent = if (sourceIntent?.hasExtra(AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP) == true) {
            sourceIntent
        } else {
            Intent().apply {
                putExtra(AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP, headerBreakpointDp)
            }
        }
        return AppConfig.shouldShowCategoryHeader(this, evalIntent)
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
            if (!targetIntent.hasExtra(AppConfig.EXTRA_MENU_INTENT)) {
                menuIntentState.value?.let { baseMenuIntent ->
                    val targetMenuIntent = Intent(baseMenuIntent).apply {
                        if (!categoryId.isNullOrEmpty()) {
                            putExtra(AppConfig.EXTRA_CATEGORY_ID, categoryId)
                        }
                    }
                    targetIntent.putExtra(AppConfig.EXTRA_MENU_INTENT, targetMenuIntent)
                }
            }
            if (categoryId != null) {
                HomeNavigationBridge.notifyCategorySelected(categoryId)
            }
            launchCategoryForResult(targetIntent)
        }
    }

    @Suppress("DEPRECATION")
    internal fun launchCategoryForResult(targetIntent: Intent) {
        try {
            Log.d(tag, "HomeActivity launching target category for result: $targetIntent")
            categoryResultTracker.onLaunchStarted()
            categoryLauncher.launch(targetIntent)
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
            categoryResultTracker.onLaunchFailed()
            Log.e(tag, "Failed to launch target intent: $e")
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
            putExtra(AppConfig.EXTRA_CATEGORY_HEADER_BREAKPOINT_DP, headerBreakpointDp)
            menuIntentState.value?.let { baseMenuIntent ->
                val targetMenuIntent = Intent(baseMenuIntent).apply {
                    putExtra(AppConfig.EXTRA_CATEGORY_ID, item.categoryId)
                }
                putExtra(AppConfig.EXTRA_MENU_INTENT, targetMenuIntent)
            }
        }

        Log.d(tag, "Launching target setting from Home search: $targetComponent, uri=$targetUri, categoryId=${item.categoryId}")
        HomeNavigationBridge.notifyCategorySelected(item.categoryId)
        HomeNavigationBridge.launchCategory(this, intent)
    }
}
