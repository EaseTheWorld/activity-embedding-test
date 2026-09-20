package com.example.feature.home

/**
 * Bridge between HomeActivity and PrimaryActivity to notify when HomeActivity
 * is resumed and revealed in the Secondary pane after a top activity finishes.
 */
object HomeNavigationBridge {

    @Volatile
    var onHomeRevealed: (() -> Unit)? = null

    @Volatile
    var onCategorySelected: ((String) -> Unit)? = null

    fun notifyHomeRevealed() {
        onHomeRevealed?.invoke()
    }

    fun notifyCategorySelected(categoryId: String) {
        onCategorySelected?.invoke(categoryId)
    }
}
