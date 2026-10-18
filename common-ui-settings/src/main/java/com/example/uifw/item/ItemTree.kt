package com.example.uifw.item

import androidx.compose.runtime.Immutable

@DslMarker
annotation class UiTreeDsl

@Immutable
class ItemTree(val root: UiContainerItem) {
    override fun equals(other: Any?): Boolean = other is ItemTree && root == other.root
    override fun hashCode(): Int = root.hashCode()
    override fun toString(): String = "ItemTree(root=$root)"

    /**
     * Tree 내의 모든 자식 노드를 순회하여 일치하는 key를 가진 UiItem을 검색합니다.
     */
    fun findItem(key: String): UiItem? {
        fun search(item: UiItem): UiItem? {
            if (item.key == key) return item
            if (item is UiContainerItem) {
                for (child in item.children) {
                    val found = search(child)
                    if (found != null) return found
                }
            }
            return null
        }
        return search(root)
    }

    /**
     * Tree 내의 모든 노드를 key -> UiItem 형태의 Map으로 플랫하게 반환합니다.
     */
    fun toKeyMap(): Map<String, UiItem> = buildMap {
        fun collect(item: UiItem) {
            put(item.key, item)
            if (item is UiContainerItem) {
                item.children.forEach(::collect)
            }
        }
        collect(root)
    }
}

@UiTreeDsl
class TreeBuilderScope(val screenRoute: String) {
    private val children = mutableListOf<UiItem>()

    // 1. 외부에 val로 선언된 아이템을 트리에 배치
    operator fun <T : UiItem> T.unaryPlus(): T {
        children.add(this)
        return this
    }

    /**
     * 하위 ItemScreen을 트리 내에 직접 배치 (+subScreen).
     * 자동으로 해당 화면으로 이동하는 UiSubScreenEntryItem을 생성하여 추가합니다.
     */
    operator fun ItemScreen.unaryPlus(): UiSubScreenEntryItem {
        val entryItem = UiSubScreenEntryItem(
            key = "$screenRoute/${this.name}",
            title = this.title,
            targetScreen = this
        )
        +entryItem
        return entryItem
    }

    /**
     * 외부에 val로 선언된 SectionDefinition을 DSL에서 호출하여
     * Section 고유 Key와 Title을 유지하면서 자식 아이템들을 조립.
     */
    operator fun SectionDefinition.invoke(block: TreeBuilderScope.() -> Unit): UiSectionItem {
        val sectionItem = UiSectionItem(
            key = this.key,
            title = this.title,
            children = TreeBuilderScope(screenRoute).apply(block).build()
        )
        +sectionItem
        return sectionItem
    }

    /**
     * 인라인 Section 컨테이너
     */
    fun section(title: String, groupKey: String, block: TreeBuilderScope.() -> Unit) =
        +UiSectionItem(
            key = "$screenRoute/$groupKey",
            title = title,
            children = TreeBuilderScope(screenRoute).apply(block).build()
        )

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
