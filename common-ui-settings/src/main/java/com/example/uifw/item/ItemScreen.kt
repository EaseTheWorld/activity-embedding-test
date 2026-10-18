package com.example.uifw.item

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class ItemScreen(
    val title: String,
    parent: ItemScreen? = null,
    val parentSection: SectionDefinition? = null,
    explicitName: String? = null,
    explicitRoute: String? = null,
    val routeArg: String? = null
) {
    /**
     * parentSection이 지정된 경우 해당 섹션의 소속 화면을 parent로 자동 추론,
     * 명시적 parent가 전달된 경우 우선 사용.
     */
    val parent: ItemScreen? = parent ?: parentSection?.screen

    /**
     * 화면 식별자 이름. explicitName이 제공되면 우선 사용, 없으면 JVM 단순 클래스명 사용.
     */
    val name: String = explicitName ?: this.javaClass.simpleName
        .removeSuffix("$")
        .substringAfterLast('$')

    /**
     * 부모 경로와 결합하여 고유 Route 결정 (Section 레벨은 포함하지 않음):
     * - explicitRoute가 있으면 최우선 사용
     * - parent가 있으면 "${parent.route}/$name"
     * - parent가 없으면 "$name"
     */
    val route: String = explicitRoute ?: if (this.parent != null) "${this.parent.route}/$name" else name

    /**
     * NavGraph 등록용 Route 1 패턴 (화면 자체로 이동하는 용도).
     * 동적 인자(routeArg)가 정의된 경우 "$route/{$routeArg}" 형태를 반환하고,
     * 정적 화면인 경우 route와 동일한 값을 반환합니다.
     */
    val routePattern: String = if (routeArg != null) "$route/{$routeArg}" else route

    /**
     * NavGraph 등록용 Route 2 패턴 (화면 내 특정 아이템으로 앵커링 & 하이라이트하는 용도).
     * 예: "SettingItems/Bluetooth/{itemKey}" 또는 "SettingItems/Bluetooth/DeviceDetail/{deviceId}/{itemKey}"
     */
    val itemRoutePattern: String = "$routePattern/{$ITEM_KEY_ARG}"

    /**
     * 동적 인자(routeArg)가 있는 화면에 대해 실제 런타임 인자 값을 바인딩한 이동 경로를 생성합니다.
     * 예: "SettingItems/Bluetooth/DeviceDetail/Galaxy_Buds3_Pro"
     */
    fun createRoute(argValue: String): String {
        require(routeArg != null) {
            "Screen '$route' does not declare a dynamic routeArg."
        }
        return "$route/$argValue"
    }

    /**
     * 화면 내 특정 아이템으로 앵커링 & 하이라이트하기 위한 이동 경로(Route 2)를 생성합니다.
     * - 정적 화면: "SettingItems/Bluetooth/masterToggle" (기본 item.key와 동일)
     * - 동적 인자 화면: "SettingItems/Bluetooth/DeviceDetail/Galaxy_Buds3_Pro/callAudioToggle"
     */
    fun createItemRoute(itemKey: String, argValue: String? = null): String {
        val relativeItemKey = itemKey.removePrefix("$route/").substringAfterLast('/')
        return if (routeArg != null) {
            val resolvedArg = requireNotNull(argValue) {
                "Screen '$route' requires argValue for routeArg '$routeArg' when creating an item route."
            }
            "${createRoute(resolvedArg)}/$relativeItemKey"
        } else {
            "$route/$relativeItemKey"
        }
    }

    fun createItemRoute(item: UiItem, argValue: String? = null): String =
        createItemRoute(item.key, argValue)

    /**
     * Route 2 ("$routePattern/{itemKey}")의 "{itemKey}" 인자 값으로부터
     * 트리 내 실제 아이템의 전체 DeepLink Key를 복원합니다.
     */
    fun resolveHighlightKey(itemKeyArg: String?): String? {
        if (itemKeyArg.isNullOrBlank()) return null
        val candidateKey = "$route/$itemKeyArg"
        if (findItem(candidateKey) != null) return candidateKey
        // explicitKey 등으로 인해 경로 형태가 다른 경우 말단 키 이름으로 탐색
        val matched = tree.toKeyMap().keys.find { key ->
            key == itemKeyArg || key.substringAfterLast('/') == itemKeyArg
        }
        return matched ?: candidateKey
    }

    companion object {
        const val ITEM_KEY_ARG = "itemKey"
    }

    private val _subScreens = mutableListOf<ItemScreen>()

    /**
     * `by subScreen(...)` 위임으로 등록된 직속 하위 화면 목록.
     */
    val subScreens: List<ItemScreen>
        get() = _subScreens

    @PublishedApi
    internal fun registerSubScreen(subScreen: ItemScreen) {
        if (!_subScreens.contains(subScreen)) {
            _subScreens.add(subScreen)
        }
    }

    /**
     * 현재 화면을 포함하여 하위 모든 SubScreen들을 재귀적으로 수집한 전체 화면 목록.
     * NavGraph 자동 등록 및 전체 트리 검색 인덱싱에 사용됩니다.
     */
    fun allScreens(): List<ItemScreen> = buildList {
        val visited = mutableSetOf<String>()
        fun collect(s: ItemScreen) {
            if (!visited.add(s.route)) return
            add(s)
            s.subScreens.forEach(::collect)
            fun scanContainer(c: UiContainerItem) {
                c.children.forEach { ch ->
                    if (ch is UiSubScreenEntryItem && ch.targetScreen != null) {
                        collect(ch.targetScreen)
                    } else if (ch is UiContainerItem) {
                        scanContainer(ch)
                    }
                }
            }
            scanContainer(s.tree.root)
        }
        collect(this@ItemScreen)
    }

    /**
     * 부모 체인을 따라 루트부터 현재 화면까지의 화면 및 섹션 레벨 Breadcrumb 목록.
     * 부모 화면의 트리(tree) 내에 현재 화면으로 진입하는 Entry가 속한 Section 타이틀을 자동으로 추적하며,
     * 명시적 parentSection이 지정된 경우 해당 섹션 타이틀을 사용합니다.
     * 예: ["환경 설정", "주요 설정", "블루투스 설정", "기본 연결", "블루투스 고급 설정"]
     */
    val screenBreadcrumb: List<String> by lazy {
        val list = mutableListOf<String>()
        fun collect(screen: ItemScreen) {
            val p = screen.parent
            if (p != null) {
                collect(p)
                val sectionTitles = if (screen.parentSection != null) {
                    listOf(screen.parentSection.title)
                } else {
                    findEnclosingSectionTitles(p.tree.root, screen) ?: emptyList()
                }
                list.addAll(sectionTitles)
            } else if (screen.parentSection != null) {
                list.add(screen.parentSection.title)
            }
            list.add(screen.title)
        }
        collect(this)
        list
    }

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
     * 현재 화면 및 모든 하위 SubScreen을 재귀 순회하여 key와 일치하는 UiItem을 검색합니다.
     */
    fun findItemRecursively(key: String): UiItem? {
        return allScreens().findItem(key)
    }

    /**
     * 현재 화면의 모든 노드를 key -> UiItem Map으로 구성합니다.
     */
    fun buildKeyMap(): Map<String, UiItem> = tree.toKeyMap()

    /**
     * 현재 화면 및 모든 하위 SubScreen의 노드를 key -> UiItem Map으로 구성합니다.
     */
    fun buildGlobalKeyMap(): Map<String, UiItem> = allScreens().buildGlobalKeyMap()
}

