package com.example.common.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.core.item.ItemViewModelRegistry

/**
 * CompositionLocal providing access to the application's [ItemViewModelRegistry].
 *
 * Guarantees that regardless of screen context (Full Category screen, Recent screen,
 * Dashboard Quick Controls, or custom domain layouts), any Composable can resolve the
 * shared single-source-of-truth [com.example.core.item.ItemViewModel] by its item ID.
 */
val LocalItemViewModelRegistry = staticCompositionLocalOf { ItemViewModelRegistry() }