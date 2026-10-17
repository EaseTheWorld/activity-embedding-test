package com.example.uifw.node

import androidx.compose.runtime.Immutable

@DslMarker
annotation class UiTreeDsl

@Immutable
class NodeTree(val root: UiContainerNode) {
    override fun equals(other: Any?): Boolean = other is NodeTree && root == other.root
    override fun hashCode(): Int = root.hashCode()
    override fun toString(): String = "NodeTree(root=$root)"
}

@UiTreeDsl
class TreeBuilderScope(private val screenRoute: String) {
    private val children = mutableListOf<UiNode>()

    // 1. 외부에 val로 선언된 노드를 트리에 배치
    operator fun <T : UiNode> T.unaryPlus(): T {
        children.add(this)
        return this
    }

    // 2. 레이아웃 컨테이너 (중간 컨테이너의 key는 Recomposition 식별용으로만 사용)
    fun column(groupKey: String, block: TreeBuilderScope.() -> Unit) =
        +UiLinearNode(
            key = "$screenRoute/$groupKey",
            orientation = Orientation.VERTICAL,
            children = TreeBuilderScope(screenRoute).apply(block).build()
        )

    fun row(groupKey: String, block: TreeBuilderScope.() -> Unit) =
        +UiLinearNode(
            key = "$screenRoute/$groupKey",
            orientation = Orientation.HORIZONTAL,
            children = TreeBuilderScope(screenRoute).apply(block).build()
        )

    // 3. ViewModel 참조가 필요 없는 정적 인라인 노드
    fun staticText(key: String, text: String) =
        +UiTextNode(key = "$screenRoute/$key", text = text)

    internal fun build(): List<UiNode> = children.toList()
}
