package com.example.common.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.core.item.ItemViewModelRegistry

/**
 * Universal Data-Driven Settings Activity powered by Jetpack Navigation 2.x and Jetpack Compose.
 * Supports:
 * 1. Root dashboard overview.
 * 2. Inter-category and intra-category item drill-down navigation.
 * 3. Deep link parsing for `myapp://navigate/{categoryId}/{itemId}`.
 * 4. External Activity Embedding delegation for external apps (e.g. Lights).
 */
open class GenericSettingsActivity : AppCompatActivity() {

    private val tag = "GenericSettingsActivity"
    private var pendingIntent = mutableStateOf<Intent?>(null)

    protected open fun getViewModelRegistry(): ItemViewModelRegistry = defaultViewModelRegistry

    private val recentManager by lazy {
        RecentCategoryManager(
            initialKeys = listOf("auto_lock", "easy_entry_exit", "unlock_on_park", "driver_seat_heat")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIntent.value = intent

        setContent {
            val navController = rememberNavController()

            // Handle incoming deep links or intent actions
            LaunchedEffect(pendingIntent.value) {
                val targetIntent = pendingIntent.value ?: return@LaunchedEffect
                handleIntentNavigation(targetIntent, navController)
            }

            BackHandler {
                if (!navController.popBackStack()) {
                    Log.d(tag, "At root of category Composable graph -> finishing secondary activity to reveal Home")
                    finish()
                }
            }

            CompositionLocalProvider(
                LocalItemViewModelRegistry provides getViewModelRegistry()
            ) {
                SettingsNavHost(
                    navController = navController,
                    viewModelRegistry = getViewModelRegistry(),
                    recentManager = recentManager,
                    onNavigateExternal = { categoryId ->
                        launchExternalCategory(categoryId)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    private fun handleIntentNavigation(intent: Intent, navController: NavHostController) {
        val uri: Uri? = intent.data
        if (uri != null && uri.scheme == SettingsNavigation.DEEP_LINK_SCHEME) {
            Log.d(tag, "Navigating via deep link: $uri")
            try {
                navController.navigate(uri)
                return
            } catch (e: Exception) {
                Log.w(tag, "Failed to navigate directly to uri $uri, attempting fallback: $e")
            }
        }

        // Fallback for legacy intent actions and extras
        val extraCat = intent.getStringExtra(EXTRA_CATEGORY_ID)
        val action = intent.action
        val categoryId = when {
            !extraCat.isNullOrEmpty() -> extraCat
            action == "com.example.carsettings.door.OPEN" -> "door"
            action == "com.example.carsettings.seat.OPEN" -> "seat"
            action == "com.example.carsettings.DASHBOARD" -> "dashboard"
            else -> null
        }

        if (categoryId != null) {
            if (categoryId.equals("dashboard", ignoreCase = true)) {
                navController.navigate(SettingsNavigation.ROUTE_DASHBOARD)
            } else if (categoryId.equals("light", ignoreCase = true)) {
                launchExternalCategory(categoryId)
            } else {
                navController.navigate(SettingsNavigation.categoryRoute(categoryId))
            }
        }
    }

    private fun launchExternalCategory(categoryId: String) {
        if (categoryId.equals("light", ignoreCase = true)) {
            val extIntent = Intent("com.example.carsettings.light.OPEN").apply {
                setClassName("com.example.carsettings.light", "com.example.feature.light.LightSettingsActivity")
            }
            try {
                startActivity(extIntent)
                Log.d(tag, "Launched external category activity for $categoryId")
            } catch (e: Exception) {
                Log.e(tag, "Failed to launch external activity for $categoryId: $e")
            }
        }
    }

    companion object {
        var defaultViewModelRegistry: ItemViewModelRegistry = ItemViewModelRegistry()
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_AUTHORITY = "extra_authority"
        const val EXTRA_TITLE = "extra_title"
    }
}
