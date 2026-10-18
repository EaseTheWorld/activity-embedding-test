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

/**
 * 정적 ItemScreen 이동과 동적 Route 문자열(예: createRoute("...")) 이동을 모두 지원하는 네비게이터.
 * SAM(fun interface)이므로 테스트나 단순 호출부에서는 `{ screen -> ... }` 람다로도 즉시 생성 가능합니다.
 */
fun interface ItemNavigator {
    fun navigateToScreen(screen: com.example.uifw.item.ItemScreen)

    fun navigateToRoute(route: String) {}

    operator fun invoke(screen: com.example.uifw.item.ItemScreen) = navigateToScreen(screen)
    operator fun invoke(route: String) = navigateToRoute(route)
}

interface EntryController {
    val item: UiItem

    /**
     * 기본 동작: EntryItem(UiSubScreenEntryItem)에 선언된 정적 targetScreen으로 자동 이동합니다.
     * 런타임 조건에 따라 다른 화면으로 분기하거나 동적 라우트 인자(createRoute)를 넘겨야 할 때만 오버라이드합니다.
     */
    fun onClick(navigator: ItemNavigator) {
        val target = (item as? com.example.uifw.item.UiSubScreenEntryItem)?.targetScreen
        if (target != null) {
            navigator(target)
        }
    }
}


