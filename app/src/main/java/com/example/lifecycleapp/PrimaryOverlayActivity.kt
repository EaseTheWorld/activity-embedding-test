package com.example.lifecycleapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayController
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity

/**
 * Narrow Mode Pinned Overlay Menu Activity (`Theme.LifecycleApp.Translucent` - Translucent).
 *
 * Responsibilities:
 * 1. Exclusively launched by `HomeActivity` in Narrow Mode as an Always-On-Top Pinned Overlay
 *    (`setOverlayCreateParams` + `shouldAssociateWithLaunchingActivity = false`).
 * 2. Toggles between a collapsed `80dp × 80dp` floating `MENU` button and an expanded `35%` drawer
 *    via `OverlayController.updateOverlayAttributes`.
 * 3. Delegates category launches to `HomeActivity.launchCategoryInTask()` so content activities
 *    stack inside `HomeActivity`'s single fullscreen `ExpandedTaskFragment` underneath this overlay.
 */
class PrimaryOverlayActivity : BaseCategoryMenuActivity() {

    private lateinit var layoutCollapsed: View
    private lateinit var layoutExpanded: View
    private lateinit var btnMore: View
    private lateinit var btnCollapse: View
    private var isDrawerExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDrawerExpanded = false
        SplitInitializer.isOverlayDrawerExpanded = false

        setContentView(R.layout.activity_primary_overlay)

        layoutCollapsed = findViewById(R.id.layoutCollapsed)
        layoutExpanded = findViewById(R.id.layoutExpanded)
        btnMore = findViewById(R.id.btnMore)
        btnCollapse = findViewById(R.id.btnCollapse)

        btnMore.setOnClickListener {
            Log.d(tag, "[$activityName] 'MENU' button clicked -> expanding overlay drawer to 35%")
            setDrawerExpanded(true, "MenuButtonClicked")
        }

        btnCollapse.setOnClickListener {
            Log.d(tag, "[$activityName] 'Collapse' button clicked -> collapsing overlay to 80dp button")
            setDrawerExpanded(false, "CollapseButtonClicked")
        }

        updateOverlayViews()
        initCategoryMenuViews()

        if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            window.decorView.post {
                handleIntent(intent)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawerExpanded) {
                    setDrawerExpanded(false, "BackPressedCollapse")
                    return
                }
                navigateBackToHomeOrFinish()
            }
        })
    }

    private fun updateOverlayViews() {
        if (!::layoutCollapsed.isInitialized || !::layoutExpanded.isInitialized) return
        if (isDrawerExpanded) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            layoutCollapsed.visibility = View.GONE
            layoutExpanded.visibility = View.VISIBLE
        } else {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            layoutCollapsed.visibility = View.VISIBLE
            layoutExpanded.visibility = View.GONE
        }
    }

    private fun setDrawerExpanded(expanded: Boolean, source: String) {
        if (isDrawerExpanded != expanded) {
            isDrawerExpanded = expanded
            SplitInitializer.isOverlayDrawerExpanded = expanded
            Log.d(tag, "[$activityName] Overlay drawer state changed to isDrawerExpanded=$isDrawerExpanded via $source")
            updateOverlayViews()
            try {
                val bounds = SplitInitializer.computeOverlayBounds(this, expanded)
                OverlayController.getInstance(this).updateOverlayAttributes(
                    AppConfig.TAG_PRIMARY_OVERLAY,
                    OverlayAttributes(bounds)
                )
                Log.d(tag, "[$activityName] Updated overlay bounds: isExpanded=$expanded, bounds=$bounds")
            } catch (e: Exception) {
                Log.e(tag, "[$activityName] Failed to update overlay attributes: $e", e)
            }
        }
    }

    override fun onBeforeCategoryLaunch() {
        setDrawerExpanded(false, "CategoryLaunch")
    }

    override fun startContentActivity(targetIntent: Intent) {
        // Narrow Mode: Post to next frame so the overlay collapse (updateOverlayAttributes)
        // commits cleanly without BLASTSyncEngine contention with the new activity launch.
        window.decorView.post {
            HomeActivity.homeInstance?.launchCategoryInTask(targetIntent) ?: startActivity(targetIntent)
        }
    }
}
