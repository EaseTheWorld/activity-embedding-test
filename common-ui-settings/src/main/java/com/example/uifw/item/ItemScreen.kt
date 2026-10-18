package com.example.uifw.item

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class ItemScreen(
    val title: String,
    explicitRoute: String? = null
) {
    /**
     * kotlin-reflect 없이 순수 JVM 클래스명을 사용하여 계층형 Route 자동 생성.
     * explicitRoute가 전달된 경우 해당 route를 우선 사용.
     * 예: "com.example.SettingItems$Bluetooth$Advanced" -> "SettingItems/Bluetooth/Advanced"
     */
    val route: String = explicitRoute ?: this.javaClass.name
        .substringAfterLast('.')
        .replace('$', '/')

    abstract val tree: ItemTree

    protected fun buildTree(block: TreeBuilderScope.() -> Unit): ItemTree {
        val rootChildren = TreeBuilderScope(route).apply(block).build()
        return ItemTree(
            root = UiLinearItem(
                key = "$route/root",
                orientation = Orientation.VERTICAL,
                children = rootChildren
            )
        )
    }

    /**
     * 현재 화면의 트리에서 key와 일치하는 UiItem을 검색합니다.
     */
    fun findItem(key: String): UiItem? = tree.findItem(key)

    /**
     * 현재 화면 및 연결된 모든 하위 서브스크린(UiSubScreenEntryItem)을 재귀 순회하여 key와 일치하는 UiItem을 검색합니다.
     */
    fun findItemRecursively(key: String): UiItem? {
        val visited = mutableSetOf<String>()
        fun searchScreen(screen: ItemScreen): UiItem? {
            if (!visited.add(screen.route)) return null
            val direct = screen.tree.findItem(key)
            if (direct != null) return direct

            fun searchContainer(container: UiContainerItem): UiItem? {
                for (child in container.children) {
                    if (child is UiSubScreenEntryItem) {
                        val found = searchScreen(child.targetScreen)
                        if (found != null) return found
                    } else if (child is UiContainerItem) {
                        val found = searchContainer(child)
                        if (found != null) return found
                    }
                }
                return null
            }
            return searchContainer(screen.tree.root)
        }
        return searchScreen(this)
    }

    /**
     * 현재 화면 및 모든 하위 서브스크린을 순회하여 전체 노드를 key -> UiItem Map으로 구성합니다.
     */
    fun buildGlobalKeyMap(): Map<String, UiItem> = buildMap {
        val visited = mutableSetOf<String>()
        fun collectScreen(screen: ItemScreen) {
            if (!visited.add(screen.route)) return
            putAll(screen.tree.toKeyMap())
            fun collectContainer(container: UiContainerItem) {
                for (child in container.children) {
                    if (child is UiSubScreenEntryItem) {
                        collectScreen(child.targetScreen)
                    } else if (child is UiContainerItem) {
                        collectContainer(child)
                    }
                }
            }
            collectContainer(screen.tree.root)
        }
        collectScreen(this@ItemScreen)
    }
}

/**
 * 아이템이나 섹션의 Canonical DeepLink Key를 결정합니다.
 * 1. explicitKey가 지정되지 않은 경우(null): "${screenRoute}/${propertyName}"
 * 2. explicitKey에 '/'가 포함되거나 '/'로 시작하는 경우: 전체(절대) 딥링크 경로로 간주
 * 3. explicitKey가 단순 문자열인 경우: 화면 경로 하위의 상대 키 "${screenRoute}/$explicitKey"로 결합
 */
fun resolveItemKey(screenRoute: String, explicitKey: String?, propertyName: String): String {
    if (explicitKey == null) return "$screenRoute/$propertyName"
    return if (explicitKey.startsWith("/")) {
        explicitKey.removePrefix("/")
    } else if (explicitKey.contains('/')) {
        explicitKey
    } else {
        "$screenRoute/$explicitKey"
    }
}

/**
 * ItemScreen 내부 프로퍼티 전용 Delegate.
 * explicitKey가 제공되면 해당 키를 사용하고, 생략 시 "${screen.route}/${property.name}"으로 고유 Key(DeepLink)를 생성.
 */
inline fun <T : UiItem> screenItem(
    explicitKey: String? = null,
    crossinline creator: (fullKey: String) -> T
) = PropertyDelegateProvider<ItemScreen, ReadOnlyProperty<ItemScreen, T>> { screen, property ->
    val fullKey = resolveItemKey(screen.route, explicitKey, property.name)
    val item = creator(fullKey) // 최초 1회 생성 후 캐싱
    ReadOnlyProperty { _, _ -> item }
}

/**
 * Definition of a Section declared via val on ItemScreen.
 * Encapsulates the section's unique deep link key and localized title.
 */
class SectionDefinition(
    val key: String,
    val title: String
)

/**
 * Delegate for declaring a Section as a val property on an ItemScreen.
 * Automatically derives the unique key: "${screen.route}/${property.name}" (or uses explicitKey).
 */
fun ItemScreen.section(
    title: String,
    explicitKey: String? = null
) = PropertyDelegateProvider<ItemScreen, ReadOnlyProperty<ItemScreen, SectionDefinition>> { screen, property ->
    val fullKey = resolveItemKey(screen.route, explicitKey, property.name)
    val def = SectionDefinition(key = fullKey, title = title)
    ReadOnlyProperty { _, _ -> def }
}

fun ItemScreen.toggle(
    title: String,
    explicitKey: String? = null
) = screenItem(explicitKey) { key ->
    UiToggleItem(key = key, title = title)
}

fun ItemScreen.text(
    initialText: String,
    explicitKey: String? = null
) = screenItem(explicitKey) { key ->
    UiTextItem(key = key, text = initialText)
}

fun ItemScreen.subScreenEntry(
    target: ItemScreen,
    title: String = target.title,
    explicitKey: String? = null
) = screenItem(explicitKey) { key ->
    UiSubScreenEntryItem(key = key, title = title, targetScreen = target)
}
