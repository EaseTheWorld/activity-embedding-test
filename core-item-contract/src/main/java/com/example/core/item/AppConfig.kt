package com.example.core.item

object AppConfig {
    /**
     * Single source of truth for Primary Drawer width ratio.
     * In Wide (Split-like) mode, both DummyPrimary Split ratio and PrimaryActivity Overlay width
     * use this exact constant so their boundaries always match 1:1 without drift.
     */
    const val PRIMARY_DRAWER_RATIO: Float = 0.35f

    /**
     * Floating hamburger button size in dp when collapsed in Narrow (Overlay) mode.
     */
    const val COLLAPSED_BUTTON_SIZE_DP: Int = 80

    /**
     * Hardcoded toggle for testing Narrow (0% EXPAND + collapsible overlay) vs Wide (35% fixed split + fixed overlay)
     * on the same emulator.
     * - When true (Narrow / sw < 800dp): Secondary uses SplitType.SPLIT_TYPE_EXPAND (100% full screen at x=0),
     *   and Primary overlay toggles between 80dp floating button and 35% drawer.
     * - When false (Wide / sw >= 800dp): DummyPrimary takes 35% (Secondary starts at x=35%),
     *   and Primary overlay permanently covers the left 35% area.
     */
    var IS_OVERLAY_MODE: Boolean = false

    fun isOverlayMode(@Suppress("UNUSED_PARAMETER") context: Any? = null): Boolean {
        return IS_OVERLAY_MODE
    }
}

