package com.example.core.item

object AppConfig {
    /**
     * Primary pane width ratio (35%) used for:
     * - Wide Mode: SplitAttributes.SplitType.ratio(0.35f) in SplitPairRule
     * - Narrow Mode: Expanded Overlay drawer width EmbeddingBounds.Dimension.ratio(0.35f)
     */
    const val PRIMARY_DRAWER_RATIO: Float = 0.35f

    /**
     * Floating hamburger button size in dp when collapsed in Narrow (Overlay) mode.
     */
    const val COLLAPSED_BUTTON_SIZE_DP: Int = 80

    /**
     * Tag for the Always-On-Top Primary Overlay container in Narrow mode.
     */
    const val TAG_PRIMARY_OVERLAY: String = "tag_primary_overlay"

    /**
     * Explicit mode toggle for testing Narrow (Pinned Overlay) vs Wide (Pure Split) on the same emulator.
     * - true  (Narrow Mode): No SplitPairRule; HomeActivity + categories stack in the fullscreen Task,
     *                        and PrimaryActivity runs as an Always-On-Top Pinned Overlay (80dp button <-> 35% drawer).
     * - false (Wide Mode):   Pure SplitPairRule; PrimaryActivity (left 35%) + SecondaryTaskFragment (right 65%),
     *                        with no Overlay container.
     */
    var IS_OVERLAY_MODE: Boolean = false

    fun isOverlayMode(@Suppress("UNUSED_PARAMETER") context: Any? = null): Boolean {
        return IS_OVERLAY_MODE
    }
}
