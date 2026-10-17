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

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(item: UiItem): T {
        return store.getOrPut(item.key) {
            val factory = factoryMap[item.key]
                ?: error("No Factory registered for item key='${item.key}'")
            factory.create(item)
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
