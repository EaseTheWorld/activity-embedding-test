package com.example.common.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.core.item.CategoryItemProvider
import com.example.core.item.CategoryItemRegistry

/**
 * Universal Data-Driven Settings Activity powered by Jetpack Compose.
 * Renders any category's items dynamically via polymorphic ComposableItemRenderer.
 */
open class GenericSettingsActivity : AppCompatActivity() {

    private val tag = "GenericSettingsActivity"
    private var currentProvider by mutableStateOf<CategoryItemProvider?>(null)
    private var currentTitle by mutableStateOf("Settings")
    private var currentSubtitle by mutableStateOf("Data-driven items rendered via Jetpack Compose")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resolveCategory(intent)

        setContent {
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
            action == "com.example.carsettings.door.OPEN" -> "door"
            action == "com.example.carsettings.seat.OPEN" -> "seat"
            else -> "door"
        }

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
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_AUTHORITY = "extra_authority"
        const val EXTRA_TITLE = "extra_title"
    }
}
