package com.example.uifw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.example.uifw.item.UiItem
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Common Item(Toggle, Text, Entry, Section, Linear)과 Custom Item 모두 동일하게 구현하는
 * 타입(KClass) 기반 UI 렌더러 추상 클래스.
 */
abstract class ItemRenderer<T : UiItem>(
    val itemClass: KClass<T>
) {
    /**
     * 자식 노드가 없는 Leaf Item(Toggle, Text, Entry, CustomItem 등)을 렌더링합니다.
     */
    @Composable
    open fun Render(
        item: T,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
    }

    /**
     * 컨테이너 아이템(Section, Linear)과 Leaf Item을 모두 아우르는 통합 렌더링 진입점.
     * Leaf Item은 기본 구현을 통해 3-파라미터 Render로 자동 위임됩니다.
     */
    @Composable
    open fun Render(
        item: T,
        isHighlighted: Boolean,
        modifier: Modifier,
        renderChild: @Composable (UiItem) -> Unit
    ) {
        Render(item = item, isHighlighted = isHighlighted, modifier = modifier)
    }
}

/**
 * 프레임워크 기본 Common Item 렌더러들과 모듈별 Custom Item 렌더러(Hilt @IntoSet)를
 * 통합 관리하는 레지스트리.
 */
class ItemRendererRegistry @Inject constructor(
    customRenderers: Set<@JvmSuppressWildcards ItemRenderer<*>> = emptySet()
) {
    private val rendererMap: Map<KClass<out UiItem>, ItemRenderer<*>> = buildMap {
        // 1. 프레임워크 기본 Common Item Renderers 등록
        for (defaultRenderer in DefaultItemRenderers.all) {
            put(defaultRenderer.itemClass, defaultRenderer)
        }
        // 2. 외부에서 주입된 Custom Item Renderers 등록 (중복 등록 검증 포함)
        val seenCustomClasses = mutableSetOf<KClass<out UiItem>>()
        for (renderer in customRenderers) {
            val clazz = renderer.itemClass
            require(seenCustomClasses.add(clazz)) {
                "Duplicate ItemRenderer registered for item class '${clazz.qualifiedName}'"
            }
            put(clazz, renderer)
        }
    }

    fun hasRenderer(itemClass: KClass<out UiItem>): Boolean =
        rendererMap.containsKey(itemClass)

    @Suppress("UNCHECKED_CAST")
    fun <T : UiItem> getOrNull(item: T): ItemRenderer<T>? =
        rendererMap[item::class] as? ItemRenderer<T>

    @Suppress("UNCHECKED_CAST")
    fun <T : UiItem> get(item: T): ItemRenderer<T> =
        (rendererMap[item::class]
            ?: error("No ItemRenderer registered for item class='${item::class.qualifiedName}' (key='${item.key}')")) as ItemRenderer<T>
}

val LocalItemRendererRegistry = staticCompositionLocalOf {
    ItemRendererRegistry()
}
