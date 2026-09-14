package com.example.common.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.example.core.item.Item
import com.example.core.item.ItemViewModel
import com.example.core.item.ItemViewModelRegistry
import kotlin.reflect.KClass

/**
 * Composable renderer interface decoupling Item data schema from Jetpack Compose rendering.
 * Pure rendering contract without presentation mode coupling.
 */
interface ItemRenderer<in I : Item, T> {
    @Composable
    fun Render(
        item: I,
        viewModel: ItemViewModel<T>,
        modifier: Modifier
    )
}

/**
 * Registry holding mapping of Item classes to their respective [ItemRenderer].
 * Can be instantiated separately for different presentation surfaces (e.g. List vs Grid).
 */
class ItemRendererRegistry {
    private val renderers = mutableMapOf<KClass<out Item>, ItemRenderer<*, *>>()

    fun <I : Item, T> register(clazz: KClass<I>, renderer: ItemRenderer<I, T>) {
        renderers[clazz] = renderer
    }

    @Suppress("UNCHECKED_CAST")
    fun <I : Item, T> getRenderer(clazz: KClass<out I>): ItemRenderer<I, T>? {
        val exact = renderers[clazz]
        if (exact != null) {
            return exact as ItemRenderer<I, T>
        }
        val matching = renderers.entries.firstOrNull { (k, _) -> k.java.isAssignableFrom(clazz.java) }?.value
        return matching as? ItemRenderer<I, T>
    }

    /**
     * Checks if this registry has a renderer capable of rendering [clazz].
     * Used by Recent / Quick Controls screens to verify that an item is eligible for display.
     */
    fun hasRenderer(clazz: KClass<out Item>): Boolean =
        getRenderer<Item, Any?>(clazz) != null

    fun findRenderer(clazz: KClass<out Item>): ItemRenderer<*, *>? =
        getRenderer<Item, Any?>(clazz)

    @Composable
    @Suppress("UNCHECKED_CAST")
    fun Render(
        item: Item,
        viewModelRegistry: ItemViewModelRegistry,
        modifier: Modifier = Modifier
    ) {
        val renderer = getRenderer<Item, Any?>(item::class)
        val viewModel = viewModelRegistry.getViewModel<Any?>(item.id)

        if (renderer != null && viewModel != null) {
            renderer.Render(item, viewModel, modifier)
        }
    }

    companion object {
        /**
         * Default registry for standard full-width List Row presentations.
         * Contains renderers for all items supported in primary category screens.
         */
        val defaultListRegistry by lazy {
            ItemRendererRegistry().apply {
                register(UiToggleItem::class, ToggleListRenderer())
                register(UiChoiceItem::class, ChoiceListRenderer())
                register(UiSliderItem::class, SliderListRenderer())
            }
        }

        /**
         * Default registry for compact Grid Card presentations (Quick Controls / Recent).
         * Only items suitable for compact grid display are registered here.
         * Complex UI items (e.g. multi-band equalizers, 3D seat visualizers) are deliberately omitted.
         */
        val defaultGridRegistry by lazy {
            ItemRendererRegistry().apply {
                register(UiToggleItem::class, ToggleGridCardRenderer())
                register(UiChoiceItem::class, ChoiceGridCardRenderer())
                register(UiSliderItem::class, SliderGridCardRenderer())
            }
        }

        /**
         * Legacy alias pointing to [defaultListRegistry].
         */
        val defaultInstance get() = defaultListRegistry
    }
}

// ============================================================================
// 1. Standard List Row Renderers (Primary Category Screens: Door, Seat, Sound)
// ============================================================================

class ToggleListRenderer : ItemRenderer<UiToggleItem, Boolean> {
    @Composable
    override fun Render(
        item: UiToggleItem,
        viewModel: ItemViewModel<Boolean>,
        modifier: Modifier
    ) {
        ToggleItemRow(item = item, viewModel = viewModel, modifier = modifier)
    }
}

class ChoiceListRenderer : ItemRenderer<UiChoiceItem, Any?> {
    @Composable
    override fun Render(
        item: UiChoiceItem,
        viewModel: ItemViewModel<Any?>,
        modifier: Modifier
    ) {
        ChoiceItemRow(item = item, viewModel = viewModel, modifier = modifier)
    }
}

class SliderListRenderer : ItemRenderer<UiSliderItem, Int> {
    @Composable
    override fun Render(
        item: UiSliderItem,
        viewModel: ItemViewModel<Int>,
        modifier: Modifier
    ) {
        SliderItemRow(item = item, viewModel = viewModel, modifier = modifier)
    }
}

// ============================================================================
// 2. Compact Grid Card Renderers (Quick Controls & Recent Screen)
// ============================================================================

class ToggleGridCardRenderer : ItemRenderer<UiToggleItem, Boolean> {
    @Composable
    override fun Render(
        item: UiToggleItem,
        viewModel: ItemViewModel<Boolean>,
        modifier: Modifier
    ) {
        ToggleGridCard(item = item, viewModel = viewModel, modifier = modifier)
    }
}

class ChoiceGridCardRenderer : ItemRenderer<UiChoiceItem, Any?> {
    @Composable
    override fun Render(
        item: UiChoiceItem,
        viewModel: ItemViewModel<Any?>,
        modifier: Modifier
    ) {
        ChoiceGridCard(item = item, viewModel = viewModel, modifier = modifier)
    }
}

class SliderGridCardRenderer : ItemRenderer<UiSliderItem, Int> {
    @Composable
    override fun Render(
        item: UiSliderItem,
        viewModel: ItemViewModel<Int>,
        modifier: Modifier
    ) {
        SliderGridCard(item = item, viewModel = viewModel, modifier = modifier)
    }
}

// Backward compatibility typealiases
typealias ToggleItemRenderer = ToggleListRenderer
typealias ChoiceItemRenderer = ChoiceListRenderer
typealias SliderItemRenderer = SliderListRenderer

val LocalListItemRendererRegistry = staticCompositionLocalOf { ItemRendererRegistry.defaultListRegistry }
val LocalGridItemRendererRegistry = staticCompositionLocalOf { ItemRendererRegistry.defaultGridRegistry }
val LocalItemRendererRegistry = LocalListItemRendererRegistry
val LocalItemViewModelRegistry = staticCompositionLocalOf { ItemViewModelRegistry() }

