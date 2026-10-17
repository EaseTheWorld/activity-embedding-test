package com.example.uifw.item

import androidx.compose.runtime.Immutable

@Immutable
interface UiItem {
    val key: String
}

@Immutable
interface UiCustomItem : UiItem {
    val title: String? get() = null
}

@Immutable
sealed interface UiContainerItem : UiItem {
    val children: List<UiItem>
}

enum class Orientation { VERTICAL, HORIZONTAL }

@Immutable
data class UiLinearItem(
    override val key: String,
    val orientation: Orientation,
    override val children: List<UiItem>
) : UiContainerItem

@Immutable
data class UiSectionItem(
    override val key: String,
    val title: String,
    override val children: List<UiItem>
) : UiContainerItem

@Immutable
data class UiToggleItem(
    override val key: String,
    val title: String
) : UiItem

@Immutable
data class UiTextItem(
    override val key: String,
    val text: String
) : UiItem

@Immutable
data class UiSubScreenEntryItem(
    override val key: String,
    val title: String,
    val targetScreen: ItemScreen
) : UiItem
