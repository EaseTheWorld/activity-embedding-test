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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import com.example.core.item.MutableItemViewModel
import com.example.core.item.ValueWithState
import kotlinx.coroutines.flow.StateFlow

/**
 * Standard Toggle Row: Renders a Switch with compile-time @StringRes title,
 * optional subtitle, optional leading icon, and optional Badge based on item.badgeKey.
 *
 * Adheres to ISP: enables Switch only if [viewModel] implements [MutableItemViewModel].
 */
@Composable
fun ToggleItemRow(
    item: UiToggleItem,
    viewModel: ItemViewModel<Boolean>,
    modifier: Modifier = Modifier
) {
    val isChecked by viewModel.valueFlow.collectAsState()
    val isMutable = viewModel is MutableItemViewModel
    ToggleItemRowContent(
        item = item,
        isChecked = isChecked,
        onCheckedChange = { (viewModel as? MutableItemViewModel)?.setValue(it) },
        enabled = isMutable,
        modifier = modifier
    )
}

@Composable
fun ToggleItemRow(
    item: UiToggleItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<Boolean>(item.id)
    if (viewModel != null) {
        ToggleItemRow(item = item, viewModel = viewModel, modifier = modifier)
    } else {
        ToggleItemRowContent(item = item, isChecked = false, onCheckedChange = {}, enabled = false, modifier = modifier)
    }
}

@Composable
private fun ToggleItemRowContent(
    item: UiToggleItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val iconRes = item.getValueIconRes(isChecked) ?: item.iconRes

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 10.dp),
                tint = Color(0xFF6750A4)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1E2E)
                    )
                )
                if (item.badgeKey != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    ) {
                        Text(text = item.badgeKey!!, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (subtitle != null && subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF757575)
                    )
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * Compact Grid Card for Quick Controls and Recent Category screens.
 */
@Composable
fun ToggleGridCard(
    item: UiToggleItem,
    viewModel: ItemViewModel<Boolean>,
    modifier: Modifier = Modifier
) {
    val isChecked by viewModel.valueFlow.collectAsState()
    val isMutable = viewModel is MutableItemViewModel
    val title = stringResource(item.titleRes)
    val iconRes = item.getValueIconRes(isChecked) ?: item.iconRes

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) Color(0xFFEDE7F6) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isChecked) Color(0xFF6750A4) else Color(0xFF757575)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }
                Switch(
                    checked = isChecked,
                    onCheckedChange = { (viewModel as? MutableItemViewModel)?.setValue(it) },
                    enabled = isMutable
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1E2E)
                ),
                maxLines = 2
            )
        }
    }
}

@Composable
fun ToggleGridCard(
    item: UiToggleItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<Boolean>(item.id)
    if (viewModel != null) {
        ToggleGridCard(item = item, viewModel = viewModel, modifier = modifier)
    } else {
        val title = stringResource(item.titleRes)
        val iconRes = item.iconRes

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF757575)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        enabled = false
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1E2E)
                    ),
                    maxLines = 2
                )
            }
        }
    }
}


/**
 * Standard Choice Row: Renders Segmented Buttons with compile-time @StringRes title,
 * optional subtitle, and localized option labels/icons.
 */
@Composable
fun ChoiceItemRow(
    item: UiChoiceItem,
    viewModel: ItemViewModel<String>,
    modifier: Modifier = Modifier
) {
    val optionStatesFlow = viewModel.valueWithStateFlow
    val isMutable = viewModel is MutableItemViewModel<*>
    if (optionStatesFlow != null) {
        val optionStates by optionStatesFlow.collectAsState()
        val effectiveStates = if (isMutable) optionStates else optionStates.map { it.copy(isEnabled = false) }
        ChoiceItemRowWithStates(
            item = item,
            optionStates = effectiveStates,
            onOptionSelected = { (viewModel as? MutableItemViewModel<String>)?.setValue(it) },
            modifier = modifier
        )
    } else {
        val selectedOption by viewModel.valueFlow.collectAsState()
        ChoiceItemRowContent(
            item = item,
            selectedOption = selectedOption,
            onOptionSelected = { (viewModel as? MutableItemViewModel<String>)?.setValue(it) },
            enabled = isMutable,
            modifier = modifier
        )
    }
}

@Composable
fun ChoiceItemRow(
    item: UiChoiceItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<String>(item.id)
    if (viewModel != null) {
        ChoiceItemRow(item = item, viewModel = viewModel, modifier = modifier)
    } else {
        ChoiceItemRowContent(item = item, selectedOption = "", onOptionSelected = {}, enabled = false, modifier = modifier)
    }
}

