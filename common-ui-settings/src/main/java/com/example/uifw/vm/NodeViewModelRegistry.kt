package com.example.uifw.vm

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import com.example.uifw.node.UiNode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

class NodeViewModelRegistry @Inject constructor(
    factories: Set<@JvmSuppressWildcards NodeViewModelFactory<*>>
) {
    // 초기화 시점에 Set을 Map으로 변환하며 중복 바인딩 검증
    private val factoryMap: Map<String, NodeViewModelFactory<*>> = buildMap {
        for (factory in factories) {
            val key = factory.targetNode.key
            val prev = put(key, factory)
            require(prev == null) {
                "Duplicate NodeViewModelFactory registered for key '$key': " +
                    "${prev!!::class.qualifiedName} and ${factory::class.qualifiedName}"
            }
        }
    }

    private val store = mutableMapOf<String, NodeViewModel>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(node: UiNode): T {
        return store.getOrPut(node.key) {
            val factory = factoryMap[node.key]
                ?: error("No Factory registered for node key='${node.key}'")
            factory.create(node)
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
    val nodeRegistry: NodeViewModelRegistry
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        nodeRegistry.clearAll()
    }
}

val LocalNodeViewModelRegistry = staticCompositionLocalOf<NodeViewModelRegistry> {
    error("No NodeViewModelRegistry provided")
}
