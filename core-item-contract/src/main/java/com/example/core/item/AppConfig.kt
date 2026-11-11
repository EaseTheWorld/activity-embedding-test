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
     * Intent Extra Key to control category header display based on breakpoint width in DP.
     * Host app passes the breakpoint (e.g. 840dp).
     * If screen/window width is strictly smaller than this breakpoint (Narrow/Overlay Mode),
     * each category's main screen renders its own top category title/header.
     * If width is greater than or equal to breakpoint (Wide/Split Mode), the title is hidden
     * because the persistent 35% primary sidebar already highlights the active category.
     */
    const val EXTRA_CATEGORY_HEADER_BREAKPOINT_DP: String = "extra_category_header_breakpoint_dp"

    /**
     * Intent extra key carrying the host category-menu drawer Activity target as a
     * [android.content.ComponentName] (or [android.content.pm.ComponentInfo] / flattened String).
     * Category main screens show the top-left menu button only when this target is provided
     * alongside narrow mode.
     */
    const val EXTRA_MENU_COMPONENT: String = "extra_menu_component"

    /**
     * Backward-compatible boolean extra key for explicit override.
     */
    const val EXTRA_SHOW_CATEGORY_HEADER: String = "extra_show_category_header"

    /**
     * WindowManager Window Extension Bundle Key for configuring whether the overlay activity stack
     * is associated with the launching activity. Setting this to `false` creates an Always-On-Top
     * Pinned Overlay that survives launcher navigation and finishing.
     */
    const val KEY_OVERLAY_SHOULD_ASSOCIATE_WITH_LAUNCHING_ACTIVITY: String =
        "androidx.window.extensions.embedding.shouldAssociateWithLaunchingActivity"

    /**
     * Breakpoint in DP for switching between Pure Split Mode and Narrow Overlay Mode.
     * When screenWidthDp < 840, Narrow Overlay Mode is used; otherwise, Wide Pure Split Mode is used.
     */
    const val OVERLAY_MODE_WIDTH_DP_BREAKPOINT: Int = 840

    /**
     * Explicit mode override for testing Narrow (Pinned Overlay) vs Wide (Pure Split).
     * If null, mode is automatically determined by display width (screenWidthDp < OVERLAY_MODE_WIDTH_DP_BREAKPOINT).
     */
    var OVERRIDE_OVERLAY_MODE: Boolean? = null

    /**
     * Explicit mode toggle for testing Narrow (Pinned Overlay) vs Wide (Pure Split) on the same emulator.
     * Kept for backwards compatibility with tests that read/write IS_OVERLAY_MODE.
     */
    var IS_OVERLAY_MODE: Boolean
        get() = OVERRIDE_OVERLAY_MODE ?: true
        set(value) {
            OVERRIDE_OVERLAY_MODE = value
        }

    fun isOverlayMode(context: Any? = null): Boolean {
        OVERRIDE_OVERLAY_MODE?.let { return it }
        val widthDp = extractScreenWidthDp(context)
        return if (widthDp > 0) {
            widthDp < OVERLAY_MODE_WIDTH_DP_BREAKPOINT
        } else {
            true
        }
    }

    /**
     * Determines whether a Category's main screen should render its top category title.
     * Evaluates dynamically using [EXTRA_CATEGORY_HEADER_BREAKPOINT_DP] from the intent
     * (or fallback [OVERLAY_MODE_WIDTH_DP_BREAKPOINT]) against the current screen/task width.
     *
     * Sub-screens are excluded because they already have their own dedicated top app bar / title.
     */
    fun shouldShowCategoryHeader(context: Any?, intent: Any? = null): Boolean {
        OVERRIDE_OVERLAY_MODE?.let { return it }

        // 1. Check for legacy/explicit boolean override if present
        if (intent != null) {
            try {
                val hasExtraMethod = intent.javaClass.getMethod("hasExtra", String::class.java)
                val hasBoolean = hasExtraMethod.invoke(intent, EXTRA_SHOW_CATEGORY_HEADER) as? Boolean ?: false
                if (hasBoolean) {
                    val getBooleanExtraMethod = intent.javaClass.getMethod(
                        "getBooleanExtra",
                        String::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                    return getBooleanExtraMethod.invoke(intent, EXTRA_SHOW_CATEGORY_HEADER, true) as Boolean
                }
            } catch (_: Exception) {}
        }

        // 2. Extract breakpoint from Intent extras (defaults to 840dp)
        var breakpoint = OVERLAY_MODE_WIDTH_DP_BREAKPOINT
        if (intent != null) {
            try {
                val getIntExtraMethod = intent.javaClass.getMethod(
                    "getIntExtra",
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
                breakpoint = getIntExtraMethod.invoke(
                    intent,
                    EXTRA_CATEGORY_HEADER_BREAKPOINT_DP,
                    OVERLAY_MODE_WIDTH_DP_BREAKPOINT
                ) as Int
            } catch (_: Exception) {}
        }

        val widthDp = extractScreenWidthDp(context)
        return if (widthDp > 0) {
            widthDp < breakpoint
        } else {
            true
        }
    }

    /**
     * Safely inspects screenWidthDp from Context, Configuration, or Resources via reflection
     * so core-item-contract remains a pure kotlin-jvm library without depending on Android SDK.
     */
        /**
     * Safely inspects the host Task or Display screen width in DP rather than the split sub-window width.
     * When PrimaryActivity is in Split mode (35%), its local Activity Configuration.screenWidthDp
     * reports the 35% sub-window (e.g. 448dp), NOT the full screen width (1280dp).
     * Therefore, we must inspect the WindowManager maximumWindowMetrics or Application Context displayMetrics
     * so that Wide Split mode does not mistakenly trigger Narrow Overlay mode.
     */
    private fun extractScreenWidthDp(obj: Any?): Int {
        if (obj == null) return 0
        try {
            val clazz = obj.javaClass

            // 1. If it's a Context, try WindowManager.maximumWindowMetrics (API 30+)
            try {
                val getSystemServiceMethod = clazz.getMethod("getSystemService", String::class.java)
                val wm = getSystemServiceMethod.invoke(obj, "window")
                if (wm != null) {
                    val getMaxMetricsMethod = wm.javaClass.getMethod("getMaximumWindowMetrics")
                    val maxMetrics = getMaxMetricsMethod.invoke(wm)
                    if (maxMetrics != null) {
                        val getBoundsMethod = maxMetrics.javaClass.getMethod("getBounds")
                        val bounds = getBoundsMethod.invoke(maxMetrics)
                        if (bounds != null) {
                            val widthMethod = bounds.javaClass.getMethod("width")
                            val widthPx = widthMethod.invoke(bounds) as Int

                            val getResMethod = clazz.getMethod("getResources")
                            val resources = getResMethod.invoke(obj)
                            if (resources != null) {
                                val getMetricsMethod = resources.javaClass.getMethod("getDisplayMetrics")
                                val metrics = getMetricsMethod.invoke(resources)
                                if (metrics != null) {
                                    val densityField = metrics.javaClass.getField("density")
                                    val density = densityField.getFloat(metrics)
                                    if (widthPx > 0 && density > 0f) {
                                        return (widthPx / density).toInt()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Try applicationContext's displayMetrics (full display, not sub-window)
            try {
                val getAppCtxMethod = clazz.getMethod("getApplicationContext")
                val appCtx = getAppCtxMethod.invoke(obj)
                if (appCtx != null) {
                    val getResMethod = appCtx.javaClass.getMethod("getResources")
                    val resources = getResMethod.invoke(appCtx)
                    if (resources != null) {
                        val getMetricsMethod = resources.javaClass.getMethod("getDisplayMetrics")
                        val metrics = getMetricsMethod.invoke(resources)
                        if (metrics != null) {
                            val widthPxField = metrics.javaClass.getField("widthPixels")
                            val densityField = metrics.javaClass.getField("density")
                            val widthPx = widthPxField.getInt(metrics)
                            val density = densityField.getFloat(metrics)
                            if (widthPx > 0 && density > 0f) {
                                return (widthPx / density).toInt()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Direct screenWidthDp property (Configuration)
            try {
                val field = clazz.getField("screenWidthDp")
                return field.getInt(obj)
            } catch (_: Exception) {}

            // 4. getConfiguration() -> screenWidthDp (Resources)
            try {
                val getConfMethod = clazz.getMethod("getConfiguration")
                val configuration = getConfMethod.invoke(obj)
                if (configuration != null) {
                    val field = configuration.javaClass.getField("screenWidthDp")
                    return field.getInt(configuration)
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
        return 0
    }
}
