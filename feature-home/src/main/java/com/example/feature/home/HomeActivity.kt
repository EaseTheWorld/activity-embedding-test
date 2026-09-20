package com.example.feature.home

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent

class HomeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_INTENT = "extra_target_intent"
    }

    private val tag = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "HomeActivity created in Secondary pane")

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

    override fun onResume() {
        super.onResume()
        Log.d(tag, "HomeActivity onResume: revealed in Secondary pane")
        HomeNavigationBridge.notifyHomeRevealed()
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
            Log.d(tag, "HomeActivity launching target category on top: $targetIntent")
            startActivity(targetIntent)
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

        Log.d(tag, "Launching target setting from Home search: $targetComponent, uri=$targetUri")
        startActivity(intent)
    }
}