@Composable
private fun ChoiceItemRowContent(
    item: UiChoiceItem,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val iconRes = item.iconRes
    val currentLabel = if (selectedOption.isNotEmpty()) {
        item.getValueTextRes(selectedOption)?.let { stringResource(it) } ?: selectedOption
    } else {
        "-"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 10.dp),
                    tint = if (enabled) Color(0xFF6750A4) else Color(0xFF757575)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1E2E)
                    )
                )
                Text(
                    text = "Current: $currentLabel",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (enabled) Color(0xFF6750A4) else Color(0xFF757575),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        if (subtitle != null && subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF757575)
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item.choiceOptions.forEach { option ->
                val isSelected = (option.value == selectedOption)
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                    item.optionSlot(
                        option,
                        isSelected,
                        { if (enabled) onOptionSelected(option.value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceItemRowWithStates(
    item: UiChoiceItem,
    optionStates: List<ValueWithState<String>>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedOption = optionStates.firstOrNull { it.isSelected }?.id ?: ""
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val iconRes = item.iconRes
    val currentLabel = if (selectedOption.isNotEmpty()) {
        item.getValueTextRes(selectedOption)?.let { stringResource(it) } ?: selectedOption
    } else {
        "-"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 10.dp),
                    tint = Color(0xFF6750A4)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1E2E)
                    )
                )
                Text(
                    text = "Current: $currentLabel",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        if (subtitle != null && subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF757575)
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // Options rendered dynamically from optionStates (ID-mapped to static UiOption)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            optionStates.forEach { state ->
                val option = item.getOption(state.id)
                if (option != null) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        item.optionSlot(
                            option,
                            state.isSelected,
                            { if (state.isEnabled) onOptionSelected(state.id) }
                        )
                    }
                }
            }
        }
    }
}


/**
 * Compact Choice Grid Card for Recent Category screens.
 */
@Composable
fun ChoiceGridCard(
    item: UiChoiceItem,
    viewModel: ItemViewModel<String>,
    modifier: Modifier = Modifier
) {
    val selectedOption by viewModel.valueFlow.collectAsState()
    ChoiceGridCardContent(item = item, selectedOption = selectedOption, modifier = modifier)
}

@Composable
fun ChoiceGridCard(
    item: UiChoiceItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<String>(item.id)
    if (viewModel != null) {
        ChoiceGridCard(item = item, viewModel = viewModel, modifier = modifier)
    } else {
        ChoiceGridCardContent(item = item, selectedOption = "-", modifier = modifier)
    }
}

@Composable
private fun ChoiceGridCardContent(
    item: UiChoiceItem,
    selectedOption: String,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val currentLabel = if (selectedOption.isNotEmpty() && selectedOption != "-") {
        item.getValueTextRes(selectedOption)?.let { stringResource(it) } ?: selectedOption
    } else {
        "-"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconRes = item.iconRes
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF6750A4)
                    )
                }
                Badge(containerColor = Color(0xFFEDE7F6), contentColor = Color(0xFF6750A4)) {
                    Text(text = currentLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1E2E)
                ),
                maxLines = 2
            )
        }
    }
}


/**
 * Standard Slider Row: Renders a Slider for UiSliderItem.
 */
@Composable
fun SliderItemRow(
    item: UiSliderItem,
    viewModel: ItemViewModel<Int>,
    modifier: Modifier = Modifier
) {
    val value by viewModel.valueFlow.collectAsState()
    val isMutable = viewModel is MutableItemViewModel
    SliderItemRowContent(
        item = item,
        value = value,
        onValueChange = { (viewModel as? MutableItemViewModel)?.setValue(it) },
        enabled = isMutable,
        modifier = modifier
    )
}

@Composable
fun SliderItemRow(
    item: UiSliderItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<Int>(item.id)
    if (viewModel != null) {
        SliderItemRow(item = item, viewModel = viewModel, modifier = modifier)
    } else {
        SliderItemRowContent(item = item, value = item.min, onValueChange = {}, enabled = false, modifier = modifier)
    }
}

@Composable
private fun SliderItemRowContent(
    item: UiSliderItem,
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val unit = item.unitRes?.let { stringResource(it) } ?: ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1E2E)
                    )
                )
                if (subtitle != null && subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF757575)
                        )
                    )
                }
            }
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            enabled = enabled,
            valueRange = item.min.toFloat()..item.max.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Compact Slider Grid Card for Recent Category screens.
 */
@Composable
fun SliderGridCard(
    item: UiSliderItem,
    viewModel: ItemViewModel<Int>,
    modifier: Modifier = Modifier
) {
    val value by viewModel.valueFlow.collectAsState()
    val isMutable = viewModel is MutableItemViewModel
    val title = stringResource(item.titleRes)
    val unit = item.unitRes?.let { stringResource(it) } ?: ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$value$unit",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                )
            }
            androidx.compose.material3.Slider(
                value = value.toFloat(),
                onValueChange = { (viewModel as? MutableItemViewModel)?.setValue(it.toInt()) },
                valueRange = item.min.toFloat()..item.max.toFloat(),
                enabled = isMutable,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SliderGridCard(
    item: UiSliderItem,
    viewModelRegistry: ItemViewModelRegistry = LocalItemViewModelRegistry.current,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModelRegistry.getViewModel<Int>(item.id)
    if (viewModel != null) {
        SliderGridCard(item = item, viewModel = viewModel, modifier = modifier)
    } else {
        val title = stringResource(item.titleRes)
        val unit = item.unitRes?.let { stringResource(it) } ?: ""

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "-$unit",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4)
                        )
                    )
                }
                androidx.compose.material3.Slider(
                    value = item.min.toFloat(),
                    onValueChange = {},
                    enabled = false,
                    valueRange = item.min.toFloat()..item.max.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

