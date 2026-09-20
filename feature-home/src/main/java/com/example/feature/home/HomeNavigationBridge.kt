package com.example.feature.home

/**
 * Bridge between HomeActivity and PrimaryActivity to notify when HomeActivity
 * is resumed and revealed in the Secondary pane after a top activity finishes.
 */
object HomeNavigationBridge {

    @Volatile
    var onHomeRevealed: (() -> Unit)? = null

    fun notifyHomeRevealed() {
        onHomeRevealed?.invoke()
    }
}
