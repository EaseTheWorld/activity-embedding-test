package com.example.uifw.item

import androidx.compose.runtime.Immutable

@DslMarker
annotation class UiTreeDsl

@Immutable
class ItemTree(val root: UiContainerItem) {
    override fun equals(other: Any?): Boolean = other is ItemTree && root == other.root
    override fun hashCode(): Int = root.hashCode()
    override fun toString(): String = "ItemTree(root=$root)"
}

@UiTreeDsl
class TreeBuilderScope(private val screenRoute: String) {
    private val children = mutableListOf<UiItem>()

    // 1. 외부에 val로 선언된 아이템을 트리에 배치
    operator fun <T : UiItem> T.unaryPlus(): T {
        children.add(this)
        return this
    }

    // 2. 레이아웃 컨테이너 (중간 컨테이너의 key는 Recomposition 식별용으로만 사용)
    fun column(groupKey: String, block: TreeBuilderScope.() -> Unit) =
        +UiLinearItem(
            key = "$screenRoute/$groupKey",
            orientation = Orientation.VERTICAL,
            children = TreeBuilderScope(screenRoute).apply(block).build()
        )

    fun row(groupKey: String, block: TreeBuilderScope.() -> Unit) =
        +UiLinearItem(
            key = "$screenRoute/$groupKey",
            orientation = Orientation.HORIZONTAL,
            children = TreeBuilderScope(screenRoute).apply(block).build()
        )

    // 3. ItemController 참조가 필요 없는 정적 인라인 아이템
    fun staticText(key: String, text: String) =
        +UiTextItem(key = "$screenRoute/$key", text = text)

    internal fun build(): List<UiItem> = children.toList()
}
