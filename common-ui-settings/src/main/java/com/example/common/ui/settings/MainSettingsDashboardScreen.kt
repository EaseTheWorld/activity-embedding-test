package com.example.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.item.CategoryItemProvider
import com.example.core.item.Item
import com.example.core.item.ItemViewModelRegistry

/**
 * Composable slot type for a category's custom layout.
 * Gives complete layout freedom to features (e.g. dual switches side-by-side, 2D coordinates, 3D views)
 * with full Compose expressiveness.
 */
typealias CategoryLayoutSlot = @Composable (items: List<Item>, viewModelRegistry: ItemViewModelRegistry) -> Unit

/**
 * Unified Vehicle Settings Dashboard Screen.
 *
 * Layout Structure:
 * 1. Top Section: Recent / Quick Controls in a compact 2-column grid.
 * 2. Bottom Section: Actual Categories collected from [CategoryItemProvider]s.
 *
 * Notice on Screen-Independent Declarative Composition (ADR 0004 & ADR 0005):
 * Category screens compose standard rows ([ToggleItemRow], [ChoiceItemRow], [SliderItemRow]) and custom
 * domain composables directly inside native Jetpack Compose containers, with ItemViewModel guaranteed by ID.
 */
@Composable
fun MainSettingsDashboardScreen(
    recentManager: RecentCategoryManager,
    providers: List<CategoryItemProvider>,
    itemResolver: (String) -> Item?,
    onCategoryClick: ((String) -> Unit)? = null,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    customCategoryLayouts: Map<String, CategoryLayoutSlot> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val recentKeys by recentManager.recentKeys.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF6F7FB)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Dashboard Header
            Text(
                text = "Vehicle Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1E2E)
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Quick Controls and Category Overview",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6B7280))
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ================================================================
            // 1. TOP SECTION: Recent (Quick Controls) - Compact 2-Column Grid
            // ================================================================
            val recentItems = recentKeys.mapNotNull { itemResolver(it) }
            if (recentItems.isNotEmpty()) {
                Text(
                    text = "Quick Controls (Recent)",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E2E)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Render in chunks of 2 for a clean, non-nested 2-column grid
                recentItems.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                DashboardGridItemCard(
                                    item = item,
                                    viewModelRegistry = viewModelRegistry
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ================================================================
            // 2. BOTTOM SECTION: Actual Categories (Registry-Free!)
            // ================================================================
            Text(
                text = "All Categories",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1E2E)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            providers.forEach { provider ->
                CategoryCardSection(
                    provider = provider,
                    viewModelRegistry = viewModelRegistry,
                    customContent = customCategoryLayouts[provider.categoryId],
                    onCategoryClick = onCategoryClick
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Renders an individual category in a rounded Card container.
 * Notice: Completely declarative Compose layout.
 */
@Composable
fun CategoryCardSection(
    provider: CategoryItemProvider,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    customContent: CategoryLayoutSlot? = null,
    onCategoryClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Category Header (Resolved from Category metadata)
            val context = LocalContext.current
            val titleRes = context.resources.getIdentifier(provider.titleKey, "string", context.packageName)
            val titleText = if (titleRes != 0) context.getString(titleRes) else provider.titleKey

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E2E)
                    )
                )
                if (onCategoryClick != null) {
                    IconButton(
                        onClick = { onCategoryClick(provider.categoryId) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open $titleText",
                            tint = Color(0xFF6750A4)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Items Content: Either a domain-specific custom layout or default direct Compose rows
            if (customContent != null) {
                customContent(provider.items, viewModelRegistry)
            } else {
                DefaultCategoryItemsContent(
                    items = provider.items,
                    viewModelRegistry = viewModelRegistry
                )
            }
        }
    }
}

/**
 * Default direct Compose layout for category items.
 * Renders items via direct Composable rows.
 */
@Composable
fun DefaultCategoryItemsContent(
    items: List<Item>,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isItemVisible by item.isVisible.collectAsState(initial = true)
            val vm = viewModelRegistry.getViewModel<Any>(item.id)
            val isVmVisible = (vm?.isVisibleFlow?.collectAsState())?.value ?: true
            if (isItemVisible && isVmVisible) {
                when (item) {
                    is UiToggleItem -> {
                        ToggleItemRow(item = item, viewModelRegistry = viewModelRegistry)
                    }
                    is UiChoiceItem -> {
                        ChoiceItemRow(item = item, viewModelRegistry = viewModelRegistry)
                    }
                    is UiSliderItem -> {
                        SliderItemRow(item = item, viewModelRegistry = viewModelRegistry)
                    }
                    is UiItem -> {
                        Text(
                            text = stringResource(item.nameResId),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = "Item: ${item.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                if (index < items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color(0xFFF1F2F6)
                    )
                }
            }
        }
    }
}

/**
 * Compact grid card renderer for the top Recent / Quick Controls section.
 */
@Composable
fun DashboardGridItemCard(
    item: Item,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val isItemVisible by item.isVisible.collectAsState(initial = true)
    val vm = viewModelRegistry.getViewModel<Any>(item.id)
    val isVmVisible = (vm?.isVisibleFlow?.collectAsState())?.value ?: true
    if (!isItemVisible || !isVmVisible) return

    when (item) {
        is UiToggleItem -> {
            ToggleGridCard(item = item, viewModelRegistry = viewModelRegistry, modifier = modifier)
        }
        is UiChoiceItem -> {
            ChoiceGridCard(item = item, viewModelRegistry = viewModelRegistry, modifier = modifier)
        }
        is UiSliderItem -> {
            SliderGridCard(item = item, viewModelRegistry = viewModelRegistry, modifier = modifier)
        }
        else -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = item.id, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Example helper demonstrating layout freedom (ADR 0003):
 * Renders two independent [UiToggleItem]s side-by-side in a single row.
 * Shows why direct Compose composition without a Registry provides superior layout flexibility.
 */
@Composable
fun DualToggleRow(
    item1: UiToggleItem,
    item2: UiToggleItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ToggleItemRow(item = item1, viewModelRegistry = viewModelRegistry)
        }
        Box(modifier = Modifier.weight(1f)) {
            ToggleItemRow(item = item2, viewModelRegistry = viewModelRegistry)
        }
    }
}
