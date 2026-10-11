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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayController
import androidx.window.embedding.SplitController
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

class PrimaryActivity : BaseLoggingActivity() {

    companion object {
        const val ACTION_CATEGORY_PROVIDER = "com.example.carsettings.CATEGORY_PROVIDER"
        const val EXTRA_IS_OVERLAY = "extra_is_overlay"

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
    private lateinit var layoutCollapsed: View
    private lateinit var layoutExpanded: View
    private lateinit var btnMore: View
    private lateinit var btnCollapse: View
    private var categories: MutableList<SettingCategory> = mutableListOf()
    private var categoryAdapter: CategoryAdapter? = null
    private val observedAuthorities = mutableSetOf<String>()
    private var isPrimaryActive: Boolean = false
    private var isNarrowOverlayMode: Boolean = false

    private fun updateLayoutVisibility() {
        if (!::layoutCollapsed.isInitialized || !::layoutExpanded.isInitialized) return
        if (!isNarrowOverlayMode) {
            // Wide Mode (Pure Split): always show expanded left pane without collapse button
            layoutCollapsed.visibility = View.GONE
            layoutExpanded.visibility = View.VISIBLE
            btnCollapse.visibility = View.GONE
            return
        }
        // Narrow Mode (Pinned Overlay): toggle between 80dp button and 35% drawer
        btnCollapse.visibility = View.VISIBLE
        if (isPrimaryActive) {
            layoutCollapsed.visibility = View.GONE
            layoutExpanded.visibility = View.VISIBLE
        } else {
            layoutCollapsed.visibility = View.VISIBLE
            layoutExpanded.visibility = View.GONE
        }
    }

    private fun updateActiveState(isActive: Boolean, source: String) {
        if (!isNarrowOverlayMode) {
            isPrimaryActive = true
            updateLayoutVisibility()
            return
        }
        if (isPrimaryActive != isActive) {
            isPrimaryActive = isActive
            SplitInitializer.isOverlayDrawerExpanded = isActive
            Log.d(tag, "[$activityName] Active state changed to $isPrimaryActive via $source")
            updateLayoutVisibility()
            updateOverlayWindowAttributes(isActive)
        }
    }

    private fun updateOverlayWindowAttributes(isExpanded: Boolean) {
        try {
            val bounds = SplitInitializer.computeOverlayBounds(this, isExpanded)
            OverlayController.getInstance(this).updateOverlayAttributes(
                AppConfig.TAG_PRIMARY_OVERLAY,
                OverlayAttributes(bounds)
            )
            Log.d(tag, "[$activityName] Updated overlay bounds: isExpanded=$isExpanded, bounds=$bounds")
        } catch (e: Exception) {
            Log.e(tag, "[$activityName] Failed to update overlay attributes: $e", e)
        }
    }

    private val categoryObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d(tag, "[$activityName] Category ContentObserver triggered onChange: $uri")
            refreshCategories()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isNarrowOverlayMode = AppConfig.isOverlayMode(this)
        val isOverlayExtra = intent?.getBooleanExtra(EXTRA_IS_OVERLAY, false) == true

        // Explicit Mode Branching at Entry:
        // 1. Narrow Mode: If launched as initial entry (not yet in Pinned Overlay), bootstrap HomeActivity
        //    as the fullscreen Task root so HomeActivity spawns PrimaryActivity as a Pinned Overlay.
        if (isNarrowOverlayMode && !isOverlayExtra) {
            Log.i(tag, "[$activityName] Narrow Mode entry -> bootstrapping HomeActivity as Task root")
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                if (intent?.data != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_DEEPLINK_URI, intent.data.toString())
                }
                if (intent?.action != null && intent.action != Intent.ACTION_MAIN) {
                    putExtra(HomeActivity.EXTRA_INITIAL_DEEPLINK_ACTION, intent.action)
                }
                if (intent?.extras != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_DEEPLINK_EXTRAS, intent.extras)
                }
            }
            startActivity(homeIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
            return
        }

        isPrimaryActive = !isNarrowOverlayMode
        SplitInitializer.isOverlayDrawerExpanded = isPrimaryActive

        setContentView(R.layout.activity_primary)

        layoutCollapsed = findViewById(R.id.layoutCollapsed)
        layoutExpanded = findViewById(R.id.layoutExpanded)
        btnMore = findViewById(R.id.btnMore)
        btnCollapse = findViewById(R.id.btnCollapse)

        btnMore.setOnClickListener {
            Log.d(tag, "[$activityName] '... More' button clicked -> expanding to 35%")
            updateActiveState(true, "MoreButtonClicked")
        }

        btnCollapse.setOnClickListener {
            Log.d(tag, "[$activityName] 'Collapse' button clicked -> collapsing")
            updateActiveState(false, "CollapseButtonClicked")
        }

        updateLayoutVisibility()

        val extensionVersion = androidx.window.WindowSdkExtensions.getInstance().extensionVersion
        Log.i(tag, "[$activityName] WindowSdkExtensions.extensionVersion = $extensionVersion (isNarrowOverlayMode=$isNarrowOverlayMode)")

        tvDiscoveryStatus = findViewById(R.id.tvDiscoveryStatus)
        listViewCategories = findViewById(R.id.listViewCategories)

        categories = discoverCategories().toMutableList()
        tvDiscoveryStatus.text = getString(R.string.discovery_status_format, categories.size)

        categoryAdapter = CategoryAdapter(this, categories)
        listViewCategories.adapter = categoryAdapter

        listViewCategories.setOnItemClickListener { _, _, position, _ ->
            categoryAdapter?.selectedPosition = position
            categoryAdapter?.notifyDataSetChanged()
            updateActiveState(false, "ListItemSelected")
            val selectedCategory = categories[position]
            launchCategory(selectedCategory)
        }

        // 2. Wide Mode: PrimaryActivity is the Primary of SplitPairRule -> launch HomeActivity into SecondaryTaskFragment
        if (!isNarrowOverlayMode && savedInstanceState == null) {
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            Log.i(tag, "[$activityName] Wide Mode -> launching HomeActivity into SecondaryTaskFragment via SplitPairRule")
            startActivity(homeIntent)
        }

        if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            window.decorView.post {
                handleIntent(intent)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNarrowOverlayMode && isPrimaryActive) {
                    updateActiveState(false, "BackPressedCollapse")
                    return
                }
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
        })

        HomeNavigationBridge.onHomeRevealed = {
            runOnUiThread {
                if (categoryAdapter?.selectedPosition != 0) {
                    Log.d(tag, "[$activityName] Home revealed -> updating selectedPosition to 0")
                    categoryAdapter?.selectedPosition = 0
                    categoryAdapter?.notifyDataSetChanged()
                    listViewCategories.smoothScrollToPosition(0)
                }
            }
        }

        HomeNavigationBridge.onCategorySelected = { categoryId ->
            runOnUiThread {
                val matchedIndex = categories.indexOfFirst { it.id.equals(categoryId, ignoreCase = true) }
                if (matchedIndex >= 0 && categoryAdapter?.selectedPosition != matchedIndex) {
                    Log.d(tag, "[$activityName] Category selected from Home search -> updating selectedPosition to $matchedIndex ($categoryId)")
                    categoryAdapter?.selectedPosition = matchedIndex
                    categoryAdapter?.notifyDataSetChanged()
                    listViewCategories.smoothScrollToPosition(matchedIndex)
                    updateActiveState(false, "HomeNavigationBridge")
                }
            }
        }
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        Log.d(tag, "[$activityName] onTopResumedActivityChanged(isTopResumedActivity = $isTopResumedActivity)")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(tag, "[$activityName] onWindowFocusChanged(hasFocus = $hasFocus)")
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        HomeNavigationBridge.onHomeRevealed = null
        HomeNavigationBridge.onCategorySelected = null
        try {
            contentResolver.unregisterContentObserver(categoryObserver)
            Log.d(tag, "[$activityName] Unregistered categoryObserver")
        } catch (e: Exception) {
            Log.w(tag, "[$activityName] Error unregistering categoryObserver: $e")
        }
        try {
            SplitController.getInstance(this).clearSplitAttributesCalculator()
        } catch (e: Exception) {
            Log.w(tag, "[$activityName] Error clearing SplitAttributesCalculator: $e")
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
        val sortedCategories = (listOf(homeCategory, dashboardCategory) + discovered).sortedBy { it.order }

        return sortedCategories
    }

    private fun startContentActivity(targetIntent: Intent) {
        if (isNarrowOverlayMode) {
            // Narrow Mode: Launch in the underlying fullscreen Task via HomeActivity
            HomeActivity.homeInstance?.launchCategoryInTask(targetIntent) ?: startActivity(targetIntent)
        } else {
            // Wide Mode: PrimaryActivity is the SplitPairRule Primary, so startActivity places it in SecondaryTaskFragment
            startActivity(targetIntent)
        }
    }

    private fun launchCategory(category: SettingCategory, deepLinkUri: Uri? = null, extras: Bundle? = null) {
        updateActiveState(false, "CategoryLaunch")

        if (category.id == "home") {
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.parse("myapp://navigate/home")
            }
            Log.d(tag, "[$activityName] Navigating to HomeActivity (isNarrowOverlayMode=$isNarrowOverlayMode)")
            startContentActivity(homeIntent)
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
            targetIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
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

            Log.d(tag, "[$activityName] Launching category ${category.title} (isNarrowOverlayMode=$isNarrowOverlayMode)")
            startContentActivity(targetIntent)
        } else {
            Log.w(tag, "[$activityName] No target intent for category ${category.title}")
        }
    }

    private fun handleIntent(intent: Intent?) {
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

        if (categoryId.equals("light", ignoreCase = true)) {
            val extIntent = Intent(ACTION_SETTINGS_EMBED).apply {
                setClassName("com.example.carsettings.light", "com.example.feature.light.LightSettingsActivity")
                data = uri
                if (extras != null) putExtras(extras)
            }
            try {
                startContentActivity(extIntent)
                Log.d(tag, "[$activityName] Launched external light category fallback via $uri")
            } catch (e: Exception) {
                Log.e(tag, "[$activityName] Failed to launch external light activity: $e")
            }
        } else {
            val settingsIntent = Intent(this, GenericSettingsActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                data = uri
                putExtra(GenericSettingsActivity.EXTRA_CATEGORY_ID, categoryId)
                if (extras != null) putExtras(extras)
            }
            startContentActivity(settingsIntent)
            Log.d(tag, "[$activityName] Launched GenericSettingsActivity fallback for $categoryId via $uri")
        }
    }
}

private class CategoryAdapter(
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

        // --- PATTERN A RESOLUTION ---
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
