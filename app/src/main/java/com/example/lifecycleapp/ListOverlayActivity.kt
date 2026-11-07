package com.example.lifecycleapp

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
 * 3. Uses `WindowContainerTransaction.requestFocusOnTaskFragment` to shift WindowManager focus
 *    between the overlay drawer (when expanded) and the content container (when collapsed).
 */
class ListOverlayActivity : BaseCategoryMenuActivity() {

    private lateinit var layoutCollapsed: View
    private lateinit var layoutExpanded: View
    private lateinit var btnMore: View
    private lateinit var btnCollapse: View
    private var isDrawerExpanded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDrawerExpanded = false
        SplitInitializer.isOverlayDrawerExpanded = false

        setContentView(R.layout.activity_list_overlay)

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

        val initialCategoryId = intent?.getStringExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID)
        if (!initialCategoryId.isNullOrEmpty()) {
            val matchedIndex = categories.indexOfFirst { it.id.equals(initialCategoryId, ignoreCase = true) }
            if (matchedIndex >= 0) {
                categoryAdapter?.selectedPosition = matchedIndex
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(matchedIndex)
            }
            onBeforeCategoryLaunch()
        } else if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            handleIntent(intent)
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
                } else if (com.example.feature.home.CategoryStackManager.getActiveCategoryToken() != null) {
                    Log.d(tag, "[$activityName] External category active, dismissing via CategoryStackManager")
                    com.example.feature.home.CategoryStackManager.dismissCurrentCategory(this@ListOverlayActivity)
                    categoryAdapter?.selectedPosition = 0
                    categoryAdapter?.notifyDataSetChanged()
                    listViewCategories.smoothScrollToPosition(0)
                } else {
                    Log.d(tag, "[$activityName] No category stack active, fallback to navigateBackToHomeOrFinish()")
                    navigateBackToHomeOrFinish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    fun ensureSecondaryFocus() {
        if (isFinishing || isDrawerExpanded) return
        OverlayBoundsHelper.requestFocusOnSecondary(this)
        window.decorView.postDelayed({
            if (!isFinishing && !isDrawerExpanded) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 50L)
        window.decorView.postDelayed({
            if (!isFinishing && !isDrawerExpanded) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 200L)
        window.decorView.postDelayed({
            if (!isFinishing && !isDrawerExpanded) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 500L)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isDrawerExpanded && !isFinishing) {
            window.decorView.postDelayed({
                if (hasWindowFocus() && !isDrawerExpanded && !isFinishing) {
                    ensureSecondaryFocus()
                }
            }, 100L)
        }
    }

    private lateinit var backCallback: OnBackPressedCallback

    private fun applyOverlayBounds(expanded: Boolean) {
        val updated = OverlayBoundsHelper.updateOverlayBounds(
            this,
            AppConfig.TAG_PRIMARY_OVERLAY,
            expanded
        )
        if (!updated) {
            try {
                val overlayController = OverlayController.getInstance(this)
                val bounds = SplitInitializer.computeOverlayBounds(this, expanded)
                overlayController.updateOverlayAttributes(
                    AppConfig.TAG_PRIMARY_OVERLAY,
                    OverlayAttributes(bounds)
                )
                Log.d(tag, "[$activityName] applyOverlayBounds (Fallback Controller): expanded=$expanded, bounds=$bounds")
            } catch (e: Exception) {
                Log.e(tag, "[$activityName] Failed to update overlay attributes: $e", e)
            }
        }
    }

    fun setDrawerExpanded(expanded: Boolean, reason: String = "Unknown") {
        if (isDrawerExpanded == expanded) return
        isDrawerExpanded = expanded
        SplitInitializer.isOverlayDrawerExpanded = expanded
        Log.i(tag, "[$activityName] setDrawerExpanded: $expanded (reason=$reason)")

        if (expanded) {
            applyOverlayBounds(true)
            OverlayBoundsHelper.requestFocusOnOverlay(this)
            window.decorView.postDelayed({
                if (isDrawerExpanded && !isFinishing) {
                    OverlayBoundsHelper.requestFocusOnOverlay(this)
                }
            }, 50L)

            layoutExpanded.visibility = View.VISIBLE
            layoutExpanded.alpha = 0f
            layoutExpanded.translationX = -layoutExpanded.width.toFloat().coerceAtLeast(300f)

            layoutExpanded.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            layoutCollapsed.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(200)
                .withEndAction {
                    layoutCollapsed.visibility = View.GONE
                }
                .start()
        } else {
            OverlayBoundsHelper.requestFocusOnSecondary(this)
            window.decorView.postDelayed({
                if (!isDrawerExpanded && !isFinishing) {
                    OverlayBoundsHelper.requestFocusOnSecondary(this)
                }
            }, 50L)

            layoutCollapsed.visibility = View.VISIBLE
            layoutCollapsed.alpha = 0f
            layoutCollapsed.scaleX = 0.7f
            layoutCollapsed.scaleY = 0.7f

            layoutCollapsed.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()

            val slideDistance = if (layoutExpanded.width > 0) layoutExpanded.width.toFloat() else 400f
            layoutExpanded.animate()
                .translationX(-slideDistance)
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    layoutExpanded.visibility = View.GONE
                    applyOverlayBounds(false)
                }
                .start()
        }
    }

    private fun updateOverlayViews() {
        if (isDrawerExpanded) {
            layoutCollapsed.visibility = View.GONE
            layoutExpanded.visibility = View.VISIBLE
            layoutExpanded.translationX = 0f
            layoutExpanded.alpha = 1f
        } else {
            layoutCollapsed.visibility = View.VISIBLE
            layoutCollapsed.alpha = 1f
            layoutCollapsed.scaleX = 1f
            layoutCollapsed.scaleY = 1f
            layoutExpanded.visibility = View.GONE
        }
    }

    override fun onBeforeCategoryLaunch() {
        if (isDrawerExpanded) {
            setDrawerExpanded(false, "CategorySelectedAutoCollapse")
        }
        ensureSecondaryFocus()
    }

    override fun onHomeRevealed() {
        super.onHomeRevealed()
        if (isDrawerExpanded) {
            setDrawerExpanded(false, "HomeRevealedAutoCollapse")
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDrawerExpanded) {
            OverlayBoundsHelper.requestFocusOnOverlay(this)
            window.decorView.postDelayed({
                if (isDrawerExpanded && !isFinishing) {
                    OverlayBoundsHelper.requestFocusOnOverlay(this)
                }
            }, 50L)
        } else {
            ensureSecondaryFocus()
        }
    }
}
