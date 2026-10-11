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
import androidx.window.embedding.setLaunchingActivityStack
import androidx.window.embedding.setOverlayCreateParams
import com.example.core.item.AppConfig

class HomeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_INTENT = "extra_target_intent"
        const val EXTRA_INITIAL_DEEPLINK_URI = "extra_initial_deeplink_uri"
        const val EXTRA_INITIAL_DEEPLINK_ACTION = "extra_initial_deeplink_action"
        const val EXTRA_INITIAL_DEEPLINK_EXTRAS = "extra_initial_deeplink_extras"
        const val REQUEST_CODE_CATEGORY = 1001
        var homeInstance: HomeActivity? = null
    }

    private val tag = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeInstance = this
        val isNarrowOverlayMode = AppConfig.isOverlayMode(this)
        Log.d(tag, "HomeActivity created (isNarrowOverlayMode=$isNarrowOverlayMode)")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(tag, "Back pressed at Home (Root of App) -> finishAffinity to exit")
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

        if (isNarrowOverlayMode && savedInstanceState == null) {
            launchPrimaryPinnedOverlay(intent)
        }

        handleTargetLaunch(intent)
    }

    /**
     * In Narrow Mode, HomeActivity is the fullscreen Task root and spawns PrimaryOverlayActivity
     * as an Always-On-Top Pinned Overlay (`shouldAssociateWithLaunchingActivity = false`).
     */
    private fun launchPrimaryPinnedOverlay(hostIntent: Intent?) {
        try {
            val dpPx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * resources.displayMetrics.density).toInt()
            val bounds = EmbeddingBounds(
                EmbeddingBounds.Alignment.ALIGN_LEFT,
                EmbeddingBounds.Dimension.pixel(dpPx),
                EmbeddingBounds.Dimension.pixel(dpPx)
            )
            val overlayParams = OverlayCreateParams.Builder()
                .setTag(AppConfig.TAG_PRIMARY_OVERLAY)
                .setOverlayAttributes(OverlayAttributes(bounds))
                .build()

            val options = ActivityOptions.makeBasic().toBundle()
            options.setOverlayCreateParams(this, overlayParams)
            options.putBoolean(
                "androidx.window.extensions.embedding.shouldAssociateWithLaunchingActivity",
                false
            )

            val overlayIntent = Intent().apply {
                component = ComponentName(packageName, "com.example.lifecycleapp.PrimaryOverlayActivity")
                hostIntent?.getStringExtra(EXTRA_INITIAL_DEEPLINK_ACTION)?.let { action = it }
                hostIntent?.getStringExtra(EXTRA_INITIAL_DEEPLINK_URI)?.let { data = Uri.parse(it) }
                hostIntent?.getBundleExtra(EXTRA_INITIAL_DEEPLINK_EXTRAS)?.let { putExtras(it) }
            }

            Log.i(tag, "Launching PrimaryOverlayActivity as Always-On-Top Pinned Overlay in Narrow Mode (bounds=$bounds)")
            startActivity(overlayIntent, options)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch PrimaryOverlayActivity pinned overlay: $e", e)
        }
    }

    /**
     * Launches a category activity directly into HomeActivity's TaskFragment on top of HomeActivity,
     * finishing any previously active category activity (`REQUEST_CODE_CATEGORY`) first so categories
     * never stack on top of each other.
     */
    private var isLaunchingTarget = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val clearLaunchingFlagRunnable = Runnable {
        isLaunchingTarget = false
    }

    @Suppress("DEPRECATION")
    fun launchCategoryInTask(targetIntent: Intent) {
        isLaunchingTarget = true
        mainHandler.removeCallbacks(clearLaunchingFlagRunnable)
        mainHandler.postDelayed(clearLaunchingFlagRunnable, 400L)
        try {
            finishActivity(REQUEST_CODE_CATEGORY)
        } catch (e: Exception) {
            Log.w(tag, "finishActivity(REQUEST_CODE_CATEGORY) warning: $e")
        }
        try {
            val optionsBundle = getLaunchOptionsBundle()
            Log.d(tag, "HomeActivity launching category in Home's TaskFragment (options=$optionsBundle): $targetIntent")
            startActivityForResult(targetIntent, REQUEST_CODE_CATEGORY, optionsBundle)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch category in task: $e", e)
            isLaunchingTarget = false
            mainHandler.removeCallbacks(clearLaunchingFlagRunnable)
        }
    }

    /**
     * Finishes the currently active category activity (internal or external) to reveal HomeActivity underneath.
     */
    fun returnToHome() {
        isLaunchingTarget = false
        mainHandler.removeCallbacks(clearLaunchingFlagRunnable)
        try {
            Log.d(tag, "HomeActivity returnToHome -> finishActivity(REQUEST_CODE_CATEGORY)")
            finishActivity(REQUEST_CODE_CATEGORY)
        } catch (e: Exception) {
            Log.w(tag, "returnToHome finishActivity warning: $e")
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "HomeActivity onStart()")
    }

    override fun onResume() {
        super.onResume()
        if (isLaunchingTarget) {
            Log.d(tag, "HomeActivity onResume: skipped bridge notification because target category is being launched")
            return
        }
        Log.d(tag, "HomeActivity onResume: Home truly revealed")
        HomeNavigationBridge.notifyHomeRevealed()
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "HomeActivity onPause()")
        isLaunchingTarget = false
        mainHandler.removeCallbacks(clearLaunchingFlagRunnable)
    }

    override fun onStop() {
        super.onStop()
        Log.d(tag, "HomeActivity onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (homeInstance == this) {
            homeInstance = null
        }
        Log.d(tag, "HomeActivity onDestroy()")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "HomeActivity onConfigurationChanged: $newConfig")
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
        if (targetIntent != null) {
            intent.removeExtra(EXTRA_TARGET_INTENT)
            isLaunchingTarget = true
            try {
                Log.d(tag, "HomeActivity launching target category on top: $targetIntent")
                launchCategoryInTask(targetIntent)
            } catch (e: Exception) {
                Log.e(tag, "Failed to launch target intent: $e")
                isLaunchingTarget = false
            }
        }
    }

    private fun getLaunchOptionsBundle(): Bundle? {
        return try {
            val token = androidx.window.embedding.ActivityEmbeddingController.getInstance(this)
                .getActivityStack(this)
            if (token != null) {
                Bundle().apply {
                    setLaunchingActivityStack(this@HomeActivity, token)
                    Log.d(tag, "Applied setLaunchingActivityStack targeting Home's stack: $token")
                }
            } else null
        } catch (e: Exception) {
            Log.w(tag, "Failed to get current ActivityStack token: $e")
            null
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
        }

        Log.d(tag, "Launching target setting from Home search: $targetComponent, uri=$targetUri, categoryId=${item.categoryId}")
        HomeNavigationBridge.notifyCategorySelected(item.categoryId)
        launchCategoryInTask(intent)
    }
}
