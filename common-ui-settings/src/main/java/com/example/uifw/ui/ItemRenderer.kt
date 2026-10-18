package com.example.uifw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.example.uifw.controller.ItemUiState
import com.example.uifw.item.UiItem
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * 정적 UiItem 대신 동적 ItemUiState(S) 타입 기준으로 Aggregation되는 UI 렌더러 추상 클래스.
 *
 * - state: Controller가 emit한 순수 불변 데이터 스냅샷 (value, isVisible, isEnabled 포함)
 * - onValueChanged: 사용자 이벤트가 발생했을 때 호출할 콜백 (`controller::onValueChanged` 레퍼런스가 전달됨)
 */
abstract class ItemRenderer<S : ItemUiState, V>(
    val stateClass: KClass<S>
) {
    /**
     * 자식 노드가 없는 Leaf Item(ToggleUiState, TextUiState, EntryUiState, CustomUiState 등)을 렌더링합니다.
     */
    @Composable
    open fun Render(
        state: S,
        onValueChanged: (V) -> Unit,
        isHighlighted: Boolean,
        modifier: Modifier
    ) {
    }

    /**
     * 컨테이너 상태(SectionUiState, LinearUiState)와 Leaf 상태를 모두 아우르는 통합 렌더링 진입점.
     */
    @Composable
    open fun Render(
        state: S,
        onValueChanged: (V) -> Unit,
        isHighlighted: Boolean,
        modifier: Modifier,
        renderChild: @Composable (UiItem) -> Unit
    ) {
        Render(
            state = state,
            onValueChanged = onValueChanged,
            isHighlighted = isHighlighted,
            modifier = modifier
        )
    }
}

/**
 * 프레임워크 기본 Common UiState 렌더러들과 모듈별 Custom UiState 렌더러(Hilt @IntoSet)를
 * ItemUiState의 KClass 기준으로 통합 관리하는 레지스트리.
 */
class ItemRendererRegistry @Inject constructor(
    customRenderers: Set<@JvmSuppressWildcards ItemRenderer<*, *>>
) {
    constructor() : this(emptySet())

    private val rendererMap: Map<KClass<out ItemUiState>, ItemRenderer<*, *>> = buildMap {
        // 1. 프레임워크 기본 Common UiState Renderers 등록
        for (defaultRenderer in DefaultItemRenderers.all) {
            put(defaultRenderer.stateClass, defaultRenderer)
        }
        // 2. 외부에서 주입된 Custom UiState Renderers 등록 (중복 등록 검증 포함)
        val seenCustomClasses = mutableSetOf<KClass<out ItemUiState>>()
        for (renderer in customRenderers) {
            val clazz = renderer.stateClass
            require(seenCustomClasses.add(clazz)) {
                "Duplicate ItemRenderer registered for UiState class '${clazz.qualifiedName}'"
            }
            put(clazz, renderer)
        }
    }

    fun hasRenderer(stateClass: KClass<out ItemUiState>): Boolean =
        rendererMap.containsKey(stateClass)

    @Suppress("UNCHECKED_CAST")
    fun <S : ItemUiState> getOrNull(state: S): ItemRenderer<S, Any?>? =
        rendererMap[state::class] as? ItemRenderer<S, Any?>

    @Suppress("UNCHECKED_CAST")
    fun <S : ItemUiState> get(state: S): ItemRenderer<S, Any?> =
        (rendererMap[state::class]
            ?: error("No ItemRenderer registered for UiState class='${state::class.qualifiedName}' (item key='${state.item.key}')")) as ItemRenderer<S, Any?>
}

val LocalItemRendererRegistry = staticCompositionLocalOf {
    ItemRendererRegistry()
}
