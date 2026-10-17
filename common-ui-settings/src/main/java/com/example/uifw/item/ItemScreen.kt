package com.example.uifw.item

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class ItemScreen(val title: String) {
    /**
     * kotlin-reflect 없이 순수 JVM 클래스명을 사용하여 계층형 Route 자동 생성
     * 예: "com.example.SettingItems$Bluetooth$Advanced" -> "SettingItems/Bluetooth/Advanced"
     */
    val route: String = this.javaClass.name
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
}

/**
 * ItemScreen 내부 프로퍼티 전용 Delegate.
 * 중간 Container와 무관하게 "${screen.route}/${property.name}"으로 고유 Key(DeepLink)를 생성.
 */
inline fun <T : UiItem> screenItem(
    crossinline creator: (fullKey: String) -> T
) = PropertyDelegateProvider<ItemScreen, ReadOnlyProperty<ItemScreen, T>> { screen, property ->
    val fullKey = "${screen.route}/${property.name}"
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
 * Automatically derives the unique key: "${screen.route}/${property.name}".
 */
fun ItemScreen.section(title: String) =
    PropertyDelegateProvider<ItemScreen, ReadOnlyProperty<ItemScreen, SectionDefinition>> { screen, property ->
        val fullKey = "${screen.route}/${property.name}"
        val def = SectionDefinition(key = fullKey, title = title)
        ReadOnlyProperty { _, _ -> def }
    }

fun ItemScreen.toggle(title: String) = screenItem { key ->
    UiToggleItem(key = key, title = title)
}

fun ItemScreen.text(initialText: String) = screenItem { key ->
    UiTextItem(key = key, text = initialText)
}

fun ItemScreen.subScreenEntry(
    target: ItemScreen,
    title: String = target.title
) = screenItem { key ->
    UiSubScreenEntryItem(key = key, title = title, targetScreen = target)
}
