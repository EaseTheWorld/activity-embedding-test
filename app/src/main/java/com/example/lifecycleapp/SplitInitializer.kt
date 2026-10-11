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

    override fun create(context: Context): RuleController {
        val ruleController = RuleController.getInstance(context)
        val isNarrowOverlayMode = AppConfig.isOverlayMode(context)

        if (isNarrowOverlayMode) {
            // Narrow Mode:
            // - HomeActivity -> ExpandedTaskFragment #0
            // - CategoryHostActivity -> ExpandedTaskFragment #1 (hosts Generic/Light + sub-activities)
            val expandFilters = setOf(
                ActivityFilter(ComponentName(context, com.example.feature.home.HomeActivity::class.java), null),
                ActivityFilter(ComponentName(context, com.example.feature.home.CategoryHostActivity::class.java), null)
            )
            val expandRule = ActivityRule.Builder(expandFilters)
                .setAlwaysExpand(true)
                .build()
            ruleController.setRules(setOf(expandRule))

            try {
                OverlayController.getInstance(context).setOverlayAttributesCalculator { params ->
                    val bounds = computeOverlayBounds(context, isExpanded = isOverlayDrawerExpanded)
                    Log.d("LifecycleLog", "[SplitInitializer] OverlayAttributesCalculator (tag=${params.overlayTag}) -> isExpanded=$isOverlayDrawerExpanded, bounds=$bounds")
                    OverlayAttributes(bounds)
                }
            } catch (e: Exception) {
                Log.w("LifecycleLog", "[SplitInitializer] OverlayAttributesCalculator registration skipped: $e")
            }
            Log.d("LifecycleLog", "[SplitInitializer] Initialized Narrow Mode (ActivityRule alwaysExpand on HomeActivity & CategoryHostActivity + Pinned Overlay)")
        } else {
            // Wide Mode:
            // 1. PrimaryActivity <-> HomeActivity (root SecondaryTaskFragment #1, finishes together with PrimaryActivity)
            // 2. PrimaryActivity <-> CategoryHostActivity (category SecondaryTaskFragment #2, finishing it reveals HomeActivity)
            val primaryComponent = ComponentName(context, PrimaryActivity::class.java)
            val homeComponent = ComponentName(context, com.example.feature.home.HomeActivity::class.java)
            val categoryHostComponent = ComponentName(context, com.example.feature.home.CategoryHostActivity::class.java)

            val animParams = EmbeddingAnimationParams.Builder()
                .setAnimationBackground(EmbeddingAnimationBackground.DEFAULT)
                .setChangeAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setOpenAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setCloseAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .build()

            val defaultSplitAttributes = SplitAttributes.Builder()
                .setSplitType(SplitAttributes.SplitType.ratio(AppConfig.PRIMARY_DRAWER_RATIO))
                .setLayoutDirection(SplitAttributes.LayoutDirection.LEFT_TO_RIGHT)
                .setAnimationParams(animParams)
                .build()

            val homeSplitRule = SplitPairRule.Builder(
                setOf(SplitPairFilter(primaryComponent, homeComponent, null))
            )
                .setMinWidthDp(0)
                .setMinHeightDp(0)
                .setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(defaultSplitAttributes)
                .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.ALWAYS)
                .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
                .setClearTop(false)
                .build()

            val categorySplitRule = SplitPairRule.Builder(
                setOf(SplitPairFilter(primaryComponent, categoryHostComponent, null))
            )
                .setMinWidthDp(0)
                .setMinHeightDp(0)
                .setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(defaultSplitAttributes)
                .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
                .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
                .setClearTop(false)
                .build()

            ruleController.setRules(setOf(homeSplitRule, categorySplitRule))
            Log.d("LifecycleLog", "[SplitInitializer] Initialized Wide Mode (SplitPairRules for HomeActivity and CategoryHostActivity, ratio=${AppConfig.PRIMARY_DRAWER_RATIO})")
        }

        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
