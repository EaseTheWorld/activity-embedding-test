package com.example.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.item.Item
import com.example.core.item.ItemViewModelRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the list of recently used setting item keys.
 * Demonstrates why an Item-Driven UI is required: The Recent category only maintains
 * lightweight keys (`id`s), while the Item-Driven framework handles dynamic multi-source
 * resolution and Grid rendering seamlessly.
 */
class RecentCategoryManager(
    initialKeys: List<String> = emptyList(),
    private val maxRecentItems: Int = 6
) {
    private val _recentKeys = MutableStateFlow(initialKeys)
    val recentKeys: StateFlow<List<String>> = _recentKeys.asStateFlow()

    fun recordUsage(itemId: String) {
        val current = _recentKeys.value.toMutableList()
        current.remove(itemId)
        current.add(0, itemId)
        if (current.size > maxRecentItems) {
            _recentKeys.value = current.take(maxRecentItems)
        } else {
            _recentKeys.value = current
        }
    }

    /**
     * Checks if an item is eligible for Recent / Quick Controls display.
     * Screens have full autonomy over eligibility: by default, compact items (Toggle, Choice, Slider)
     * are eligible, but a custom predicate can be provided.
     */
    fun isEligibleForRecent(
        item: Item,
        predicate: (Item) -> Boolean = { it is UiToggleItem || it is UiChoiceItem || it is UiSliderItem }
    ): Boolean = predicate(item)
}

/**
 * Screen-specific compact card presentation for [RecentSettingsScreen].
 * Each screen independently decides how to render the same Item, while resolving
 * the shared [com.example.core.item.ItemViewModel] by ID.
 */
@Composable
fun RecentItemCard(
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
 * Recent / Quick Controls Screen rendering items in a compact Grid format.
 * Notice: Zero ItemRendererRegistry usage! UI rendering is screen-independent,
 * while ItemViewModel single-source-of-truth is guaranteed by ID.
 */
@Composable
fun RecentSettingsScreen(
    recentManager: RecentCategoryManager,
    itemResolver: (String) -> Item?,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    itemCardContent: @Composable (Item, ItemViewModelRegistry) -> Unit = { item, vmRegistry ->
        RecentItemCard(item = item, viewModelRegistry = vmRegistry)
    },
    modifier: Modifier = Modifier
) {
    val recentKeys by recentManager.recentKeys.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF6F7FB)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Quick Controls (Recent)",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E1E2E)
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Frequently accessed items rendered as compact grid cards",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6B7280))
            )
            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recentKeys, key = { it }) { key ->
                    val item = itemResolver(key)
                    if (item != null && recentManager.isEligibleForRecent(item)) {
                        itemCardContent(item, viewModelRegistry)
                    }
                }
            }
        }
    }
}

