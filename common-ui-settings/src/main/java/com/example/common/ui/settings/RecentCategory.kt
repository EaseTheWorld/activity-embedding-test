package com.example.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
     * Checks if an item is eligible for Recent / Quick Controls display
     * by querying whether a grid card renderer exists in the given [gridRegistry].
     */
    fun isEligibleForRecent(item: Item, gridRegistry: ItemRendererRegistry): Boolean =
        gridRegistry.hasRenderer(item::class)
}

/**
 * Recent / Quick Controls Screen rendering items in a compact Grid format.
 * Notice: Uses [LocalGridItemRendererRegistry] to render compact grid cards.
 * Items with complex UIs that lack a Grid renderer are automatically and safely excluded!
 */
@Composable
fun RecentSettingsScreen(
    recentManager: RecentCategoryManager,
    itemResolver: (String) -> Item?,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    gridRendererRegistry: ItemRendererRegistry = LocalGridItemRendererRegistry.current,
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
                    // Eligibility check: Only render items that have a dedicated Grid renderer!
                    // Complex items (e.g. 10-band equalizers, 3D seat views) are safely skipped.
                    if (item != null && gridRendererRegistry.hasRenderer(item::class)) {
                        gridRendererRegistry.Render(
                            item = item,
                            viewModelRegistry = viewModelRegistry
                        )
                    }
                }
            }
        }
    }
}
