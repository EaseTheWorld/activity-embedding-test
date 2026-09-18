package com.example.feature.home

import android.content.Context
import com.example.common.ui.settings.UiItem
import com.example.core.item.CategoryItemRegistry
import com.example.core.item.Item
import com.example.core.item.ItemType

/**
 * Searchable representation of a setting item for the Home search engine.
 */
data class SearchableSettingItem(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val categoryId: String,
    val categoryTitle: String,
    val itemType: ItemType,
    val deepLinkUri: String
)

/**
 * Builds the search index from [CategoryItemRegistry] and performs search filtering.
 */
object HomeItemSearchIndexer {

    fun buildIndex(context: Context): List<SearchableSettingItem> {
        val list = mutableListOf<SearchableSettingItem>()
        for (provider in CategoryItemRegistry.getAllProviders()) {
            val catId = provider.categoryId
            val titleRes = context.resources.getIdentifier(provider.titleKey, "string", context.packageName)
            val catTitle = if (titleRes != 0) {
                try { context.getString(titleRes) } catch (_: Exception) { catId.replaceFirstChar { it.uppercase() } }
            } else {
                catId.replaceFirstChar { it.uppercase() }
            }

            fun addRecursive(item: Item) {
                val uiItem = item as? UiItem
                val itemTitle = if (uiItem != null && uiItem.titleRes != 0) {
                    try { context.getString(uiItem.titleRes) } catch (_: Exception) { item.id }
                } else item.id

                val subRes = uiItem?.subtitleRes
                val itemSubtitle = if (subRes != null && subRes != 0) {
                    try { context.getString(subRes) } catch (_: Exception) { "" }
                } else ""

                list.add(
                    SearchableSettingItem(
                        itemId = item.id,
                        title = itemTitle,
                        subtitle = itemSubtitle,
                        categoryId = catId,
                        categoryTitle = catTitle,
                        itemType = item.type,
                        deepLinkUri = "myapp://navigate/$catId/${item.id}"
                    )
                )

                item.children.forEach { addRecursive(it) }
            }

            provider.items.forEach { addRecursive(it) }
        }
        return list
    }

    fun search(query: String, items: List<SearchableSettingItem>): List<SearchableSettingItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val q = trimmed.lowercase()

        return items.filter { item ->
            item.title.lowercase().contains(q) ||
            item.subtitle.lowercase().contains(q) ||
            item.itemId.lowercase().contains(q) ||
            item.categoryTitle.lowercase().contains(q) ||
            item.categoryId.lowercase().contains(q)
        }
    }
}
