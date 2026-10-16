package com.example.lifecycleapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.window.embedding.OverlayAttributes
import androidx.window.embedding.OverlayController
import com.example.core.item.AppConfig

/**
 * Narrow Mode Pinned Overlay Menu Activity (`Theme.LifecycleApp.Translucent` - Translucent).
 *
 * Responsibilities:
 * 1. Exclusively launched by `HomeActivity` in Narrow Mode as an Always-On-Top Pinned Overlay
 *    (`setOverlayCreateParams` + `shouldAssociateWithLaunchingActivity = false`).
 * 2. Toggles between a collapsed `80dp × 80dp` floating `MENU` button and an expanded `35%` drawer
 *    via `OverlayController.updateOverlayAttributes`.
 * 3. Uses [WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL] so touches outside the overlay bounds
 *    pass 100% through to Secondary while keeping valid window focus (`mCurrentFocus != null`, zero ANR)
 *    and routing Back key events to the active Secondary stack (`HomeActivity`, `GenericSettingsActivity`,
 *    `SubSettingActivity`, or `CategoryHostActivity` for external app categories).
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

        // Allow pointer events outside [0, 48][160, 208] to pass through to Secondary
        // while keeping this window focusable (preventing mCurrentFocus = null ANRs).
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

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

        updateOverlayViews(animate = false)
        initCategoryMenuViews()

        if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            window.decorView.post {
                handleIntent(intent)
            }
        }

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawerExpanded) {
                    setDrawerExpanded(false, "BackPressedCollapse")
                    return
                }
                val topSecondary = LifecycleApp.getTopSecondaryActivity()
                if (topSecondary != null) {
                    Log.d(tag, "[$activityName] Forwarding Back key to top secondary activity: ${topSecondary.javaClass.simpleName}")
                    topSecondary.onBackPressedDispatcher.onBackPressed()
                } else {
                    Log.d(tag, "[$activityName] No in-process secondary activity found, fallback to navigateBackToHomeOrFinish()")
                    navigateBackToHomeOrFinish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private lateinit var backCallback: OnBackPressedCallback

    private fun applyOverlayBounds(expanded: Boolean) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val updated = OverlayBoundsHelper.updateOverlayBounds(
            this,
            AppConfig.TAG_PRIMARY_OVERLAY,
            expanded
        )
        if (!updated) {
            try {
                val bounds = SplitInitializer.computeOverlayBounds(this, expanded)
                OverlayController.getInstance(this).updateOverlayAttributes(
                    AppConfig.TAG_PRIMARY_OVERLAY,
                    OverlayAttributes(bounds)
                )
                Log.d(tag, "[$activityName] Fallback overlay bounds: isExpanded=$expanded, bounds=$bounds")
            } catch (e: Exception) {
                Log.e(tag, "[$activityName] Failed to update overlay attributes: $e", e)
            }
        }
    }

    private fun updateOverlayViews(animate: Boolean = false) {
        if (!::layoutCollapsed.isInitialized || !::layoutExpanded.isInitialized) return

        if (!animate) {
            layoutCollapsed.animate().cancel()
            layoutExpanded.animate().cancel()
            if (isDrawerExpanded) {
                layoutCollapsed.visibility = View.GONE
                layoutCollapsed.alpha = 0f
                layoutExpanded.visibility = View.VISIBLE
                layoutExpanded.alpha = 1f
                layoutExpanded.translationX = 0f
            } else {
                layoutCollapsed.visibility = View.VISIBLE
                layoutCollapsed.alpha = 1f
                layoutCollapsed.scaleX = 1f
                layoutCollapsed.scaleY = 1f
                layoutExpanded.visibility = View.GONE
                layoutExpanded.alpha = 0f
            }
            applyOverlayBounds(isDrawerExpanded)
            return
        }

        val duration = 250L
        if (isDrawerExpanded) {
            // Collapsed -> Expanded: Expand window bounds first, then animate drawer in
            applyOverlayBounds(true)

            // 1. Fade out & scale down the collapsed button
            layoutCollapsed.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(150L)
                .withEndAction {
                    layoutCollapsed.visibility = View.GONE
                }
                .start()

            // 2. Slide in from left & fade in the drawer
            layoutExpanded.visibility = View.VISIBLE
            layoutExpanded.alpha = 0f
            layoutExpanded.translationX = -layoutExpanded.width.toFloat().coerceAtLeast(300f)
            layoutExpanded.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            // Expanded -> Collapsed: Animate drawer out first, then shrink window bounds
            // 1. Slide out to left & fade out the drawer
            val targetTranslationX = -layoutExpanded.width.toFloat().coerceAtLeast(300f)
            layoutExpanded.animate()
                .translationX(targetTranslationX)
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    layoutExpanded.visibility = View.GONE
                    applyOverlayBounds(false)
                }
                .start()

            // 2. Fade in & scale up the collapsed button
            layoutCollapsed.visibility = View.VISIBLE
            layoutCollapsed.alpha = 0f
            layoutCollapsed.scaleX = 0.6f
            layoutCollapsed.scaleY = 0.6f
            layoutCollapsed.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(100L)
                .setDuration(duration)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun setDrawerExpanded(expanded: Boolean, source: String, animate: Boolean = true) {
        if (isDrawerExpanded != expanded) {
            isDrawerExpanded = expanded
            SplitInitializer.isOverlayDrawerExpanded = expanded
            Log.d(tag, "[$activityName] Overlay drawer state changed to isDrawerExpanded=$isDrawerExpanded via $source")
            updateOverlayViews(animate = animate)
        }
    }

    override fun onResume() {
        super.onResume()
        applyOverlayBounds(isDrawerExpanded)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOverlayBounds(isDrawerExpanded)
    }

    override fun onHomeRevealed() {
        super.onHomeRevealed()
        if (isDrawerExpanded) {
            setDrawerExpanded(false, "HomeRevealed", animate = false)
        }
    }

    override fun onBeforeCategoryLaunch() {
        setDrawerExpanded(false, "CategoryLaunch", animate = true)
    }
}
