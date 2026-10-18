package com.example.uifw.controller

import androidx.compose.runtime.Immutable
import com.example.uifw.item.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// ── 1. ItemUiState 계층 (정적 UiItem + 동적 value / isVisible / isEnabled) ──

@Immutable
interface ItemUiState {
    val item: UiItem
    val isVisible: Boolean get() = true
    val isEnabled: Boolean get() = true
}

@Immutable
data class SectionUiState(
    override val item: UiSectionItem,
    val title: String = item.title,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

@Immutable
data class LinearUiState(
    override val item: UiLinearItem,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

@Immutable
data class ToggleUiState(
    override val item: UiToggleItem,
    val title: String = item.title,
    val isChecked: Boolean,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

@Immutable
data class TextUiState(
    override val item: UiTextItem,
    val text: String = item.text,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

@Immutable
data class EntryUiState(
    override val item: UiSubScreenEntryItem,
    val title: String = item.title,
    val targetScreen: ItemScreen? = item.targetScreen,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

@Immutable
data class DefaultCustomItemUiState(
    override val item: UiCustomItem,
    val title: String? = item.title,
    override val isVisible: Boolean = true,
    override val isEnabled: Boolean = true
) : ItemUiState

fun createDefaultUiState(item: UiItem): ItemUiState = when (item) {
    is UiSectionItem -> SectionUiState(item = item)
    is UiLinearItem -> LinearUiState(item = item)
    is UiToggleItem -> ToggleUiState(item = item, isChecked = false)
    is UiTextItem -> TextUiState(item = item)
    is UiSubScreenEntryItem -> EntryUiState(item = item)
    is UiCustomItem -> DefaultCustomItemUiState(item = item)
    else -> object : ItemUiState {
        override val item: UiItem = item
    }
}

// ── 2. ItemController 및 StatefulItemController / ToggleItemController ──

/**
 * 개별 UI 노드를 제어하며 동적 상태(uiState: StateFlow<ItemUiState>)를 방출하는 Controller.
 * Renderer에게는 수집된 `state`와 `controller::onValueChanged` 람다가 분리되어 전달됩니다.
 */
abstract class ItemController {
    /**
     * 이 Controller가 담당하는 대상 정적 UiItem
     */
    abstract val item: UiItem

    protected val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 이 Controller가 emit하는 동적 UiState 스트림.
     * ToggleController 구현체는 ToggleUiState로, EntryController 구현체는 EntryUiState로 자동 변환되며,
     * StatefulItemController 하위 클래스는 고유 UiState(예: ToggleUiState, AudioLatencyUiState)를 직접 emit합니다.
     */
    open val uiState: StateFlow<ItemUiState> by lazy {
        when (this) {
            is ToggleController -> {
                val toggleItem = item as UiToggleItem
                combine(isChecked, isEnabled, isVisible) { checked, enabled, visible ->
                    ToggleUiState(
                        item = toggleItem,
                        isChecked = checked,
                        isVisible = visible,
                        isEnabled = enabled
                    )
                }.stateIn(
                    scope = controllerScope,
                    started = SharingStarted.Eagerly,
                    initialValue = ToggleUiState(
                        item = toggleItem,
                        isChecked = isChecked.value,
                        isVisible = isVisible.value,
                        isEnabled = isEnabled.value
                    )
                )
            }
            else -> MutableStateFlow(createDefaultUiState(item)).asStateFlow()
        }
    }

    /**
     * Renderer에서 사용자 입력이 발생했을 때 `controller::onValueChanged` 레퍼런스로 호출되는 진입점.
     */
    open fun onValueChanged(value: Any?) {
        when (this) {
            is ToggleController -> if (value is Boolean) onToggleChange(value)
            is EntryController -> if (value is ItemNavigator) onClick(value)
        }
    }

    open fun onCleared() {
        controllerScope.cancel()
    }
}

/**
 * 특정 ItemUiState(S)를 방출하고 타입 안전한 값 변경(V)을 처리하는 기반 Controller.
 */
abstract class StatefulItemController<S : ItemUiState, V> : ItemController() {
    abstract override val uiState: StateFlow<S>

    abstract fun onValueChange(value: V)

    @Suppress("UNCHECKED_CAST")
    override fun onValueChanged(value: Any?) {
        onValueChange(value as V)
    }
}

// ── 3. UI 컴포넌트별 상태/이벤트 계약 (Contracts) ──

interface ToggleController {
    val isChecked: StateFlow<Boolean>
    val isEnabled: StateFlow<Boolean>
    val isVisible: StateFlow<Boolean>
        get() = DefaultVisibleFlow
    fun onToggleChange(checked: Boolean)
}

private val DefaultVisibleFlow: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

/**
 * ToggleUiState를 직접 emit하는 표준 ToggleItemController 기반 클래스.
 * value(isChecked), isVisible, isEnabled를 하나의 ToggleUiState로 원자적(atomic)으로 관리합니다.
 */
abstract class ToggleItemController(
    override val item: UiToggleItem,
    initialChecked: Boolean = false,
    initialVisible: Boolean = true,
    initialEnabled: Boolean = true
) : StatefulItemController<ToggleUiState, Boolean>(), ToggleController {

    private val _isChecked = MutableStateFlow(initialChecked)
    override val isChecked: StateFlow<Boolean> = _isChecked.asStateFlow()

    private val _isEnabled = MutableStateFlow(initialEnabled)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isVisible = MutableStateFlow(initialVisible)
    override val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _uiState = MutableStateFlow(
        ToggleUiState(
            item = item,
            isChecked = initialChecked,
            isVisible = initialVisible,
            isEnabled = initialEnabled
        )
    )
    override val uiState: StateFlow<ToggleUiState> = _uiState.asStateFlow()

    override fun onValueChange(value: Boolean) {
        onToggleChange(value)
    }

    override fun onToggleChange(checked: Boolean) {
        _isChecked.value = checked
        _uiState.value = _uiState.value.copy(isChecked = checked)
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
    }

    fun setVisible(visible: Boolean) {
        _isVisible.value = visible
        _uiState.value = _uiState.value.copy(isVisible = visible)
    }
}

interface TextController {
    val text: StateFlow<String>
    fun onClick() {}
}

/**
 * 정적 ItemScreen 이동과 동적 Route 문자열(예: createRoute("...")) 이동을 모두 지원하는 네비게이터.
 */
fun interface ItemNavigator {
    fun navigateToScreen(screen: ItemScreen)

    fun navigateToRoute(route: String) {}

    operator fun invoke(screen: ItemScreen) = navigateToScreen(screen)
    operator fun invoke(route: String) = navigateToRoute(route)
}

interface EntryController {
    val item: UiItem

    /**
     * 기본 동작: EntryItem(UiSubScreenEntryItem)에 선언된 정적 targetScreen으로 자동 이동합니다.
     */
    fun onClick(navigator: ItemNavigator) {
        val target = (item as? UiSubScreenEntryItem)?.targetScreen
        if (target != null) {
            navigator(target)
        }
    }
}

/**
 * EntryUiState를 직접 emit하고 onValueChange(navigator)에서 화면 이동을 수행하는 표준 EntryItemController.
 * ToggleItemController와 완전히 동일한 StatefulItemController<S, V> 구조를 따릅니다.
 */
open class EntryItemController(
    override val item: UiSubScreenEntryItem,
    initialVisible: Boolean = true,
    initialEnabled: Boolean = true
) : StatefulItemController<EntryUiState, ItemNavigator>(), EntryController {

    private val _uiState = MutableStateFlow(
        EntryUiState(
            item = item,
            title = item.title,
            targetScreen = item.targetScreen,
            isVisible = initialVisible,
            isEnabled = initialEnabled
        )
    )
    override val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    override fun onValueChange(value: ItemNavigator) {
        onClick(value)
    }

    fun setVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isVisible = visible)
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
    }
}
