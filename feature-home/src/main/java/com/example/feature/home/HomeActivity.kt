package com.example.feature.home

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.example.common.ui.settings.CategoryMenuTracker
import com.example.common.ui.settings.extractMenuIntent
import com.example.common.ui.settings.launchMenuDrawer
import com.example.core.item.AppConfig
import com.example.core.item.GetCurrentMenuId

class HomeActivity : ComponentActivity(), GetCurrentMenuId {

    companion object {
        const val EXTRA_TARGET_INTENT = "extra_target_intent"
        const val EXTRA_INITIAL_CATEGORY_ID = "extra_initial_category_id"
        const val EXTRA_IS_ROOT_HOME = "extra_is_root_home"
        const val EXTRA_INITIAL_DEEPLINK_URI = "extra_initial_deeplink_uri"
        const val EXTRA_INITIAL_DEEPLINK_ACTION = "extra_initial_deeplink_action"
        const val EXTRA_INITIAL_DEEPLINK_EXTRAS = "extra_initial_deeplink_extras"
    }

    private val tag = "HomeActivity"
    private val showCategoryHeader = mutableStateOf(true)
    private val menuIntentState = mutableStateOf<Intent?>(null)
    private var headerBreakpointDp: Int = AppConfig.OVERLAY_MODE_WIDTH_DP_BREAKPOINT

    override fun getCurrentMenuId(): String = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateHeaderAndMenuState(intent)

        val isNarrowOverlayMode = AppConfig.isOverlayMode(this)
        HomeNavigationBridge.ensureSplitRules(this, isNarrowOverlayMode)
        if (!isNarrowOverlayMode) {
            CategoryStackManager.registerHomeActivity(this)
        }
        Log.d(tag, "HomeActivity created (isNarrowOverlayMode=$isNarrowOverlayMode, isRootHome=${isRootHome()})")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRootHome()) {
                    Log.d(tag, "Back pressed at Root Home -> finishAffinity to exit")
                    HomeNavigationBridge.notifyAppExit()
                    finishAffinity()
                } else {
                    Log.d(tag, "Back pressed at stacked Home -> finish to reveal previous category")
                    finish()
                }
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

        if (savedInstanceState == null && targetIntent != null) {
            intent?.removeExtra(EXTRA_TARGET_INTENT)
            intent?.removeExtra(EXTRA_INITIAL_CATEGORY_ID)
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
                    launchStackedCategory(targetIntent, initialCategoryId)
                }
            }
        }
    }

    private fun isRootHome(): Boolean =
        intent?.getBooleanExtra(EXTRA_IS_ROOT_HOME, false) == true

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
        if (isFinishing && isRootHome()) {
            HomeNavigationBridge.notifyAppExit()
        }
        Log.d(tag, "HomeActivity onDestroy()")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "HomeActivity onConfigurationChanged: $newConfig")
        HomeNavigationBridge.handleModeTransition(this, newConfig)
        showCategoryHeader.value = resolveShowCategoryHeader(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        val preservedIsRoot = isRootHome()
        super.onNewIntent(intent)
        if (preservedIsRoot && intent != null && !intent.hasExtra(EXTRA_IS_ROOT_HOME)) {
            intent.putExtra(EXTRA_IS_ROOT_HOME, true)
        }
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
        intent.removeExtra(EXTRA_TARGET_INTENT)
        intent.removeExtra(EXTRA_INITIAL_CATEGORY_ID)

        if (categoryId?.equals("home", ignoreCase = true) == true) {
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
            launchStackedCategory(targetIntent, categoryId)
        }
    }

    private fun launchStackedCategory(targetIntent: Intent, categoryId: String?) {
        val targetPkg = targetIntent.component?.packageName ?: targetIntent.`package`
        val isExternal = !targetPkg.isNullOrEmpty() && targetPkg != packageName
        if (isExternal && !categoryId.isNullOrEmpty()) {
            CategoryMenuTracker.onExternalCategoryLaunched(categoryId)
        }
        try {
            Log.d(tag, "HomeActivity launching stacked category (isExternal=$isExternal): $targetIntent")
            startActivity(targetIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        } catch (e: Exception) {
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
        launchStackedCategory(intent, item.categoryId)
    }
}
