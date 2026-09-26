package com.example.common.ui.settings

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val TAG = "ItemAnchor"

/**
 * Event describing a deep link target item and whether a value mutation was executed.
 */
data class HighlightEvent(
    val itemId: String,
    val hasValueMutation: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * CompositionLocal holding the ID of the setting item targeted by deep link navigation.
 */
val LocalAnchorTarget: ProvidableCompositionLocal<String?> = compositionLocalOf { null }

/**
 * CompositionLocal holding the active [HighlightEvent] triggered by navigation or intent mutation.
 */
val LocalHighlightEvent: ProvidableCompositionLocal<HighlightEvent?> = compositionLocalOf { null }

/**
 * Modifier that marks a Composable as an anchorable setting item row.
 *
 * When the [itemId] matches [LocalAnchorTarget] or [LocalHighlightEvent]:
 * 1. Automatically requests the parent scroll container to bring this item into view.
 * 2. Triggers a visual highlight / pulse animation for 1 second (1000ms) to draw attention.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.anchor(
    itemId: String,
    highlightColor: Color = Color(0x406750A4)
): Modifier {
    val highlightEvent = LocalHighlightEvent.current
    val targetAnchor = LocalAnchorTarget.current
    val isTarget = (highlightEvent?.itemId == itemId) || (targetAnchor == itemId)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var highlighted by remember { mutableStateOf(false) }

    LaunchedEffect(highlightEvent?.timestamp, isTarget) {
        if (isTarget) {
            Log.d(TAG, "Item row highlight started for '$itemId', hasValueMutation=${highlightEvent?.hasValueMutation}")
            bringIntoViewRequester.bringIntoView()
            highlighted = true
            delay(1000)
            highlighted = false
            Log.d(TAG, "Item row highlight completed for '$itemId'")
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = if (highlighted) highlightColor else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "AnchorHighlightAnimation"
    )

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .clip(RoundedCornerShape(8.dp))
        .background(animatedBg)
}

/**
 * Modifier that triggers a 1-second pulse/glow highlight specifically on the value control
 * (e.g. Switch, selected Choice button, Slider value) when a value mutation was executed via deep link.
 */
@Composable
fun Modifier.valueHighlight(
    itemId: String,
    isSelectedOption: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    highlightColor: Color = Color(0xFF6750A4)
): Modifier {
    val highlightEvent = LocalHighlightEvent.current
    val shouldHighlight = highlightEvent != null &&
            highlightEvent.itemId == itemId &&
            highlightEvent.hasValueMutation &&
            isSelectedOption

    var active by remember { mutableStateOf(false) }

    LaunchedEffect(highlightEvent?.timestamp, shouldHighlight) {
        if (shouldHighlight) {
            Log.d(TAG, "Value control highlight started for '$itemId' (selected=$isSelectedOption)")
            active = true
            delay(1000)
            active = false
            Log.d(TAG, "Value control highlight completed for '$itemId'")
        }
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "ValueHighlightGlowAnimation"
    )

    return this
        .border(
            width = (2.5 * glowAlpha).dp,
            color = highlightColor.copy(alpha = glowAlpha),
            shape = shape
        )
        .background(
            color = highlightColor.copy(alpha = glowAlpha * 0.25f),
            shape = shape
        )
}

/**
 * Alias for [anchor].
 */
@Composable
fun Modifier.itemAnchor(itemId: String): Modifier = anchor(itemId)
