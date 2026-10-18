package com.example.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.uifw.controller.ItemNavigator
import com.example.uifw.controller.LocalItemControllerRegistry
import com.example.uifw.controller.ScreenHostViewModel
import com.example.uifw.item.ItemScreen
import com.example.uifw.search.ItemSearchIndexer
import com.example.uifw.search.SearchIndexEntry
import com.example.uifw.ui.DrawTree
import com.example.uifw.ui.LocalHighlightKey
import com.example.uifw.ui.LocalItemNavigator

@Composable
fun SampleSettingsScreen(
    initialDeepLinkKey: String? = null,
    initialSearchQuery: String? = null,
    hostViewModel: ScreenHostViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val searchIndex = remember { ItemSearchIndexer.indexTree(SettingItems) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery ?: "") }

    // Helper: 특정 DeepLink 아이템/섹션 키로 이동할 때 그 화면의 Route 2 ("screen_route/{itemKey}") 경로 계산
    fun resolveItemNavigationRoute(targetKey: String): String {
        val matchingEntry = searchIndex.find { it.targetKey == targetKey }
        val baseRoute = matchingEntry?.screenRoute ?: targetKey.substringBeforeLast('/')
        val targetScreen = SettingItems.allScreens().find { it.route == baseRoute }
        return if (targetScreen != null) {
            val defaultArg = if (targetScreen.routeArg != null) "default_device" else null
            targetScreen.createItemRoute(targetKey, defaultArg)
        } else {
            targetKey
        }
    }

    // Initial Deeplink navigation: Route 2 ("screen_route/{itemKey}")로 직접 이동하여 앵커링 & 하이라이트
    LaunchedEffect(initialDeepLinkKey) {
        if (!initialDeepLinkKey.isNullOrBlank()) {
            val targetRoute = resolveItemNavigationRoute(initialDeepLinkKey)
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }
    }

    val itemNavigator = remember(navController) {
        object : ItemNavigator {
            override fun navigateToScreen(screen: ItemScreen) {
                val route = if (screen.routeArg != null) {
                    screen.createRoute("default_device")
                } else {
                    screen.route
                }
                navController.navigate(route)
            }

            override fun navigateToRoute(route: String) {
                navController.navigate(route)
            }
        }
    }

    CompositionLocalProvider(
        LocalItemControllerRegistry provides hostViewModel.itemRegistry,
        LocalItemNavigator provides itemNavigator
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("설정 항목 및 섹션 검색 (ex: Wi-Fi, 듀얼, 연결...)") },
                    leadingIcon = { Text("🔍") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✕")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (searchQuery.isNotBlank()) {
                    // Search results view
                    val searchResults = remember(searchQuery) {
                        ItemSearchIndexer.search(searchIndex, searchQuery)
                    }

                    Text(
                        text = "검색 결과 (${searchResults.size}건)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("검색 결과가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(searchResults, key = { it.targetKey }) { entry ->
                                SearchResultCard(
                                    entry = entry,
                                    onClick = {
                                        val itemRoute = resolveItemNavigationRoute(entry.targetKey)
                                        navController.navigate(itemRoute) {
                                            launchSingleTop = true
                                        }
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Screen NavHost: 각 Screen마다 2개의 Route를 자동 등록
                    // 1) screen.routePattern: 화면 자체로 이동하는 용도
                    // 2) screen.itemRoutePattern ("${screen.routePattern}/{itemKey}"): 화면 내 특정 아이템 앵커링 & 하이라이트 용도
                    NavHost(
                        navController = navController,
                        startDestination = SettingItems.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        SettingItems.allScreens().forEach { screen ->
                            // Route 1: 화면 자체 진입
                            composable(route = screen.routePattern) { backStackEntry ->
                                val dynamicArgValue = screen.routeArg?.let { argName ->
                                    backStackEntry.arguments?.getString(argName)
                                }
                                ItemScreenScaffold(
                                    screen = screen,
                                    dynamicArgValue = dynamicArgValue,
                                    initialHighlightKey = null,
                                    canNavigateBack = navController.previousBackStackEntry != null,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // Route 2: 화면 내 특정 아이템 앵커링 & 하이라이트 진입 ("screen_route/{itemKey}")
                            composable(route = screen.itemRoutePattern) { backStackEntry ->
                                val dynamicArgValue = screen.routeArg?.let { argName ->
                                    backStackEntry.arguments?.getString(argName)
                                }
                                val itemKeyArg = backStackEntry.arguments?.getString(ItemScreen.ITEM_KEY_ARG)
                                val resolvedHighlightKey = screen.resolveHighlightKey(itemKeyArg)
                                ItemScreenScaffold(
                                    screen = screen,
                                    dynamicArgValue = dynamicArgValue,
                                    initialHighlightKey = resolvedHighlightKey,
                                    canNavigateBack = navController.previousBackStackEntry != null,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    entry: SearchIndexEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (entry.isSection) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (entry.isSection) "섹션 (Section)" else "아이템 (Item)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isSection) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📍 계층: ${entry.formattedBreadcrumb}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "🔗 DeepLink: ${entry.targetKey}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ItemScreenScaffold(
    screen: ItemScreen,
    dynamicArgValue: String? = null,
    initialHighlightKey: String? = null,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit
) {
    var activeHighlightKey by remember(initialHighlightKey) { mutableStateOf(initialHighlightKey) }

    CompositionLocalProvider(LocalHighlightKey provides activeHighlightKey) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (activeHighlightKey != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🎯 DeepLink 앵커링 & 하이라이트 활성화됨",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = activeHighlightKey!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = { activeHighlightKey = null }) {
                            Text("강조 해제")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canNavigateBack) {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Column(modifier = Modifier.padding(start = if (canNavigateBack) 4.dp else 12.dp)) {
                    Text(
                        text = if (dynamicArgValue != null) "${screen.title} ($dynamicArgValue)" else screen.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = if (dynamicArgValue != null) {
                            "Route: ${screen.createRoute(dynamicArgValue)} (Pattern: ${screen.routePattern})"
                        } else {
                            "Route: ${screen.route}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider()
            DrawTree(tree = screen.tree) { customItem ->
                when (customItem) {
                    is UiAudioLatencyVisualizerItem -> {
                        AudioLatencyVisualizerCard(item = customItem)
                    }
                    else -> {
                        Text(
                            text = "Unsupported custom item: ${customItem.key}",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AudioLatencyVisualizerCard(
    item: UiAudioLatencyVisualizerItem,
    modifier: Modifier = Modifier
) {
    var latency by remember { mutableIntStateOf(item.defaultLatencyMs) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ " + (item.title ?: "Custom Item"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CUSTOM COMPONENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${latency}ms",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = "Key: ${item.key}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = latency == 20,
                    onClick = { latency = 20 },
                    label = { Text("초저지연 (20ms)") }
                )
                FilterChip(
                    selected = latency == 45,
                    onClick = { latency = 45 },
                    label = { Text("표준 게임 (45ms)") }
                )
                FilterChip(
                    selected = latency == 120,
                    onClick = { latency = 120 },
                    label = { Text("고음질 HD (120ms)") }
                )
            }
        }
    }
}

