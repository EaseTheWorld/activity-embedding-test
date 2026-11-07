package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.window.embedding.ActivityFilter
import androidx.window.embedding.ActivityRule
import androidx.window.embedding.EmbeddingAnimationBackground
import androidx.window.embedding.EmbeddingAnimationParams
import androidx.window.embedding.EmbeddingBounds
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayController
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitRule
import com.example.core.item.AppConfig

const val ACTION_SETTINGS_EMBED = "com.example.carsettings.ACTION_SETTINGS_EMBED"

/**
 * Configures Jetpack WindowManager rules at startup using an explicit mode branch:
 * - Wide Mode (`!AppConfig.isOverlayMode`):
 *   Uses pure `SplitPairRule` (`PrimaryActivity` [35%] <-> `*` [65%]). No Overlay is used.
 * - Narrow Mode (`AppConfig.isOverlayMode`):
 *   Uses `ActivityRule(alwaysExpand = true)` (no `SplitPairRule` and no Dummy Primary) so content
 *   activities (`HomeActivity` + categories, including external plugins) run inside an organized
 *   fullscreen TaskFragment beneath `PrimaryActivity`'s Always-On-Top Pinned Overlay.
 */
class SplitInitializer : Initializer<RuleController> {

    companion object {
        var isOverlayDrawerExpanded: Boolean = false

        @Volatile
        private var lastInitializedOverlayMode: Boolean? = null

        fun getLastInitializedOverlayMode(): Boolean? = lastInitializedOverlayMode

        /**
         * Ensures WindowManager embedding rules are initialized ONLY for the currently active mode
         * (`Wide Split` vs `Narrow Overlay`) and skips redundant re-initialization if the mode has not changed.
         */
        fun ensureInitialized(context: Context, targetOverlayMode: Boolean? = null) {
            val currentMode = targetOverlayMode ?: AppConfig.isOverlayMode(context)
            if (lastInitializedOverlayMode == currentMode) {
                return
            }
            SplitInitializer().create(context.applicationContext, currentMode)
        }

        fun computeOverlayBounds(context: Context, isExpanded: Boolean): EmbeddingBounds {
            return if (isExpanded) {
                EmbeddingBounds(
                    EmbeddingBounds.Alignment.ALIGN_LEFT,
                    EmbeddingBounds.Dimension.ratio(AppConfig.PRIMARY_DRAWER_RATIO),
                    EmbeddingBounds.Dimension.DIMENSION_EXPANDED
                )
            } else {
                val dpPx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * context.resources.displayMetrics.density).toInt()
                EmbeddingBounds(
                    EmbeddingBounds.Alignment.ALIGN_LEFT,
                    EmbeddingBounds.Dimension.pixel(dpPx),
                    EmbeddingBounds.Dimension.pixel(dpPx)
                )
            }
        }
    }

    override fun create(context: Context): RuleController = create(context, null)

    fun create(context: Context, forcedOverlayMode: Boolean?): RuleController {
        val ruleController = RuleController.getInstance(context)
        val isNarrowOverlayMode = forcedOverlayMode ?: AppConfig.isOverlayMode(context)
        lastInitializedOverlayMode = isNarrowOverlayMode

        if (isNarrowOverlayMode) {
            // Narrow Mode:
            // 1. HomeActivity -> Expanded Task root (alwaysExpand = true)
            val homeFilter = ActivityFilter(ComponentName(context, com.example.feature.home.HomeActivity::class.java), null)
            val homeExpandRule = ActivityRule.Builder(setOf(homeFilter))
                .setAlwaysExpand(true)
                .build()

            // 2. SplitPairRule between HomeActivity and Categories with SPLIT_TYPE_EXPAND
            // Allows tracking and dismissing the category container via public SplitController.splitInfoList API (0% reflection)
            val homeComponent = ComponentName(context, com.example.feature.home.HomeActivity::class.java)
            val wildcardComponent = ComponentName("*", "*")
            val categorySplitRule = SplitPairRule.Builder(
                setOf(SplitPairFilter(homeComponent, wildcardComponent, ACTION_SETTINGS_EMBED))
            )
                .setMinWidthDp(0)
                .setMinHeightDp(0)
                .setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(
                    SplitAttributes.Builder()
                        .setSplitType(SplitAttributes.SplitType.SPLIT_TYPE_EXPAND)
                        .build()
                )
                .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
                .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
                .setClearTop(false)
                .build()

            ruleController.setRules(setOf(homeExpandRule, categorySplitRule))

            OverlayBoundsHelper.initCalculator(context)
            Log.d("LifecycleLog", "[SplitInitializer] Initialized Narrow Mode (SplitPairRule HomeActivity <-> ACTION_SETTINGS_EMBED with SPLIT_TYPE_EXPAND + Pinned Overlay)")
        } else {
            // Wide Mode:
            // 1. HomeActivity (Primary, Task Root) <-> ListActivity (Secondary, sidebar menu)
            //    Configured with LayoutDirection.RIGHT_TO_LEFT so HomeActivity stays on the RIGHT (65%),
            //    and ListActivity is pinned to the LEFT (35%).
            // 2. ListActivity (Primary) <-> Category (Secondary, ACTION_SETTINGS_EMBED)
            //    Configured with LayoutDirection.LEFT_TO_RIGHT so Category launches into the RIGHT (65%),
            //    covering HomeActivity. Dismissing the category reveals HomeActivity underneath.
            val listComponent = ComponentName(context, ListActivity::class.java)
            val homeComponent = ComponentName(context, com.example.feature.home.HomeActivity::class.java)
            val wildcardComponent = ComponentName("*", "*")

            val animParams = EmbeddingAnimationParams.Builder()
                .setAnimationBackground(EmbeddingAnimationBackground.DEFAULT)
                .setChangeAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setOpenAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setCloseAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .build()

            // RTL Split: HomeActivity (Primary, 65% right) <-> ListActivity (Secondary, 35% left)
            val rtlSplitAttributes = SplitAttributes.Builder()
                .setSplitType(SplitAttributes.SplitType.ratio(1.0f - AppConfig.PRIMARY_DRAWER_RATIO))
                .setLayoutDirection(SplitAttributes.LayoutDirection.RIGHT_TO_LEFT)
                .setAnimationParams(animParams)
                .build()

            val homeListSplitRule = SplitPairRule.Builder(
                setOf(SplitPairFilter(homeComponent, listComponent, null))
            )
                .setMinWidthDp(0)
                .setMinHeightDp(0)
                .setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(rtlSplitAttributes)
                .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.ALWAYS)
                .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
                .setClearTop(false)
                .build()

            // LTR Split: ListActivity (Primary, 35% left) <-> Category (Secondary, 65% right)
            val ltrSplitAttributes = SplitAttributes.Builder()
                .setSplitType(SplitAttributes.SplitType.ratio(AppConfig.PRIMARY_DRAWER_RATIO))
                .setLayoutDirection(SplitAttributes.LayoutDirection.LEFT_TO_RIGHT)
                .setAnimationParams(animParams)
                .build()

            val categorySplitRule = SplitPairRule.Builder(
                setOf(SplitPairFilter(listComponent, wildcardComponent, ACTION_SETTINGS_EMBED))
            )
                .setMinWidthDp(0)
                .setMinHeightDp(0)
                .setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(ltrSplitAttributes)
                .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
                .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
                .setClearTop(false)
                .build()

            ruleController.setRules(setOf(homeListSplitRule, categorySplitRule))
            Log.d("LifecycleLog", "[SplitInitializer] Initialized Wide Mode (HomeActivity Primary RTL 65:35 ListActivity + ListActivity LTR 35:65 Category)")
        }

        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
