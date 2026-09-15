package com.example.common.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.core.item.CategoryItemProvider
import com.example.core.item.CategoryItemRegistry
import com.example.core.item.ItemViewModelRegistry

/**
 * Universal Data-Driven Settings Activity powered by Jetpack Compose.
 * Renders any category's items dynamically via declarative Compose UI.
 */
open class GenericSettingsActivity : AppCompatActivity() {

    private val tag = "GenericSettingsActivity"
    private var isDashboard by mutableStateOf(false)
    private var currentProvider by mutableStateOf<CategoryItemProvider?>(null)
    private var currentTitle by mutableStateOf("Settings")
    private var currentSubtitle by mutableStateOf("Data-driven items rendered via Jetpack Compose")

    protected open fun getViewModelRegistry(): ItemViewModelRegistry = defaultViewModelRegistry

    private val recentManager by lazy {
        RecentCategoryManager(
            initialKeys = listOf("auto_lock", "easy_entry_exit", "unlock_on_park", "driver_seat_heat")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resolveCategory(intent)

        setContent {
            CompositionLocalProvider(
                LocalItemViewModelRegistry provides getViewModelRegistry()
            ) {
                if (isDashboard) {
                    val providers = CategoryItemRegistry.getAllProviders().toList()
                    val itemResolver: (String) -> com.example.core.item.Item? = { key ->
                        providers.firstNotNullOfOrNull { it.findItem(key) }
                    }
                    MainSettingsDashboardScreen(
                        recentManager = recentManager,
                        providers = providers,
                        itemResolver = itemResolver
                    )
                } else {
                    val provider = currentProvider
                    if (provider != null) {
                        GenericSettingsScreen(
                            title = currentTitle,
                            subtitle = currentSubtitle,
                            items = provider.items
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveCategory(intent)
    }

    private fun resolveCategory(intent: Intent?) {
        val extraCat = intent?.getStringExtra(EXTRA_CATEGORY_ID)
        val action = intent?.action

        val categoryId = when {
            !extraCat.isNullOrEmpty() -> extraCat
            action == "com.example.carsettings.DASHBOARD" -> "dashboard"
            action == "com.example.carsettings.door.OPEN" -> "door"
            action == "com.example.carsettings.seat.OPEN" -> "seat"
            else -> "dashboard"
        }

        if (categoryId.equals("dashboard", ignoreCase = true)) {
            isDashboard = true
            currentTitle = "Vehicle Settings"
            currentSubtitle = "Quick Controls and Category Overview"
            Log.d(tag, "Rendered dashboard via Compose")
            return
        }

        isDashboard = false
        val provider = CategoryItemRegistry.getProvider(categoryId)
        if (provider == null) {
            Log.e(tag, "No CategoryItemProvider found for categoryId: $categoryId")
            currentProvider = null
            currentTitle = "Settings"
            currentSubtitle = "Category not found: $categoryId"
            return
        }

        val extraTitle = intent?.getStringExtra(EXTRA_TITLE)
        val resId = resources.getIdentifier(provider.titleKey, "string", packageName)
        currentTitle = extraTitle ?: if (resId != 0) getString(resId) else provider.titleKey
        currentSubtitle = "Data-driven items rendered via Jetpack Compose"
        currentProvider = provider

        Log.d(tag, "Rendered category via Compose: $categoryId with ${provider.items.size} items")
    }

    companion object {
        var defaultViewModelRegistry: ItemViewModelRegistry = ItemViewModelRegistry()
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_AUTHORITY = "extra_authority"
        const val EXTRA_TITLE = "extra_title"
    }
}
