package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import androidx.window.embedding.ActivityFilter
import androidx.window.embedding.ActivityRule
import androidx.window.embedding.EmbeddingAnimationBackground
import androidx.window.embedding.EmbeddingAnimationParams
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
    }

    override fun create(context: Context): RuleController = create(context, null)

    fun create(context: Context, forcedOverlayMode: Boolean?): RuleController {
        val ruleController = RuleController.getInstance(context)
        val isNarrowOverlayMode = forcedOverlayMode ?: AppConfig.isOverlayMode(context)
        lastInitializedOverlayMode = isNarrowOverlayMode

        if (isNarrowOverlayMode) {
            // Narrow Mode:
            // Pure native Android Activity stack with CLEAR_TOP | SINGLE_TOP.
            // No WindowManager SplitPairRules or TaskFragment Embedding rules required!
            ruleController.setRules(emptySet())
            Log.d("LifecycleLog", "[SplitInitializer] Initialized Narrow Mode: No SplitRules required (pure native Activity stack)")
        } else {
            // Wide Mode:
            // Standard LTR Split: ListActivity (Primary, 35% left) <-> Content (Secondary, 65% right)
            val listComponent = ComponentName(context, ListActivity::class.java)
            val wildcardComponent = ComponentName("*", "*")

            val animParams = EmbeddingAnimationParams.Builder()
                .setAnimationBackground(EmbeddingAnimationBackground.DEFAULT)
                .setChangeAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setOpenAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .setCloseAnimation(EmbeddingAnimationParams.AnimationSpec.DEFAULT)
                .build()

            val ltrSplitAttributes = SplitAttributes.Builder()
                .setSplitType(SplitAttributes.SplitType.ratio(AppConfig.PRIMARY_DRAWER_RATIO))
                .setLayoutDirection(SplitAttributes.LayoutDirection.LEFT_TO_RIGHT)
                .setAnimationParams(animParams)
                .build()

            val splitRule = SplitPairRule.Builder(
                setOf(
                    SplitPairFilter(listComponent, wildcardComponent, null),
                    SplitPairFilter(listComponent, wildcardComponent, ACTION_SETTINGS_EMBED)
                )
            )
                .setMinWidthDp(0)
                .setMinHeightDp(0)
                .setMinSmallestWidthDp(0)
                .setDefaultSplitAttributes(ltrSplitAttributes)
                .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
                .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
                .setClearTop(false)
                .build()

            ruleController.setRules(setOf(splitRule))
            Log.d("LifecycleLog", "[SplitInitializer] Initialized Wide Mode (Standard LTR Split ListActivity 35% <-> Content 65%)")
        }

        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
