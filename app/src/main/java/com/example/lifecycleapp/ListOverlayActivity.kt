package com.example.lifecycleapp

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity

/**
 * Narrow Mode Pinned Overlay Menu Activity (`Theme.LifecycleApp.Translucent` - Translucent).
 *
 * Responsibilities:
 * 1. Exclusively launched by `HomeActivity` in Narrow Mode as an Always-On-Top Pinned Overlay
 *    (`setOverlayCreateParams` + `shouldAssociateWithLaunchingActivity = false`).
 * 2. Toggles between a collapsed `80dp × 80dp` floating `MENU` button and an expanded full-width
 *    overlay (left 35% drawer + right 65% `viewDimScrim`) via `OverlayBoundsHelper.updateOverlayBounds`.
 * 3. Uses `WindowContainerTransaction.requestFocusOnTaskFragment` to shift WindowManager focus
 *    between the overlay drawer (when expanded) and the content container (when collapsed).
 */
class ListOverlayActivity : BaseCategoryMenuActivity() {

    private lateinit var layoutCollapsed: View
    private lateinit var layoutExpanded: View
    private lateinit var viewDimScrim: View
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
        viewDimScrim = findViewById(R.id.viewDimScrim)
        btnMore = findViewById(R.id.btnMore)
        btnCollapse = findViewById(R.id.btnCollapse)

        btnMore.setOnClickListener {
            Log.d(tag, "[$activityName] 'MENU' button clicked -> expanding overlay drawer to 35% with full-width dimmed scrim")
            setDrawerExpanded(true, "MenuButtonClicked")
        }

        btnCollapse.setOnClickListener {
            Log.d(tag, "[$activityName] 'Collapse' button clicked -> collapsing overlay to 80dp button")
            setDrawerExpanded(false, "CollapseButtonClicked")
        }

        viewDimScrim.setOnClickListener {
            Log.d(tag, "[$activityName] Dimmed outside area clicked -> collapsing overlay to 80dp button")
            setDrawerExpanded(false, "DimScrimClicked")
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

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (isDrawerExpanded) {
                    setDrawerExpanded(false, "BackPressedCollapse")
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    fun ensureSecondaryFocus() {
        if (isFinishing || isDrawerExpanded) return
        OverlayBoundsHelper.requestFocusOnSecondary(this)

        // relayoutWindow:
        // Temporarily add FLAG_NOT_FOCUSABLE when drawer is collapsed so WindowManagerService.relayoutWindow
        // sees flagChanges & FLAG_NOT_FOCUSABLE != 0 and executes updateFocusedWindowLocked,
        // shifting mCurrentFocus immediately to mFocusedApp (secondary task).
        // Then clear FLAG_NOT_FOCUSABLE after a frame so ListOverlayActivity's collapsed button remains tap-responsive.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.decorView.post {
            if (!isFinishing && !isDestroyed && !isDrawerExpanded) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }

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
        OverlayBoundsHelper.updateOverlayBounds(
            this,
            AppConfig.TAG_PRIMARY_OVERLAY,
            expanded
        )
    }

    fun setDrawerExpanded(expanded: Boolean, reason: String = "Unknown") {
        if (isDrawerExpanded == expanded) return
        isDrawerExpanded = expanded
        SplitInitializer.isOverlayDrawerExpanded = expanded
        backCallback.isEnabled = expanded
        Log.i(tag, "[$activityName] setDrawerExpanded: $expanded (reason=$reason)")

        if (expanded) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            applyOverlayBounds(true)
            OverlayBoundsHelper.requestFocusOnOverlay(this)
            window.decorView.postDelayed({
                if (isDrawerExpanded && !isFinishing) {
                    OverlayBoundsHelper.requestFocusOnOverlay(this)
                }
            }, 50L)

            viewDimScrim.animate().cancel()
            viewDimScrim.visibility = View.VISIBLE
            viewDimScrim.alpha = 0f
            viewDimScrim.animate()
                .alpha(1f)
                .setDuration(250)
                .start()

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
            ensureSecondaryFocus()

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

            viewDimScrim.animate().cancel()
            viewDimScrim.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    viewDimScrim.visibility = View.GONE
                }
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
                    ensureSecondaryFocus()
                }
                .start()
        }
    }

    private fun updateOverlayViews() {
        if (isDrawerExpanded) {
            layoutCollapsed.visibility = View.GONE
            viewDimScrim.visibility = View.VISIBLE
            viewDimScrim.alpha = 1f
            layoutExpanded.visibility = View.VISIBLE
            layoutExpanded.translationX = 0f
            layoutExpanded.alpha = 1f
        } else {
            layoutCollapsed.visibility = View.VISIBLE
            layoutCollapsed.alpha = 1f
            layoutCollapsed.scaleX = 1f
            layoutCollapsed.scaleY = 1f
            viewDimScrim.visibility = View.GONE
            viewDimScrim.alpha = 0f
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
