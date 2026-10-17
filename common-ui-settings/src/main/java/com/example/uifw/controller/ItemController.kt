package com.example.uifw.controller

import com.example.uifw.item.UiItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

abstract class ItemController {
    protected val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    open fun onCleared() {
        controllerScope.cancel()
    }
}

/**
 * Hilt @IntoSet 멀티바인딩을 위한 공통 Factory 인터페이스.
 * 어노테이션 문자열(@StringKey) 대신 targetItem을 코드로 직접 참조하여 오타 방지.
 */
interface ItemControllerFactory<out C : ItemController> {
    val targetItem: UiItem
    fun create(item: UiItem): C
}

// ── UI 컴포넌트별 상태/이벤트 계약 (Contracts) ──
interface ToggleController {
    val isChecked: StateFlow<Boolean>
    val isEnabled: StateFlow<Boolean>
    fun onToggleChange(checked: Boolean)
}

interface TextController {
    val text: StateFlow<String>
    fun onClick() {}
}
