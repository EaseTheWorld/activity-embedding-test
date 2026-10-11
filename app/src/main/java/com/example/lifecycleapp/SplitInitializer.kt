package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.window.embedding.EmbeddingAnimationBackground
import androidx.window.embedding.EmbeddingAnimationParams
import androidx.window.embedding.EmbeddingBounds
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayController
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitController
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitRule
import com.example.core.item.AppConfig

const val ACTION_SETTINGS_EMBED = "com.example.carsettings.ACTION_SETTINGS_EMBED"

class SplitInitializer : Initializer<RuleController> {

    companion object {
        /**
         * Tracks whether the Primary Overlay drawer is currently expanded when in Narrow mode.
         * In Wide mode (`!AppConfig.isOverlayMode`), the drawer is always expanded (35%).
         */
        var isOverlayDrawerExpanded: Boolean = false

        /**
         * Single source of truth for computing the Overlay's EmbeddingBounds.
         * Guarantees that in Wide mode, the Overlay width (`PRIMARY_DRAWER_RATIO`)
         * matches the DummyPrimary (`HostAnchorActivity`) split ratio 1:1.
         */
        fun computeOverlayBounds(context: Context, isExpanded: Boolean): EmbeddingBounds {
            val isNarrowMode = AppConfig.isOverlayMode(context)
            return if (!isNarrowMode || isExpanded) {
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

        /**
         * Computes the SplitAttributes for the underlying HostAnchorActivity <-> Secondary split.
         * - Wide mode: 35% (`PRIMARY_DRAWER_RATIO`) for HostAnchorActivity, 65% for Secondary (x = 35%).
         * - Narrow mode: `SPLIT_TYPE_EXPAND` (0% HostAnchor, 100% Secondary starting at x = 0%).
         */
        fun computeSplitAttributes(context: Context): SplitAttributes {
            val isNarrowMode = AppConfig.isOverlayMode(context)
            val splitType = if (isNarrowMode) {
                SplitAttributes.SplitType.SPLIT_TYPE_EXPAND
            } else {
                SplitAttributes.SplitType.ratio(AppConfig.PRIMARY_DRAWER_RATIO)
            }
            val animParams = EmbeddingAnimationParams.Builder()
                .setAnimationBackground(EmbeddingAnimationBackground.DEFAULT)
                .setChangeAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setOpenAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setCloseAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .build()

            return SplitAttributes.Builder()
                .setSplitType(splitType)
                .setLayoutDirection(SplitAttributes.LayoutDirection.LEFT_TO_RIGHT)
                .setAnimationParams(animParams)
                .build()
        }
    }

    override fun create(context: Context): RuleController {
        val ruleController = RuleController.getInstance(context)
        val isNarrowMode = AppConfig.isOverlayMode(context)
        Log.d("LifecycleLog", "[SplitInitializer] Initializing unified DummyPrimary + Overlay architecture (isNarrowMode=$isNarrowMode)")

        val anchorComponent = ComponentName(context, HostAnchorActivity::class.java)

        val filters = setOf(
            SplitPairFilter(anchorComponent, ComponentName("*", "*"), ACTION_SETTINGS_EMBED)
        )

        val defaultSplitAttributes = computeSplitAttributes(context)

        val splitPairRule = SplitPairRule.Builder(filters)
            .setMinWidthDp(0)
            .setMinHeightDp(0)
            .setMinSmallestWidthDp(0)
            .setDefaultSplitAttributes(defaultSplitAttributes)
            .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.ALWAYS)
            .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
            .setClearTop(false)
            .build()

        ruleController.setRules(setOf(splitPairRule))

        // Register SplitAttributesCalculator so Wide (35%) <-> Narrow (EXPAND 100%) transitions dynamically
        SplitController.getInstance(context).setSplitAttributesCalculator { _ ->
            val attrs = computeSplitAttributes(context)
            Log.d("LifecycleLog", "[SplitInitializer] SplitAttributesCalculator -> splitType=${attrs.splitType}")
            attrs
        }

        // Register OverlayAttributesCalculator so Overlay size is also centrally governed
        try {
            OverlayController.getInstance(context).setOverlayAttributesCalculator { params ->
                val bounds = computeOverlayBounds(context, isExpanded = isOverlayDrawerExpanded)
                Log.d("LifecycleLog", "[SplitInitializer] OverlayAttributesCalculator (tag=${params.overlayTag}) -> isExpanded=$isOverlayDrawerExpanded, bounds=$bounds")
                OverlayAttributes(bounds)
            }
        } catch (e: Exception) {
            Log.w("LifecycleLog", "[SplitInitializer] OverlayAttributesCalculator registration skipped: $e")
        }

        Log.d("LifecycleLog", "[SplitInitializer] Initialized SplitPairRule on HostAnchorActivity with ratio=${AppConfig.PRIMARY_DRAWER_RATIO}")
        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
