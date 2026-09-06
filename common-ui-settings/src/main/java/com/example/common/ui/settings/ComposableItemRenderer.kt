package com.example.common.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Polymorphic Composable renderer interface.
 * All setting items (standard or bespoke custom) implement this interface so that the
 * common settings UI screen can render them without needing any 'when' branching or
 * knowledge of feature module classes.
 */
interface ComposableItemRenderer {
    @Composable
    fun Draw(modifier: Modifier)
}

/**
 * Convenience extension providing default [Modifier] for callers desiring a parameterless invocation.
 */
@Composable
fun ComposableItemRenderer.Draw() {
    Draw(Modifier)
}
