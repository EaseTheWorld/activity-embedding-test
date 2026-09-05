package com.example.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.item.ChoiceItem
import com.example.core.item.ToggleItem

/**
 * Standard Toggle Row: Renders a Switch with optional Badge based on item.badgeKey.
 * Pure reactive interaction: triggers item.onValueChanged(nextValue).
 */
@Composable
fun ToggleItemRow(
    item: ToggleItem
) {
    val context = LocalContext.current
    val isChecked by item.valueFlow.collectAsState()

    val titleRes = context.resources.getIdentifier(item.titleKey, "string", context.packageName)
    val title = if (titleRes != 0) context.getString(titleRes) else item.titleKey

    val subRes = item.subtitleKey?.let { context.resources.getIdentifier(it, "string", context.packageName) } ?: 0
    val subtitle = if (subRes != 0) context.getString(subRes) else (item.subtitleKey ?: "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            if (subtitle.isNotEmpty()) {
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
 * Standard Choice Row: Renders Segmented Buttons for ChoiceItem.
 * Pure reactive interaction: triggers item.onValueChanged(option).
 */
@Composable
fun ChoiceItemRow(
    item: ChoiceItem
) {
    val context = LocalContext.current
    val selectedOption by item.valueFlow.collectAsState()

    val titleRes = context.resources.getIdentifier(item.titleKey, "string", context.packageName)
    val title = if (titleRes != 0) context.getString(titleRes) else item.titleKey

    val subRes = item.subtitleKey?.let { context.resources.getIdentifier(it, "string", context.packageName) } ?: 0
    val subtitle = if (subRes != 0) context.getString(subRes) else (item.subtitleKey ?: "")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E1E2E)
            )
        )
        Text(
            text = "Current: $selectedOption",
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Medium
            )
        )
        if (subtitle.isNotEmpty()) {
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
            item.options.forEach { option ->
                val isSelected = (option == selectedOption)
                if (isSelected) {
                    Button(
                        onClick = { /* already selected */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = option, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            item.onValueChanged(option)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = option, fontSize = 13.sp, color = Color(0xFF49454F))
                    }
                }
            }
        }
    }
}