private fun findEnclosingSectionTitles(
    container: UiContainerItem,
    target: ItemScreen,
    currentSections: List<String> = emptyList()
): List<String>? {
    for (child in container.children) {
        if (child is UiSubScreenEntryItem && child.targetScreen === target) {
            return currentSections
        }
        if (child is UiContainerItem) {
            val nextSections = if (child is UiSectionItem) currentSections + child.title else currentSections
            val found = findEnclosingSectionTitles(child, target, nextSections)
            if (found != null) return found
        }
    }
    return null
}

/**
 * Screen 컬렉션에 대한 전체 검색 및 키 맵 확장 함수
 */
fun Collection<ItemScreen>.findItem(key: String): UiItem? {
    for (screen in this) {
        val item = screen.findItem(key)
        if (item != null) return item
    }
    return null
}

fun Collection<ItemScreen>.buildGlobalKeyMap(): Map<String, UiItem> = buildMap {
    for (screen in this@buildGlobalKeyMap) {
        putAll(screen.tree.toKeyMap())
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
 * Encapsulates the section's unique deep link key, localized title, and owner screen.
 */
class SectionDefinition(
    val key: String,
    val title: String,
    val screen: ItemScreen? = null
)

/**
 * Delegate for declaring a Section as a val property on an ItemScreen.
 * Automatically derives the unique key: "${screen.route}/${property.name}" (or uses explicitKey),
 * and records the enclosing screen reference.
 */
fun ItemScreen.section(
    title: String,
    explicitKey: String? = null
) = PropertyDelegateProvider<ItemScreen, ReadOnlyProperty<ItemScreen, SectionDefinition>> { screen, property ->
    val fullKey = resolveItemKey(screen.route, explicitKey, property.name)
    val def = SectionDefinition(key = fullKey, title = title, screen = screen)
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

/**
 * 하위 ItemScreen(class)을 프로퍼티 위임(by subScreen)으로 선언합니다.
 * 프로퍼티 이름을 하위 화면의 식별자(name)로 전달하며,
 * 최초 1회만 인스턴스를 생성하여 캐싱하고 부모의 subScreens에 자동 등록합니다.
 */
inline fun <S : ItemScreen> ItemScreen.subScreen(
    crossinline creator: (parent: ItemScreen, name: String) -> S
) = subScreen(explicitName = null, creator = creator)

/**
 * explicitName을 지정하여 하위 ItemScreen(class)을 프로퍼티 위임으로 선언합니다.
 */
inline fun <S : ItemScreen> ItemScreen.subScreen(
    explicitName: String?,
    crossinline creator: (parent: ItemScreen, name: String) -> S
) = PropertyDelegateProvider<ItemScreen, ReadOnlyProperty<ItemScreen, S>> { parentScreen, property ->
    val screenName = explicitName ?: property.name
    val instance = creator(parentScreen, screenName)
    parentScreen.registerSubScreen(instance)
    ReadOnlyProperty { _, _ -> instance }
}



