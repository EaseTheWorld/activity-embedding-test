package com.example.uifw.node

import androidx.compose.runtime.Immutable

@Immutable
sealed interface UiNode {
    val key: String
}

@Immutable
sealed interface UiContainerNode : UiNode {
    val children: List<UiNode>
}

enum class Orientation { VERTICAL, HORIZONTAL }

@Immutable
data class UiLinearNode(
    override val key: String,
    val orientation: Orientation,
    override val children: List<UiNode>
) : UiContainerNode

@Immutable
data class UiToggleNode(
    override val key: String,
    val title: String
) : UiNode

@Immutable
data class UiTextNode(
    override val key: String,
    val text: String
) : UiNode

@Immutable
data class UiSubScreenEntryNode(
    override val key: String,
    val title: String,
    val targetScreen: NodeScreen
) : UiNode
