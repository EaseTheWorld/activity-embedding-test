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
     * 개별 ItemScreen의 트리를 색인합니다.
     * 부모 체인(screen.screenBreadcrumb)과 로컬 트리(Section -> Item)를 결합하여
     * "Screen > Subscreen > Section > Item" 형태의 계층형 Breadcrumb을 조립합니다.
     */
    fun indexScreen(screen: ItemScreen): List<SearchIndexEntry> {
        val results = mutableListOf<SearchIndexEntry>()
        val baseBreadcrumb = screen.screenBreadcrumb

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

        traverseContainer(screen.tree.root, baseBreadcrumb)
        return results
    }

    /**
     * 여러 Screen 컬렉션을 전달받아 일괄 색인합니다.
     */
    fun indexScreens(screens: Collection<ItemScreen>): List<SearchIndexEntry> {
        return screens.flatMap { indexScreen(it) }
    }

    /**
     * rootScreen과 그 하위 subScreens(또는 targetScreen)를 재귀 순회하여 전체 트리를 색인합니다.
     */
    fun indexTree(rootScreen: ItemScreen): List<SearchIndexEntry> {
        val allScreens = mutableListOf<ItemScreen>()
        val visited = mutableSetOf<String>()
        fun collect(s: ItemScreen) {
            if (!visited.add(s.route)) return
            allScreens.add(s)
            s.subScreens.forEach(::collect)
            fun scanContainer(c: UiContainerItem) {
                c.children.forEach { ch ->
                    if (ch is UiSubScreenEntryItem && ch.targetScreen != null) {
                        collect(ch.targetScreen)
                    } else if (ch is UiContainerItem) {
                        scanContainer(ch)
                    }
                }
            }
            scanContainer(s.tree.root)
        }
        collect(rootScreen)
        return indexScreens(allScreens)
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
