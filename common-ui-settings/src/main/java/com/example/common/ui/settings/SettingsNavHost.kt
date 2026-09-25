package com.example.common.ui.settings

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.core.item.CategoryItemProvider
import com.example.core.item.CategoryItemRegistry
import com.example.core.item.Item
import com.example.core.item.ItemViewModelRegistry

/**
 * Navigation Destinations and Deep Link Route Definitions.
 *
 * Deep Link URI Pattern:
 * - Root / Dashboard: `myapp://navigate` or `myapp://navigate/dashboard`
 * - Inter-Category: `myapp://navigate/{categoryId}`
 * - Intra-Category (Item Detail): `myapp://navigate/{categoryId}/{itemId}`
 */
object SettingsNavigation {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_CATEGORY = "navigate/{categoryId}"
    const val ROUTE_ITEM_DETAIL = "navigate/{categoryId}/{itemId}?value={value}"
    const val ROUTE_SEAT_LUMBAR = "navigate/seat/seat_lumbar?value={value}"

    const val DEEP_LINK_SCHEME = "myapp"
    const val DEEP_LINK_HOST = "navigate"

    fun categoryRoute(categoryId: String): String = "navigate/$categoryId"
    fun itemDetailRoute(categoryId: String, itemId: String, value: String? = null): String =
        if (value != null) "navigate/$categoryId/$itemId?value=$value" else "navigate/$categoryId/$itemId"

    fun categoryDeepLink(categoryId: String): String = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$categoryId"
    fun itemDetailDeepLink(categoryId: String, itemId: String, value: String? = null): String =
        if (value != null) "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$categoryId/$itemId?value=$value" else "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$categoryId/$itemId"
}

/**
 * Universal Navigation 2.x NavHost for Car Settings.
 *
 * Features:
 * 1. **Dashboard Overview**: Start destination displaying quick controls and all categories.
 * 2. **Inter-Category Navigation**: Navigates between categories via [SettingsNavigation.categoryRoute].
 * 3. **Intra-Category Navigation**: Drills down into individual setting items via [SettingsNavigation.itemDetailRoute].
 * 4. **Deep Linking**: First-class deep link URI resolution for `myapp://navigate/{categoryId}/{itemId}`.
 * 5. **External App Delegation**: Bridges external app categories (e.g. `light`) to [onNavigateExternal].
 */
