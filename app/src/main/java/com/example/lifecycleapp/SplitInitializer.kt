package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.startup.Initializer
import androidx.window.embedding.ActivityFilter
import androidx.window.embedding.RuleController
import androidx.window.embedding.SplitAttributes
import androidx.window.embedding.SplitPairFilter
import androidx.window.embedding.SplitPairRule
import androidx.window.embedding.SplitPlaceholderRule
import androidx.window.embedding.SplitRule

class SplitInitializer : Initializer<RuleController> {

    override fun create(context: Context): RuleController {
        val primaryComponent = ComponentName(context, PrimaryActivity::class.java)
        // Match any activity from any package/module for cross-app embedding

        val filters = setOf(
            SplitPairFilter(primaryComponent, ComponentName("com.example.carsettings.light", "com.example.feature.light.LightSettingsActivity"), null),
            SplitPairFilter(primaryComponent, ComponentName("com.example.carsettings.sound", "com.example.feature.sound.SoundSettingsActivity"), null),
            SplitPairFilter(primaryComponent, ComponentName("com.example.carsettings.display", "com.example.feature.display.DisplaySettingsActivity"), null),
            SplitPairFilter(primaryComponent, ComponentName(context, GenericSettingsActivity::class.java), null)
        )

        val defaultSplitAttributes = SplitAttributes.Builder()
            .setSplitType(SplitAttributes.SplitType.ratio(0.35f))
            .build()

        val splitPairRule = SplitPairRule.Builder(filters)
            .setMinWidthDp(600)
            .setMinHeightDp(0)
            .setMinSmallestWidthDp(400)
            .setDefaultSplitAttributes(defaultSplitAttributes)
            .setFinishPrimaryWithSecondary(SplitRule.FinishBehavior.NEVER)
            .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ALWAYS)
            .setClearTop(true)
            .build()

        val ruleController = RuleController.getInstance(context)
        ruleController.setRules(setOf(splitPairRule))
        Log.d("LifecycleLog", "[SplitInitializer] Initialized SplitPairRule with cross-app wildcard embedding")
        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
