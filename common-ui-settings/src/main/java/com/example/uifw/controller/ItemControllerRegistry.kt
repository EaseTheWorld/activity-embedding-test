package com.example.uifw.controller

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.example.uifw.item.UiItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

class ItemControllerRegistry @Inject constructor(
    controllers: Set<@JvmSuppressWildcards ItemController>
) {
    // 초기화 시점에 Set을 Map으로 변환하며 item.key 중복 바인딩 검증
    private val controllerMap: Map<String, ItemController> = buildMap {
        for (controller in controllers) {
            val key = controller.item.key
            val prev = put(key, controller)
            require(prev == null) {
                "Duplicate ItemController registered for key '$key': " +
                    "${prev!!::class.qualifiedName} and ${controller::class.qualifiedName}"
            }
        }
    }

    /**
     * 해당 key에 매핑된 Controller가 등록되어 있는지 여부를 반환합니다.
     */
    fun hasController(key: String): Boolean = controllerMap.containsKey(key)

    /**
     * key 문자열을 통해 해당 Controller가 담당하는 원본 UiItem 인스턴스를 반환합니다.
     */
    fun findItem(key: String): UiItem? = controllerMap[key]?.item

    /**
     * key 문자열을 통해 ItemController를 조회합니다. (없으면 error)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T {
        return (controllerMap[key]
            ?: error("No ItemController registered for item key='$key'")) as T
    }

    /**
     * UiItem 인스턴스를 통해 ItemController를 조회합니다. (없으면 error)
     */
    fun <T> get(item: UiItem): T = get(item.key)

    /**
     * key 문자열로 ItemController를 조회합니다. (없으면 null 반환)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(key: String): T? = controllerMap[key] as? T

    /**
     * UiItem 인스턴스로 ItemController를 조회합니다. (없으면 null 반환)
     */
    fun <T> getOrNull(item: UiItem): T? = getOrNull(item.key)

    /**
     * get과 동일한 편의 함수 (기존 getOrCreate 호출 호환)
     */
    fun <T> getOrCreate(key: String): T = get(key)

    /**
     * get과 동일한 편의 함수 (기존 getOrCreate 호출 호환)
     */
    fun <T> getOrCreate(item: UiItem): T = get(item.key)

    fun clear(key: String) {
        controllerMap[key]?.onCleared()
    }

    fun clearAll() {
        controllerMap.values.forEach { it.onCleared() }
    }
}

@HiltViewModel
class ScreenHostViewModel @Inject constructor(
    val itemRegistry: ItemControllerRegistry,
    val rendererRegistry: com.example.uifw.ui.ItemRendererRegistry = com.example.uifw.ui.ItemRendererRegistry()
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        itemRegistry.clearAll()
    }
}

val LocalItemControllerRegistry = staticCompositionLocalOf<ItemControllerRegistry> {
    error("No ItemControllerRegistry provided")
}
