package com.example.uifw.controller

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.example.uifw.item.UiItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

class ItemControllerRegistry @Inject constructor(
    factories: Set<@JvmSuppressWildcards ItemControllerFactory<*>>
) {
    // 초기화 시점에 Set을 Map으로 변환하며 중복 바인딩 검증
    private val factoryMap: Map<String, ItemControllerFactory<*>> = buildMap {
        for (factory in factories) {
            val key = factory.targetItem.key
            val prev = put(key, factory)
            require(prev == null) {
                "Duplicate ItemControllerFactory registered for key '$key': " +
                    "${prev!!::class.qualifiedName} and ${factory::class.qualifiedName}"
            }
        }
    }

    private val store = mutableMapOf<String, ItemController>()

    /**
     * 해당 key에 매핑된 Factory가 등록되어 있는지 여부를 반환합니다.
     */
    fun hasFactory(key: String): Boolean = factoryMap.containsKey(key)

    /**
     * key 문자열을 통해 해당 Controller가 담당하는 원본 UiItem 인스턴스를 반환합니다.
     */
    fun findItem(key: String): UiItem? = factoryMap[key]?.targetItem

    /**
     * 메모리에 이미 생성/캐시된 Controller를 key 문자열로 조회합니다. (없으면 null 반환)
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrNull(key: String): T? = store[key] as? T

    /**
     * 메모리에 이미 생성/캐시된 Controller를 UiItem 인스턴스로 조회합니다. (없으면 null 반환)
     */
    fun <T> getOrNull(item: UiItem): T? = getOrNull(item.key)

    /**
     * UiItem 인스턴스로부터 Controller를 조회하거나 생성합니다.
     */
    fun <T> getOrCreate(item: UiItem): T = getOrCreate(item.key)

    /**
     * key 문자열 또는 DeepLink 경로로부터 Controller를 조회하거나 생성합니다.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(key: String): T {
        return store.getOrPut(key) {
            val factory = factoryMap[key]
                ?: error("No Factory registered for item key='$key'")
            factory.create(factory.targetItem)
        } as T
    }

    fun clear(key: String) {
        store.remove(key)?.onCleared()
    }

    fun clearAll() {
        store.values.forEach { it.onCleared() }
        store.clear()
    }
}

@HiltViewModel
class ScreenHostViewModel @Inject constructor(
    val itemRegistry: ItemControllerRegistry
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        itemRegistry.clearAll()
    }
}

val LocalItemControllerRegistry = staticCompositionLocalOf<ItemControllerRegistry> {
    error("No ItemControllerRegistry provided")
}
