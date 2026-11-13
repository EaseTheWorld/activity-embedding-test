package com.example.lifecycleapp

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import com.example.core.item.AppConfig

/**
 * Translucent modal drawer activity for Narrow Mode.
 *
 * Launched when the user taps the category main screen's top-left menu button.
 * Occupies the top of the standard Android ActivityStack:
 * - Left 35%: Category navigation drawer (`layoutExpanded`)
 * - Right 65%: Modal dim background (`viewDimScrim`)
 *
 * Disappears naturally via [finish] on Back key press, Dim scrim click, or category selection,
 * passing focus and control back to the underlying screen without overlay windows.
 */
class ListWithTranslucentActivity : BaseCategoryMenuActivity() {

    private lateinit var layoutExpanded: View
    private lateinit var viewDimScrim: View
    private lateinit var btnCollapse: View
    private var isClosing = false
    private var activeCategoryId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContentView(R.layout.activity_list_translucent)

        layoutExpanded = findViewById(R.id.layoutExpanded)
        viewDimScrim = findViewById(R.id.viewDimScrim)
        btnCollapse = findViewById(R.id.btnCollapse)

        initCategoryMenuViews()

        val initialCategoryId = savedInstanceState?.getString(KEY_ACTIVE_CATEGORY_ID)
            ?: intent?.getStringExtra("extra_category_id")
            ?: intent?.getStringExtra("category_id")
        activeCategoryId = initialCategoryId
        if (!initialCategoryId.isNullOrEmpty()) {
            val matchedIndex = categories.indexOfFirst { it.id.equals(initialCategoryId, ignoreCase = true) }
            if (matchedIndex >= 0) {
                categoryAdapter?.selectedPosition = matchedIndex
                categoryAdapter?.notifyDataSetChanged()
                listViewCategories.smoothScrollToPosition(matchedIndex)
            }
        }

        viewDimScrim.setOnClickListener {
            Log.d(tag, "[$activityName] Dimmed scrim clicked -> closing menu")
            closeMenu()
        }

        btnCollapse.setOnClickListener {
            Log.d(tag, "[$activityName] Collapse button clicked -> closing menu")
            closeMenu()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(tag, "[$activityName] Back pressed -> closing menu")
                closeMenu()
            }
        })

        animateOpen()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ModeTransitionManager.checkAndHandleTransition(this, newConfig)
        if (!AppConfig.isOverlayMode(this) && !isFinishing && !isClosing) {
            Log.d(tag, "[$activityName] Transitioned to Wide Mode -> closing narrow menu drawer")
            isClosing = true
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun animateOpen() {
        viewDimScrim.alpha = 0f
        viewDimScrim.animate().alpha(1f).setDuration(250).start()

        layoutExpanded.alpha = 0f
        layoutExpanded.translationX = -400f
        layoutExpanded.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun closeMenu() {
        if (isClosing) return
        isClosing = true

        viewDimScrim.animate().alpha(0f).setDuration(200).start()
        layoutExpanded.animate()
            .translationX(-400f)
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            .start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val currentId = currentSelectedCategoryId ?: activeCategoryId
        if (!currentId.isNullOrEmpty()) {
            outState.putString(KEY_ACTIVE_CATEGORY_ID, currentId)
        }
    }

    override fun onBeforeCategoryLaunch() {
        closeMenu()
    }

    private companion object {
        const val KEY_ACTIVE_CATEGORY_ID = "key_active_category_id"
    }
}