@Composable
fun SettingsNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = SettingsNavigation.ROUTE_DASHBOARD,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    recentManager: RecentCategoryManager = RecentCategoryManager(
        initialKeys = listOf("auto_lock", "easy_entry_exit", "unlock_on_park", "driver_seat_heat")
    ),
    onNavigateExternal: (categoryId: String) -> Unit = {}
) {
    val context: Context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // ====================================================================
        // 1. Dashboard Destination
        // ====================================================================
        composable(
            route = SettingsNavigation.ROUTE_DASHBOARD,
            deepLinks = listOf(
                navDeepLink { uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}" },
                navDeepLink { uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}/dashboard" }
            )
        ) {
            val providers = CategoryItemRegistry.getAllProviders().toList()
            val itemResolver: (String) -> Item? = { key ->
                providers.firstNotNullOfOrNull { it.findItem(key) }
            }

            MainSettingsDashboardScreen(
                recentManager = recentManager,
                providers = providers,
                itemResolver = itemResolver,
                viewModelRegistry = viewModelRegistry,
                onCategoryClick = { categoryId ->
                    if (categoryId.equals("light", ignoreCase = true)) {
                        onNavigateExternal(categoryId)
                    } else {
                        navController.navigate(SettingsNavigation.categoryRoute(categoryId))
                    }
                }
            )
        }

        // ====================================================================
        // 2. Inter-Category Destination (Full Category Screen)
        // ====================================================================
        composable(
            route = SettingsNavigation.ROUTE_CATEGORY,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}/{categoryId}"
                }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""

            if (categoryId.equals("light", ignoreCase = true)) {
                onNavigateExternal(categoryId)
                return@composable
            }

            val provider: CategoryItemProvider? = CategoryItemRegistry.getProvider(categoryId)
            val titleRes = provider?.titleKey?.let { key ->
                context.resources.getIdentifier(key, "string", context.packageName)
            } ?: 0
            val titleText = if (titleRes != 0) context.getString(titleRes) else (provider?.titleKey ?: categoryId)

            GenericSettingsScreen(
                title = titleText,
                subtitle = "Settings for $titleText",
                items = provider?.items ?: emptyList(),
                viewModelRegistry = viewModelRegistry,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(SettingsNavigation.ROUTE_DASHBOARD) {
                            popUpTo(SettingsNavigation.ROUTE_DASHBOARD) { inclusive = true }
                        }
                    }
                },
                onItemClick = { item ->
                    // Intra-category navigation: drill down into item detail
                    navController.navigate(SettingsNavigation.itemDetailRoute(categoryId, item.id))
                }
            )
        }

        // ====================================================================
        // 3. Dedicated Detail Destination: Seat Lumbar Support (Fixed Route)
        // ====================================================================
        composable(
            route = SettingsNavigation.ROUTE_SEAT_LUMBAR,
            arguments = listOf(
                navArgument("value") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}/seat/seat_lumbar?value={value}"
                },
                navDeepLink {
                    uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}/seat/seat_lumbar"
                }
            )
        ) { backStackEntry ->
            val value = backStackEntry.arguments?.getString("value")
            LaunchedEffect(value) {
                if (!value.isNullOrEmpty()) {
                    ItemSetterHelper.applyValue(viewModelRegistry, "seat_lumbar", value)
                }
            }

            val provider = CategoryItemRegistry.getProvider("seat")
            val item = provider?.findItem("seat_lumbar")
            val titleRes = provider?.titleKey?.let { key ->
                context.resources.getIdentifier(key, "string", context.packageName)
            } ?: 0
            val categoryTitle = if (titleRes != 0) context.getString(titleRes) else "Seat"

            ItemDetailScreen(
                categoryId = "seat",
                categoryTitle = categoryTitle,
                item = item,
                viewModelRegistry = viewModelRegistry,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(SettingsNavigation.categoryRoute("seat")) {
                            popUpTo(SettingsNavigation.categoryRoute("seat")) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ====================================================================
        // 4. Category Destination with Item Anchor (Wildcard Route)
        // ====================================================================
        composable(
            route = SettingsNavigation.ROUTE_ITEM_DETAIL,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("itemId") { type = NavType.StringType },
                navArgument("value") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}/{categoryId}/{itemId}?value={value}"
                },
                navDeepLink {
                    uriPattern = "${SettingsNavigation.DEEP_LINK_SCHEME}://${SettingsNavigation.DEEP_LINK_HOST}/{categoryId}/{itemId}"
                }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val value = backStackEntry.arguments?.getString("value")

            LaunchedEffect(itemId, value) {
                if (!value.isNullOrEmpty()) {
                    ItemSetterHelper.applyValue(viewModelRegistry, itemId, value)
                }
            }

            if (categoryId.equals("light", ignoreCase = true)) {
                onNavigateExternal(categoryId)
                return@composable
            }

            val provider = CategoryItemRegistry.getProvider(categoryId)
            val titleRes = provider?.titleKey?.let { key ->
                context.resources.getIdentifier(key, "string", context.packageName)
            } ?: 0
            val categoryTitle = if (titleRes != 0) context.getString(titleRes) else (provider?.titleKey ?: categoryId)

            GenericSettingsScreen(
                title = categoryTitle,
                subtitle = "Settings for $categoryTitle",
                items = provider?.items ?: emptyList(),
                targetItemId = itemId,
                viewModelRegistry = viewModelRegistry,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(SettingsNavigation.ROUTE_DASHBOARD) {
                            popUpTo(SettingsNavigation.ROUTE_DASHBOARD) { inclusive = true }
                        }
                    }
                },
                onItemClick = { clickedItem ->
                    if (clickedItem.hasDetailScreen) {
                        navController.navigate(SettingsNavigation.itemDetailRoute(categoryId, clickedItem.id))
                    }
                }
            )
        }
    }
}
