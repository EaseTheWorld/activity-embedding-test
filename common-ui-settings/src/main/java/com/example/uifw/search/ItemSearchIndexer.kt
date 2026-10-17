package com.example.uifw.search

import com.example.uifw.item.*

data class SearchIndexEntry(
    val title: String,
    val targetKey: String,
    val screenRoute: String,
    val breadcrumb: List<String>,
    val isSection: Boolean
) {
    val formattedBreadcrumb: String
        get() = breadcrumb.joinToString(" > ")
}

object ItemSearchIndexer {

    /**
     * Recursively indexes the entire tree starting from the root screen.
     * Automatically traverses SubScreenEntryItems to construct complete
     * hierarchical breadcrumbs (e.g., Screen > Subscreen > Section > Item).
     */
    fun indexTree(rootScreen: ItemScreen): List<SearchIndexEntry> {
        val results = mutableListOf<SearchIndexEntry>()
        val visitedScreens = mutableSetOf<String>()

        fun traverse(
            screen: ItemScreen,
            screenBreadcrumb: List<String>
        ) {
            if (!visitedScreens.add(screen.route)) return

            fun traverseContainer(
                container: UiContainerItem,
                currentBreadcrumb: List<String>
            ) {
                container.children.forEach { child ->
                    when (child) {
                        is UiSectionItem -> {
                            val sectionBreadcrumb = currentBreadcrumb + child.title
                            results.add(
                                SearchIndexEntry(
                                    title = child.title,
                                    targetKey = child.key,
                                    screenRoute = screen.route,
                                    breadcrumb = sectionBreadcrumb,
                                    isSection = true
                                )
                            )
                            traverseContainer(child, sectionBreadcrumb)
                        }
                        is UiLinearItem -> {
                            traverseContainer(child, currentBreadcrumb)
                        }
                        is UiToggleItem -> {
                            results.add(
                                SearchIndexEntry(
                                    title = child.title,
                                    targetKey = child.key,
                                    screenRoute = screen.route,
                                    breadcrumb = currentBreadcrumb + child.title,
                                    isSection = false
                                )
                            )
                        }
                        is UiTextItem -> {
                            results.add(
                                SearchIndexEntry(
                                    title = child.text,
                                    targetKey = child.key,
                                    screenRoute = screen.route,
                                    breadcrumb = currentBreadcrumb + child.text,
                                    isSection = false
                                )
                            )
                        }
                        is UiSubScreenEntryItem -> {
                            results.add(
                                SearchIndexEntry(
                                    title = child.title,
                                    targetKey = child.key,
                                    screenRoute = screen.route,
                                    breadcrumb = currentBreadcrumb + child.title,
                                    isSection = false
                                )
                            )
                            // Recurse into target sub-screen with breadcrumb
                            traverse(
                                screen = child.targetScreen,
                                screenBreadcrumb = screenBreadcrumb + child.targetScreen.title
                            )
                        }
                        is UiCustomItem -> {
                            val customTitle = child.title
                            if (customTitle != null) {
                                results.add(
                                    SearchIndexEntry(
                                        title = customTitle,
                                        targetKey = child.key,
                                        screenRoute = screen.route,
                                        breadcrumb = currentBreadcrumb + customTitle,
                                        isSection = false
                                    )
                                )
                            }
                        }
                        else -> {
                            // Non-standard UiItem without custom handling
                        }
                    }
                }
            }

            traverseContainer(screen.tree.root, screenBreadcrumb)
        }

        traverse(rootScreen, listOf(rootScreen.title))
        return results
    }

    /**
     * Filters index entries matching query (case-insensitive search).
     */
    fun search(entries: List<SearchIndexEntry>, query: String): List<SearchIndexEntry> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        return entries.filter { entry ->
            entry.title.contains(q, ignoreCase = true) ||
                entry.targetKey.contains(q, ignoreCase = true)
        }
    }
}
