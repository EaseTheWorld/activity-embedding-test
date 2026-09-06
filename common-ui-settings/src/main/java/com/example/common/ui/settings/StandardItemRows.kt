package com.example.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/**
 * Standard Toggle Row: Renders a Switch with compile-time @StringRes title,
 * optional subtitle, optional leading icon, and optional Badge based on item.badgeKey.
 */
@Composable
fun ToggleItemRow(
    item: UiToggleItem
) {
    val isChecked by item.valueFlow.collectAsState()
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val iconRes = item.getValueIconRes(isChecked) ?: item.iconRes

    Row(
        modifier = Modifier
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
            onCheckedChange = { nextValue ->
                item.onValueChanged(nextValue)
            }
        )
    }
}

/**
 * Standard Choice Row: Renders Segmented Buttons with compile-time @StringRes title,
 * optional subtitle, and localized option labels/icons.
 */
@Composable
fun ChoiceItemRow(
    item: UiChoiceItem
) {
    val selectedOption by item.valueFlow.collectAsState()
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val iconRes = item.iconRes
    val currentLabel = item.getValueTextRes(selectedOption)?.let { stringResource(it) } ?: selectedOption

    Column(
        modifier = Modifier
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
                        { item.onValueChanged(option.value) }
                    )
                }
            }
        }
    }
}

/**
 * Standard Slider Row: Renders a Slider for UiSliderItem.
 */
@Composable
fun SliderItemRow(
    item: UiSliderItem
) {
    val value by item.valueFlow.collectAsState()
    val title = stringResource(item.titleRes)
    val subtitle = item.subtitleRes?.let { stringResource(it) }
    val unit = item.unitRes?.let { stringResource(it) } ?: ""

    Column(
        modifier = Modifier
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
            onValueChange = { item.onValueChanged(it.toInt()) },
            valueRange = item.min.toFloat()..item.max.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
