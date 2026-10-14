package com.example.lifecycleapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
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
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import com.example.feature.home.HomeNavigationBridge

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
    val contentUri: Uri? = null
)

/**
 * Shared base activity for category discovery, Pattern A resource resolution,
 * ListView binding, HomeNavigationBridge synchronization, and deeplink routing.
 *
 * Subclasses:
 * - [PrimaryActivity]: Wide Mode fixed left Split pane (Opaque theme, static layout)
 * - [PrimaryOverlayActivity]: Narrow Mode Pinned Overlay (Translucent theme, collapsible 80dp button <-> 35% drawer)
 */
abstract class BaseCategoryMenuActivity : BaseLoggingActivity() {

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

    protected lateinit var tvDiscoveryStatus: TextView
    protected lateinit var listViewCategories: ListView
    protected var categories: MutableList<SettingCategory> = mutableListOf()
    protected var categoryAdapter: CategoryAdapter? = null
    private val observedAuthorities = mutableSetOf<String>()

    /**
     * Optional hook invoked right before launching a category (e.g., [PrimaryOverlayActivity] collapses the drawer).
     */
    protected open fun onBeforeCategoryLaunch() {}

    private val categoryObserver = object : android.database.ContentObserver(
        android.os.Handler(android.os.Looper.getMainLooper())
    ) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(tag, "[$activityName] Category ContentObserver triggered onChange: $uri")
            refreshCategories()
        }
    }

    private val homeRevealedListener: () -> Unit = {
        runOnUiThread {
            if (categoryAdapter?.selectedPosition != 0) {
                Log.d(tag, "[$activityName] Home revealed -> updating selectedPosition to 0")
                categoryAdapter?.selectedPosition = 0
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(0)
            }
        }
    }

    private val categorySelectedListener: (String) -> Unit = { categoryId ->
        runOnUiThread {
            val matchedIndex = categories.indexOfFirst { it.id.equals(categoryId, ignoreCase = true) }
            if (matchedIndex >= 0 && categoryAdapter?.selectedPosition != matchedIndex) {
                Log.d(tag, "[$activityName] Category selected from Home search -> updating selectedPosition to $matchedIndex ($categoryId)")
                categoryAdapter?.selectedPosition = matchedIndex
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(matchedIndex)
                onBeforeCategoryLaunch()
            }
        }
    }

    protected fun initCategoryMenuViews() {
        tvDiscoveryStatus = findViewById(R.id.tvDiscoveryStatus)
        listViewCategories = findViewById(R.id.listViewCategories)

        categories = discoverCategories().toMutableList()
        tvDiscoveryStatus.text = getString(R.string.discovery_status_format, categories.size)

        categoryAdapter = CategoryAdapter(this, categories)
        listViewCategories.adapter = categoryAdapter

        listViewCategories.setOnItemClickListener { _, _, position, _ ->
            categoryAdapter?.selectedPosition = position
            categoryAdapter?.notifyDataSetChanged()
            onBeforeCategoryLaunch()
            val selectedCategory = categories[position]
            launchCategory(selectedCategory)
        }

        HomeNavigationBridge.registerHomeRevealedListener(homeRevealedListener)
        HomeNavigationBridge.registerCategorySelectedListener(categorySelectedListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        HomeNavigationBridge.unregisterHomeRevealedListener(homeRevealedListener)
        HomeNavigationBridge.unregisterCategorySelectedListener(categorySelectedListener)
        try {
            contentResolver.unregisterContentObserver(categoryObserver)
            Log.d(tag, "[$activityName] Unregistered categoryObserver")
        } catch (e: Exception) {
            Log.w(tag, "[$activityName] Error unregistering categoryObserver: $e")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    protected fun navigateBackToHomeOrFinish() {
        val currentPos = categoryAdapter?.selectedPosition ?: 0
        if (currentPos != 0) {
            categoryAdapter?.selectedPosition = 0
            categoryAdapter?.notifyDataSetChanged()
            categories.firstOrNull()?.let { homeCat ->
                launchCategory(homeCat)
            }
        } else {
            finishAffinity()
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

    protected fun discoverCategories(): List<SettingCategory> {
        val discovered = mutableListOf<SettingCategory>()
        val discoveryIntent = Intent(ACTION_CATEGORY_PROVIDER)
        val providers: List<ResolveInfo> = packageManager.queryIntentContentProviders(discoveryIntent, 0)

        Log.d(tag, "[$activityName] queryIntentContentProviders found ${providers.size} providers matching $ACTION_CATEGORY_PROVIDER")

        for (resolveInfo in providers) {
            val authority = resolveInfo.providerInfo?.authority ?: continue
            val packageName = resolveInfo.providerInfo?.packageName
            val categoryUri = Uri.parse("content://$authority/$PATH_CATEGORY")
            try {
                contentResolver.query(categoryUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        if (observedAuthorities.add(authority)) {
                            try {
                                contentResolver.registerContentObserver(categoryUri, true, categoryObserver)
                                Log.d(tag, "[$activityName] Registered categoryObserver on $categoryUri")
                            } catch (e: Exception) {
                                Log.w(tag, "[$activityName] Failed to register observer on $categoryUri: $e")
                            }
                        }
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
        val homeCategory = SettingCategory(
            id = "home",
            title = "Home",
            subtitle = "Search & Quick access",
            order = -10,
            targetAction = "com.example.carsettings.HOME",
            targetPackage = packageName,
            targetActivity = "com.example.feature.home.HomeActivity",
            iconName = "ic_feature_home",
            authority = "local",
            contentUri = Uri.EMPTY
        )
        val dashboardCategory = SettingCategory(
            id = "dashboard",
            title = "Quick Controls & All",
            subtitle = "Recent items and category overview",
            order = -1,
            targetAction = "com.example.carsettings.DASHBOARD",
            targetPackage = packageName,
            targetActivity = "com.example.lifecycleapp.GenericSettingsActivity",
            iconName = null,
            authority = "local",
            contentUri = Uri.EMPTY
        )
        return (listOf(homeCategory, dashboardCategory) + discovered).sortedBy { it.order }
    }

    protected fun launchCategory(category: SettingCategory, deepLinkUri: Uri? = null, extras: Bundle? = null) {
        onBeforeCategoryLaunch()

        if (category.id == "home") {
            Log.d(tag, "[$activityName] Navigating to HomeActivity -> HomeNavigationBridge.notifyDismissCategoryStacks()")
            window.decorView.post {
                HomeNavigationBridge.notifyDismissCategoryStacks()
            }
            return
        }

        val targetIntent = when {
            !category.targetPackage.isNullOrEmpty() && !category.targetActivity.isNullOrEmpty() -> {
                Intent().setComponent(ComponentName(category.targetPackage, category.targetActivity))
            }
            !category.targetAction.isNullOrEmpty() -> {
                Intent(category.targetAction)
            }
            else -> null
        }

        if (targetIntent != null) {
            targetIntent.action = ACTION_SETTINGS_EMBED
            targetIntent.putExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID, category.id)
            targetIntent.putExtra(GenericSettingsActivity.EXTRA_AUTHORITY, category.authority)
            targetIntent.putExtra(GenericSettingsActivity.EXTRA_TITLE, category.title)
            if (extras != null) {
                targetIntent.putExtras(extras)
            }
            val uriToPass = deepLinkUri ?: when (category.id) {
                "dashboard" -> Uri.parse("myapp://navigate/dashboard")
                else -> Uri.parse("myapp://navigate/${category.id}")
            }
            targetIntent.data = uriToPass

            Log.d(tag, "[$activityName] Launching category ${category.title} via HomeNavigationBridge.launchCategory()")
            window.decorView.post {
                HomeNavigationBridge.launchCategory(this, targetIntent)
            }
        } else {
            Log.w(tag, "[$activityName] No target intent for category ${category.title}")
        }
    }

    protected fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val dataUri = intent.data
        Log.d(tag, "[$activityName] handleIntent: action=$action, data=$dataUri, extras=${intent.extras}")

        if (dataUri != null && dataUri.scheme == "myapp" && dataUri.host == "navigate") {
            handleDeepLink(dataUri, intent.extras)
            return
        }

        if (action == "com.example.lifecycleapp.open") {
            intent.action = null
            @Suppress("DEPRECATION")
            val categoryKey = intent.getStringExtra("category") ?: when (intent.extras?.get("id")) {
                1, "1" -> "light"
                2, "2" -> "sound"
                3, "3" -> "display"
                else -> null
            }

            if (categoryKey != null) {
                val matched = categories.find { it.id.equals(categoryKey, ignoreCase = true) }
                if (matched != null) {
                    launchCategory(matched, extras = intent.extras)
                } else {
                    Log.w(tag, "[$activityName] Deeplink category key not found: $categoryKey")
                }
            }
        }
    }

    private fun handleDeepLink(uri: Uri, extras: Bundle? = null) {
        val segments = uri.pathSegments
        val categoryId = when {
            segments.isEmpty() -> "home"
            segments[0].equals("home", ignoreCase = true) -> "home"
            segments[0].equals("dashboard", ignoreCase = true) -> "dashboard"
            else -> segments[0]
        }

        Log.d(tag, "[$activityName] handleDeepLink: uri=$uri, resolved categoryId=$categoryId, extras=$extras")

        val matchedIndex = categories.indexOfFirst { it.id.equals(categoryId, ignoreCase = true) }
        if (matchedIndex >= 0) {
            categoryAdapter?.selectedPosition = matchedIndex
            categoryAdapter?.notifyDataSetChanged()
            listViewCategories.smoothScrollToPosition(matchedIndex)
            launchCategory(categories[matchedIndex], deepLinkUri = uri, extras = extras)
            return
        }

        val fallbackIntent = if (categoryId.equals("light", ignoreCase = true)) {
            Intent(ACTION_SETTINGS_EMBED).apply {
                setClassName("com.example.carsettings.light", "com.example.feature.light.LightSettingsActivity")
                data = uri
                if (extras != null) putExtras(extras)
            }
        } else {
            Intent(this, GenericSettingsActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                data = uri
                putExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID, categoryId)
                if (extras != null) putExtras(extras)
            }
        }
        window.decorView.post {
            HomeNavigationBridge.launchCategory(this, fallbackIntent)
        }
    }
}

class CategoryAdapter(
    context: Context,
    private val categories: List<SettingCategory>
) : ArrayAdapter<SettingCategory>(context, 0, categories) {

    var selectedPosition: Int = 0

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
        val category = categories[position]

        if (position == selectedPosition) {
            view.setBackgroundColor(Color.parseColor("#E8DEF8"))
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }

        val ivIcon = view.findViewById<ImageView>(R.id.ivCategoryIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvCategoryTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvCategorySubtitle)
        val tvAuthority = view.findViewById<TextView>(R.id.tvCategoryAuthority)
        val tvResInfo = view.findViewById<TextView>(R.id.tvResInfo)

        val featurePackage = category.targetPackage ?: ""
        val featureResources = try {
            if (featurePackage.isNotEmpty()) {
                context.packageManager.getResourcesForApplication(featurePackage)
            } else null
        } catch (e: Exception) {
            null
        }

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
