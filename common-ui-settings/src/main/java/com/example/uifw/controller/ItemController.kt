package com.example.uifw.controller

import com.example.uifw.item.UiItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * AndroidX ViewModel이 아닌, 개별 UI 노드를 제어하는 경량 POJO Controller.
 * 자신이 제어할 대상 UiItem(item)을 직접 선언하여 Assisted Injection이나 별도 Factory 없이 동작합니다.
 * Hilt @IntoSet 멀티바인딩을 통해 Set<ItemController> 형태로 레지스트리에 주입됩니다.
 */
abstract class ItemController {
    /**
     * 이 Controller가 담당하는 대상 UiItem (선언부의 val 프로퍼티 직접 참조)
     */
    abstract val item: UiItem

    protected val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    open fun onCleared() {
        controllerScope.cancel()
    }
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
