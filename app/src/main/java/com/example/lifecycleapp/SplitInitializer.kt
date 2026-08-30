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
        val allSecondaryComponent = ComponentName(context.packageName, "*")

        val filters = setOf(
            SplitPairFilter(primaryComponent, allSecondaryComponent, null)
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
            .setFinishSecondaryWithPrimary(SplitRule.FinishBehavior.ADJACENT)
            .setClearTop(true)
            .build()

        val placeholderFilters = setOf(ActivityFilter(primaryComponent, null))
        val placeholderIntent = Intent(context, Secondary1Activity::class.java)
        val placeholderRule = SplitPlaceholderRule.Builder(placeholderFilters, placeholderIntent)
            .setMinWidthDp(600)
            .setMinHeightDp(0)
            .setMinSmallestWidthDp(400)
            .setDefaultSplitAttributes(defaultSplitAttributes)
            .setFinishPrimaryWithPlaceholder(SplitRule.FinishBehavior.ALWAYS)
            .build()

        val ruleController = RuleController.getInstance(context)
        ruleController.setRules(setOf(splitPairRule, placeholderRule))
        Log.d("LifecycleLog", "[SplitInitializer] Initialized rules with androidx.startup.Initializer")
        return ruleController
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
