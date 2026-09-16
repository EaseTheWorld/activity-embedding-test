package com.example.common.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * CompositionLocal holding the ID of the setting item targeted by deep link navigation.
 */
val LocalAnchorTarget: ProvidableCompositionLocal<String?> = compositionLocalOf { null }

/**
 * Modifier that marks a Composable as an anchorable setting item.
 *
 * When the [itemId] matches [LocalAnchorTarget], this modifier:
 * 1. Automatically requests the parent scroll container to bring this item into view.
 * 2. Triggers a visual highlight / pulse animation to draw attention to the targeted item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.anchor(
    itemId: String,
    highlightColor: Color = Color(0x336750A4)
): Modifier {
    val targetAnchor = LocalAnchorTarget.current
    val isTarget = (targetAnchor == itemId)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var highlighted by remember { mutableStateOf(false) }

    LaunchedEffect(isTarget) {
        if (isTarget) {
            bringIntoViewRequester.bringIntoView()
            highlighted = true
            delay(1500)
            highlighted = false
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = if (highlighted) highlightColor else Color.Transparent,
        animationSpec = tween(durationMillis = 400),
        label = "AnchorHighlightAnimation"
    )

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .clip(RoundedCornerShape(8.dp))
        .background(animatedBg)
}

/**
 * Alias for [anchor].
 */
@Composable
fun Modifier.itemAnchor(itemId: String): Modifier = anchor(itemId)
