package com.example.feature.home

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.app.ActivityOptions
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
        const val TAG_PRIMARY_OVERLAY = "tag_primary_overlay"
        var homeInstance: HomeActivity? = null
    }

    private val tag = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeInstance = this
        Log.d(tag, "HomeActivity created (isOverlayMode=${AppConfig.isOverlayMode(this)})")

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

        handleTargetLaunch(intent)
    }

    private var isLaunchingTarget = false

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
        Log.d(tag, "HomeActivity onResume: Home truly revealed in Secondary pane")
        HomeNavigationBridge.notifyHomeRevealed()
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "HomeActivity onPause()")
        isLaunchingTarget = false
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
                val optionsBundle = getLaunchOptionsBundle()
                startActivity(targetIntent, optionsBundle)
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
                    Log.d(tag, "Applied setLaunchingActivityStack targeting current stack: $token")
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
        val optionsBundle = getLaunchOptionsBundle()
        startActivity(intent, optionsBundle)
    }
}
