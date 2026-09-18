package com.example.lifecycleapp

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCategoryPlacementTest {

    @Test
    fun `homeCategory has order minus 10 and is always first in sorted list`() {
        val homeCategory = SettingCategory(
            id = "home",
            title = "Home",
            subtitle = "Search & Quick access",
            order = -10,
            targetAction = "com.example.carsettings.HOME",
            targetPackage = "com.example.lifecycleapp",
            targetActivity = "com.example.feature.home.HomeActivity",
            iconName = "ic_feature_home",
            authority = "local"
        )

        val dashboardCategory = SettingCategory(
            id = "dashboard",
            title = "Quick Controls & All",
            subtitle = "Recent items and category overview",
            order = -1,
            targetAction = "com.example.carsettings.DASHBOARD",
            targetPackage = "com.example.lifecycleapp",
            targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
            iconName = null,
            authority = "local"
        )

        val discoveredCategories = listOf(
            SettingCategory(
                id = "seat",
                title = "Seats",
                subtitle = "Heating, ventilation, massage",
                order = 0,
                targetAction = null,
                targetPackage = "com.example.lifecycleapp",
                targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
                iconName = null,
                authority = "com.example.carsettings.provider.seat"
            ),
            SettingCategory(
                id = "door",
                title = "Doors & Locks",
                subtitle = "Auto lock, child lock",
                order = 1,
                targetAction = null,
                targetPackage = "com.example.lifecycleapp",
                targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
                iconName = null,
                authority = "com.example.carsettings.provider.door"
            ),
            SettingCategory(
                id = "light",
                title = "Lighting",
                subtitle = "Headlights, ambient lights",
                order = 2,
                targetAction = null,
                targetPackage = "com.example.carsettings.light",
                targetActivity = "com.example.feature.light.LightSettingsActivity",
                iconName = null,
                authority = "com.example.carsettings.provider.light"
            )
        )

        val sortedCategories = (listOf(homeCategory, dashboardCategory) + discoveredCategories).sortedBy { it.order }

        assertEquals("home", sortedCategories[0].id)
        assertEquals("Home", sortedCategories[0].title)
        assertEquals("dashboard", sortedCategories[1].id)
        assertEquals("seat", sortedCategories[2].id)
        assertEquals("door", sortedCategories[3].id)
        assertEquals("light", sortedCategories[4].id)
    }

    @Test
    fun `default deepLink URI resolution prioritizes home on empty segment`() {
        val uri = URI.create("myapp://navigate")
        val segments = uri.path?.removePrefix("/")?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
        val categoryId = when {
            segments.isEmpty() -> "home"
            segments[0].equals("home", ignoreCase = true) -> "home"
            segments[0].equals("dashboard", ignoreCase = true) -> "dashboard"
            else -> segments[0]
        }
        assertEquals("home", categoryId)
    }
}
