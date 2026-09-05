package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat

data class SettingCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val order: Int,
    val targetAction: String?,
    val targetPackage: String?,
    val targetActivity: String?,
    val iconName: String?,
    val titleResId: Int = 0,
    val subtitleResId: Int = 0,
    val iconResId: Int = 0,
    val authority: String,
    val contentUri: Uri
)

class PrimaryActivity : BaseLoggingActivity() {

    companion object {
        const val ACTION_CATEGORY_PROVIDER = "com.example.carsettings.CATEGORY_PROVIDER"

        // Contract column names
        const val PATH_CATEGORY = "category"
        const val COLUMN_CATEGORY_ID = "category_id"
        const val COLUMN_CATEGORY_TITLE = "category_title"
        const val COLUMN_CATEGORY_SUBTITLE = "category_subtitle"
        const val COLUMN_CATEGORY_ORDER = "category_order"
        const val COLUMN_CATEGORY_TARGET_ACTION = "category_target_action"
        const val COLUMN_CATEGORY_TARGET_ACTIVITY = "category_target_activity"
        const val COLUMN_CATEGORY_ICON_NAME = "category_icon_name"
        const val COLUMN_CATEGORY_TITLE_RES_ID = "category_title_res_id"
        const val COLUMN_CATEGORY_SUBTITLE_RES_ID = "category_subtitle_res_id"
        const val COLUMN_CATEGORY_ICON_RES_ID = "category_icon_res_id"
    }

    private lateinit var tvDiscoveryStatus: TextView
    private lateinit var listViewCategories: ListView
    private var categories: MutableList<SettingCategory> = mutableListOf()
    private var categoryAdapter: CategoryAdapter? = null
    private val observedAuthorities = mutableSetOf<String>()

    private val categoryObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(tag, "[$activityName] Category ContentObserver triggered onChange: $uri")
            refreshCategories()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_primary)

        tvDiscoveryStatus = findViewById(R.id.tvDiscoveryStatus)
        listViewCategories = findViewById(R.id.listViewCategories)

        categories = discoverCategories().toMutableList()
        tvDiscoveryStatus.text = getString(R.string.discovery_status_format, categories.size)

        categoryAdapter = CategoryAdapter(this, categories)
        listViewCategories.adapter = categoryAdapter

        listViewCategories.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = categories[position]
            launchCategory(selectedCategory)
        }

        if (savedInstanceState == null) {
            if (intent?.action == "com.example.lifecycleapp.open") {
                handleIntent(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            contentResolver.unregisterContentObserver(categoryObserver)
            Log.d(tag, "[$activityName] Unregistered categoryObserver")
        } catch (e: Exception) {
            Log.w(tag, "[$activityName] Error unregistering categoryObserver: $e")
        }
    }

    private fun refreshCategories() {
        val updated = discoverCategories()
        categories.clear()
        categories.addAll(updated)
        tvDiscoveryStatus.text = getString(R.string.discovery_status_format, categories.size)
        categoryAdapter?.notifyDataSetChanged()
        Log.d(tag, "[$activityName] Categories refreshed dynamically: ${categories.size} items active")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun discoverCategories(): List<SettingCategory> {
        val discovered = mutableListOf<SettingCategory>()
        val discoveryIntent = Intent(ACTION_CATEGORY_PROVIDER)
        val providers: List<ResolveInfo> = packageManager.queryIntentContentProviders(discoveryIntent, 0)

        Log.d(tag, "[$activityName] queryIntentContentProviders found ${providers.size} providers matching $ACTION_CATEGORY_PROVIDER")

        for (resolveInfo in providers) {
            val authority = resolveInfo.providerInfo?.authority ?: continue
            val packageName = resolveInfo.providerInfo?.packageName
            val categoryUri = Uri.parse("content://$authority/$PATH_CATEGORY")
            if (observedAuthorities.add(authority)) {
                try {
                    contentResolver.registerContentObserver(categoryUri, true, categoryObserver)
                    Log.d(tag, "[$activityName] Registered categoryObserver on $categoryUri")
                } catch (e: Exception) {
                    Log.w(tag, "[$activityName] Failed to register observer on $categoryUri: $e")
                }
            }
            try {
                contentResolver.query(categoryUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_ID))
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_TITLE))
                        val subtitle = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_SUBTITLE))
                        val order = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_ORDER))
                        val targetAction = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_TARGET_ACTION))
                        val targetActivity = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_TARGET_ACTIVITY))
                        val iconName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY_ICON_NAME))

                        val titleResId = cursor.getColumnIndex(COLUMN_CATEGORY_TITLE_RES_ID).takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0
                        val subtitleResId = cursor.getColumnIndex(COLUMN_CATEGORY_SUBTITLE_RES_ID).takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0
                        val iconResId = cursor.getColumnIndex(COLUMN_CATEGORY_ICON_RES_ID).takeIf { it >= 0 }?.let { cursor.getInt(it) } ?: 0

                        val category = SettingCategory(
                            id = id,
                            title = title,
                            subtitle = subtitle,
                            order = order,
                            targetAction = targetAction,
                            targetPackage = packageName,
                            targetActivity = targetActivity,
                            iconName = iconName,
                            titleResId = titleResId,
                            subtitleResId = subtitleResId,
                            iconResId = iconResId,
                            authority = authority,
                            contentUri = categoryUri
                        )
                        discovered.add(category)
                        Log.d(tag, "[$activityName] Discovered category app: id=$id, title='$title', titleResId=0x${Integer.toHexString(titleResId)}, iconResId=0x${Integer.toHexString(iconResId)}, package='$packageName'")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "[$activityName] Failed to query category from $authority: $e")
            }
        }
        val sortedCategories = discovered.sortedBy { it.order }

        val ruleController = androidx.window.embedding.RuleController.getInstance(this)
        val primaryComponent = ComponentName(this, PrimaryActivity::class.java)
        for (cat in sortedCategories) {
            if (!cat.targetPackage.isNullOrEmpty() && !cat.targetActivity.isNullOrEmpty()) {
                val pairFilter = androidx.window.embedding.SplitPairFilter(
                    primaryComponent,
                    ComponentName(cat.targetPackage, cat.targetActivity),
                    null
                )
                val splitRule = androidx.window.embedding.SplitPairRule.Builder(setOf(pairFilter))
                    .setMinWidthDp(600)
                    .setMinHeightDp(0)
                    .setMinSmallestWidthDp(400)
                    .setDefaultSplitAttributes(
                        androidx.window.embedding.SplitAttributes.Builder()
                            .setSplitType(androidx.window.embedding.SplitAttributes.SplitType.ratio(0.35f))
                            .setLayoutDirection(androidx.window.embedding.SplitAttributes.LayoutDirection.LEFT_TO_RIGHT)
                            .build()
                    )
                    .setFinishPrimaryWithSecondary(androidx.window.embedding.SplitRule.FinishBehavior.NEVER)
                    .setFinishSecondaryWithPrimary(androidx.window.embedding.SplitRule.FinishBehavior.ALWAYS)
                    .setClearTop(true)
                    .build()
                ruleController.addRule(splitRule)
                Log.d(tag, "[$activityName] Registered dynamic SplitPairRule for ${cat.targetPackage}/${cat.targetActivity}")
            }
        }

        return sortedCategories
    }

    private fun launchCategory(category: SettingCategory) {
        val intent = when {
            !category.targetPackage.isNullOrEmpty() && !category.targetActivity.isNullOrEmpty() -> {
                Intent().setComponent(ComponentName(category.targetPackage, category.targetActivity))
            }
            !category.targetAction.isNullOrEmpty() -> {
                Intent(category.targetAction)
            }
            else -> null
        }

        if (intent != null) {
            intent.putExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID, category.id)
            intent.putExtra(GenericSettingsActivity.EXTRA_AUTHORITY, category.authority)
            intent.putExtra(GenericSettingsActivity.EXTRA_TITLE, category.title)
            Log.d(tag, "[$activityName] Launching category ${category.title} via $intent")
            startActivity(intent)
        } else {
            Log.w(tag, "[$activityName] No target intent for category ${category.title}")
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        Log.d(tag, "[$activityName] handleIntent: action=$action, extras=${intent.extras}")

        if (action == "com.example.lifecycleapp.open") {
            intent.action = null
            val categoryKey = intent.getStringExtra("category") ?: when (val idExtra = intent.extras?.get("id")) {
                1, "1" -> "light"
                2, "2" -> "sound"
                3, "3" -> "display"
                else -> null
            }

            if (categoryKey != null) {
                val matched = categories.find { it.id.equals(categoryKey, ignoreCase = true) }
                if (matched != null) {
                    launchCategory(matched)
                } else {
                    Log.w(tag, "[$activityName] Deeplink category key not found: $categoryKey")
                }
            }
        }
    }
}

