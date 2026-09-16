package com.example.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.item.Item
import com.example.core.item.ItemViewModelRegistry

/**
 * Reusable Intra-Category Item Detail Screen.
 * Provides a dedicated full-page drill-down view for an individual [Item] within its category.
 *
 * Supports Back/Up navigation via [onNavigateBack], renders rich controls and child items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    categoryId: String,
    categoryTitle: String,
    item: Item?,
    onNavigateBack: () -> Unit,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = categoryTitle,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6750A4))
                        )
                        Text(
                            text = if (item is UiItem) stringResource(item.nameResId) else (item?.id ?: "Item Detail"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to $categoryTitle"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1E1E2E)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = Color(0xFFF6F7FB)
        ) {
            if (item == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Setting item not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                return@Surface
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Item Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconRes = (item as? UiItem)?.iconRes
                            if (iconRes != null) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .padding(end = 12.dp),
                                    tint = Color(0xFF6750A4)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (item is UiItem) stringResource(item.nameResId) else item.id,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E1E2E)
                                        )
                                    )
                                    val badge = (item as? UiToggleItem)?.badgeKey
                                    if (badge != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(
                                            containerColor = Color(0xFF6750A4),
                                            contentColor = Color.White
                                        ) {
                                            Text(text = badge, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                val descRes = (item as? UiItem)?.descriptionResId
                                if (descRes != null && descRes != 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6B7280))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = Color(0xFFF1F2F6))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Interactive Control Row
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
                            else -> {
                                Text(
                                    text = "Custom Setting Control (${item.id})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // If this item has sub-items / children (e.g. auto_relock under auto_lock)
                if (item.children.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Related & Dependent Settings",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E2E)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            item.children.forEachIndexed { index, child ->
                                val isItemVisible by child.isVisible.collectAsState(initial = true)
                                val vm = viewModelRegistry.getViewModel<Any>(child.id)
                                val isVmVisible = (vm?.isVisibleFlow?.collectAsState())?.value ?: true
                                if (isItemVisible && isVmVisible) {
                                    when (child) {
                                        is UiToggleItem -> ToggleItemRow(item = child, viewModelRegistry = viewModelRegistry)
                                        is UiChoiceItem -> ChoiceItemRow(item = child, viewModelRegistry = viewModelRegistry)
                                        is UiSliderItem -> SliderItemRow(item = child, viewModelRegistry = viewModelRegistry)
                                        else -> Text(text = child.id)
                                    }
                                    if (index < item.children.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = Color(0xFFF1F2F6)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Technical Info Card
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Deep Link Navigation Info",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6750A4)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "URI: myapp://navigate/$categoryId/${item.id}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF49454F))
                        )
                    }
                }
            }
        }
    }
}
