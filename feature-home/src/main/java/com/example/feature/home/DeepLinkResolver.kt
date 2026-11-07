package com.example.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

data class ResolvedDeepLink(
    val categoryId: String,
    val targetIntent: Intent?
)

object DeepLinkResolver {

    const val ACTION_SETTINGS_EMBED = "com.example.carsettings.ACTION_SETTINGS_EMBED"

    fun isDeepLinkIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val dataUri = intent.data
        if (dataUri != null && dataUri.scheme == "myapp" && dataUri.host == "navigate") {
            return true
        }
        return intent.action == "com.example.lifecycleapp.open"
    }

    fun resolve(context: Context, intent: Intent?): ResolvedDeepLink? {
        if (intent == null) return null
        val dataUri = intent.data
        val extras = intent.extras

        if (dataUri != null && dataUri.scheme == "myapp" && dataUri.host == "navigate") {
            val segments = dataUri.pathSegments
            val categoryId = when {
                segments.isEmpty() -> "home"
                segments[0].equals("home", ignoreCase = true) -> "home"
                segments[0].equals("dashboard", ignoreCase = true) -> "dashboard"
                else -> segments[0]
            }
            if (categoryId == "home") {
                return ResolvedDeepLink(categoryId = "home", targetIntent = null)
            }
            val targetIntent = buildCategoryTargetIntent(context, categoryId, dataUri, extras)
            return ResolvedDeepLink(categoryId = categoryId, targetIntent = targetIntent)
        }

        if (intent.action == "com.example.lifecycleapp.open") {
            @Suppress("DEPRECATION")
            val categoryId = intent.getStringExtra("category") ?: when (extras?.get("id")) {
                1, "1" -> "light"
                2, "2" -> "sound"
                3, "3" -> "display"
                else -> null
            } ?: return null

            if (categoryId.equals("home", ignoreCase = true)) {
                return ResolvedDeepLink(categoryId = "home", targetIntent = null)
            }
            val syntheticUri = Uri.parse("myapp://navigate/$categoryId")
            val targetIntent = buildCategoryTargetIntent(context, categoryId, syntheticUri, extras)
            return ResolvedDeepLink(categoryId = categoryId, targetIntent = targetIntent)
        }

        return null
    }

    fun buildCategoryTargetIntent(
        context: Context,
        categoryId: String,
        uri: Uri,
        extras: Bundle?
    ): Intent {
        return if (categoryId.equals("light", ignoreCase = true)) {
            Intent(ACTION_SETTINGS_EMBED).apply {
                setClassName(
                    "com.example.carsettings.light",
                    "com.example.feature.light.LightSettingsActivity"
                )
                data = uri
                putExtra("category_id", categoryId)
                if (extras != null) putExtras(extras)
            }
        } else {
            Intent(ACTION_SETTINGS_EMBED).apply {
                component = ComponentName(
                    context.packageName,
                    "com.example.lifecycleapp.GenericSettingsActivity"
                )
                data = uri
                putExtra("category_id", categoryId)
                if (extras != null) putExtras(extras)
            }
        }
    }
}
