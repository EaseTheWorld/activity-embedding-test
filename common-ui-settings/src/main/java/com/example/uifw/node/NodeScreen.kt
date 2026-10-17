package com.example.uifw.node

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class NodeScreen(val title: String) {
    /**
     * kotlin-reflect 없이 순수 JVM 클래스명을 사용하여 계층형 Route 자동 생성
     * 예: "com.example.SettingNodes$Bluetooth$Advanced" -> "SettingNodes/Bluetooth/Advanced"
     */
    val route: String = this.javaClass.name
        .substringAfterLast('.')
        .replace('$', '/')

    abstract val tree: NodeTree

    protected fun buildTree(block: TreeBuilderScope.() -> Unit): NodeTree {
        val rootChildren = TreeBuilderScope(route).apply(block).build()
        return NodeTree(
            root = UiLinearNode(
                key = "$route/root",
                orientation = Orientation.VERTICAL,
                children = rootChildren
            )
        )
    }
}

/**
 * NodeScreen 내부 프로퍼티 전용 Delegate.
 * 중간 Column/Row와 무관하게 "${screen.route}/${property.name}"으로 고유 Key(DeepLink)를 생성.
 */
inline fun <T : UiNode> screenNode(
    crossinline creator: (fullKey: String) -> T
) = PropertyDelegateProvider<NodeScreen, ReadOnlyProperty<NodeScreen, T>> { screen, property ->
    val fullKey = "${screen.route}/${property.name}"
    val node = creator(fullKey) // 최초 1회 생성 후 캐싱
    ReadOnlyProperty { _, _ -> node }
}

fun NodeScreen.toggle(title: String) = screenNode { key ->
    UiToggleNode(key = key, title = title)
}

fun NodeScreen.text(initialText: String) = screenNode { key ->
    UiTextNode(key = key, text = initialText)
}

fun NodeScreen.subScreenEntry(
    target: NodeScreen,
    title: String = target.title
) = screenNode { key ->
    UiSubScreenEntryNode(key = key, title = title, targetScreen = target)
}
