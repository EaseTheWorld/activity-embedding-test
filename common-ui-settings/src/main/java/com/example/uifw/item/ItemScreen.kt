package com.example.uifw.item

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class ItemScreen(
    val title: String,
    val parent: ItemScreen? = null,
    explicitName: String? = null,
    explicitRoute: String? = null
) {
    /**
     * 화면 식별자 이름. explicitName이 제공되면 우선 사용, 없으면 JVM 단순 클래스명 사용.
     */
    val name: String = explicitName ?: this.javaClass.simpleName
        .removeSuffix("$")
        .substringAfterLast('$')

    /**
     * 부모 경로와 결합하여 고유 Route 결정:
     * - explicitRoute가 있으면 최우선 사용
     * - parent가 있으면 "${parent.route}/$name"
     * - parent가 없으면 "$name"
     */
    val route: String = explicitRoute ?: if (parent != null) "${parent.route}/$name" else name

    /**
     * 부모 체인을 따라 루트부터 현재 화면까지의 화면 레벨 Breadcrumb 목록
     * 예: ["환경 설정", "블루투스 설정", "블루투스 고급 설정"]
     */
    val screenBreadcrumb: List<String> by lazy {
        buildList {
            var cur: ItemScreen? = this@ItemScreen
            while (cur != null) {
                add(0, cur.title)
                cur = cur.parent
            }
        }
    }

    /**
     * 인덱싱 및 전체 키 맵 생성을 위한 하위 스크린 목록.
     * 클래스 초기화 순환을 방지하기 위해 'by lazy'로 오버라이드할 수 있습니다.
     */
    open val subScreens: List<ItemScreen> get() = emptyList()

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
     * 현재 화면 및 subScreens를 재귀 순회하여 key와 일치하는 UiItem을 검색합니다.
     */
    fun findItemRecursively(key: String): UiItem? {
        val direct = tree.findItem(key)
        if (direct != null) return direct

        for (sub in subScreens) {
            val found = sub.findItemRecursively(key)
            if (found != null) return found
        }
        return null
    }

    /**
     * 현재 화면 및 모든 하위 subScreens를 순회하여 전체 노드를 key -> UiItem Map으로 구성합니다.
     */
    fun buildGlobalKeyMap(): Map<String, UiItem> = buildMap {
        putAll(tree.toKeyMap())
        for (sub in subScreens) {
            putAll(sub.buildGlobalKeyMap())
        }
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

fun ItemScreen.entry(
    title: String,
    target: ItemScreen? = null,
    explicitKey: String? = null
) = screenItem(explicitKey) { key ->
    UiSubScreenEntryItem(key = key, title = title, targetScreen = target)
}

fun ItemScreen.subScreenEntry(
    target: ItemScreen? = null,
    title: String = target?.title ?: "",
    explicitKey: String? = null
) = screenItem(explicitKey) { key ->
    UiSubScreenEntryItem(key = key, title = title, targetScreen = target)
}