private class CategoryAdapter(
    context: Context,
    private val categories: List<SettingCategory>
) : ArrayAdapter<SettingCategory>(context, 0, categories) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
        val category = categories[position]

        val ivIcon = view.findViewById<ImageView>(R.id.ivCategoryIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvCategoryTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvCategorySubtitle)
        val tvAuthority = view.findViewById<TextView>(R.id.tvCategoryAuthority)
        val tvResInfo = view.findViewById<TextView>(R.id.tvResInfo)

        // --- PATTERN A RESOLUTION ---
        val featurePackage = category.targetPackage ?: ""
        val featureResources = try {
            if (featurePackage.isNotEmpty()) {
                context.packageManager.getResourcesForApplication(featurePackage)
            } else null
        } catch (e: Exception) {
            null
        }

        // 1. Resolve Localized String via Pattern A (fallback to Cursor string if missing)
        val resolvedTitle = if (featureResources != null && category.titleResId != 0) {
            try {
                featureResources.getString(category.titleResId)
            } catch (e: Exception) {
                category.title
            }
        } else {
            category.title
        }

        val resolvedSubtitle = if (featureResources != null && category.subtitleResId != 0) {
            try {
                featureResources.getString(category.subtitleResId)
            } catch (e: Exception) {
                category.subtitle
            }
        } else {
            category.subtitle
        }

        // 2. Resolve Drawable Icon via Pattern A
        val resolvedIcon = if (category.iconResId != 0 && featurePackage.isNotEmpty()) {
            try {
                val foreignContext = context.createPackageContext(featurePackage, Context.CONTEXT_RESTRICTED)
                AppCompatResources.getDrawable(foreignContext, category.iconResId)
            } catch (e: Exception) {
                if (featureResources != null) {
                    try {
                        ResourcesCompat.getDrawable(featureResources, category.iconResId, null)
                    } catch (e2: Exception) { null }
                } else null
            }
        } else null

        tvTitle.text = resolvedTitle
        tvSubtitle.text = resolvedSubtitle
        tvAuthority.text = "content://${category.authority}"

        if (resolvedIcon != null) {
            ivIcon.setImageDrawable(resolvedIcon)
            ivIcon.visibility = View.VISIBLE
        } else {
            ivIcon.visibility = View.GONE
        }

        tvResInfo.text = "Pattern A IDs: title=0x${Integer.toHexString(category.titleResId)}, icon=0x${Integer.toHexString(category.iconResId)} [Pkg: $featurePackage]"

        return view
    }
}
