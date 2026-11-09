package com.example.lifecycleapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import com.example.core.item.AppConfig
import com.example.feature.home.HomeActivity
import com.example.feature.home.HomeNavigationBridge

/**
 * Task Root Activity for the application.
 *
 * Responsibilities:
 * 1. Wide Mode (`AppConfig.isOverlayMode == false`):
 *    - Serves as the Task Root and primary left 35% sidebar.
 *    - Pairs with Home/Category in the right 65% secondary container via [SplitInitializer].
 * 2. Narrow Mode (`AppConfig.isOverlayMode == true`):
 *    - Serves as the pristine Task Root beneath [HomeActivity].
 *    - Attaches the 80dp floating circular MENU button via [FloatingMenuManager].
 *    - Launches [HomeActivity] to cover the full screen.
 */
class ListActivity : BaseCategoryMenuActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isNarrow = AppConfig.isOverlayMode(this)
        SplitInitializer.ensureInitialized(this, isNarrow)

        if (isNarrow) {
            // Narrow Mode: Floating MENU button is attached via WindowManager
            FloatingMenuManager.attach(this)
        } else {
            // Wide Mode: Static sidebar layout on the left 35%
            setContentView(R.layout.activity_list)
            initCategoryMenuViews()
        }

        val initialCategoryId = intent?.getStringExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID)
        val initialTargetIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(HomeActivity.EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(HomeActivity.EXTRA_TARGET_INTENT)
        }

        if (savedInstanceState == null) {
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                action = ACTION_SETTINGS_EMBED
                if (initialCategoryId != null) {
                    putExtra(HomeActivity.EXTRA_INITIAL_CATEGORY_ID, initialCategoryId)
                }
                if (initialTargetIntent != null) {
                    putExtra(HomeActivity.EXTRA_TARGET_INTENT, initialTargetIntent)
                }
            }
            startActivity(homeIntent)
            overridePendingTransition(0, 0)
        }

        if (!initialCategoryId.isNullOrEmpty() && !isNarrow) {
            val matchedIndex = categories.indexOfFirst { it.id.equals(initialCategoryId, ignoreCase = true) }
            if (matchedIndex >= 0) {
                categoryAdapter?.selectedPosition = matchedIndex
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(matchedIndex)
            }
        } else if (intent?.data != null || intent?.action == "com.example.lifecycleapp.open") {
            handleIntent(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingMenuManager.detach()
    }

    private fun ensureSecondaryFocus() {
        if (isFinishing) return
        OverlayBoundsHelper.requestFocusOnSecondary(this)

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }

        window.decorView.postDelayed({
            if (!isFinishing) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 50L)
        window.decorView.postDelayed({
            if (!isFinishing) {
                OverlayBoundsHelper.requestFocusOnSecondary(this)
            }
        }, 300L)
    }

    override fun onBeforeCategoryLaunch() {
        ensureSecondaryFocus()
    }

    override fun onHomeRevealed() {
        super.onHomeRevealed()
        ensureSecondaryFocus()
    }

    override fun onResume() {
        super.onResume()
        ensureSecondaryFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            ensureSecondaryFocus()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (ev != null && (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)) {
            ensureSecondaryFocus()
        }
        return handled
    }
}
