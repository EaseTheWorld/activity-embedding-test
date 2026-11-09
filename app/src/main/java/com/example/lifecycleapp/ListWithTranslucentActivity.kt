package com.example.lifecycleapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback

/**
 * Translucent modal drawer activity for Narrow Mode.
 *
 * Launched when the user taps the 80dp floating MENU button.
 * Occupies the top of the standard Android ActivityStack:
 * - Left 35%: Category navigation drawer (`layoutExpanded`)
 * - Right 65%: Modal dim background (`viewDimScrim`)
 *
 * Disappears naturally via [finish] on Back key press, Dim scrim click, or category selection,
 * passing focus and control back to the underlying screen without complex WindowManager focus hacks.
 */
class ListWithTranslucentActivity : BaseCategoryMenuActivity() {

    private lateinit var layoutExpanded: View
    private lateinit var viewDimScrim: View
    private lateinit var btnCollapse: View
    private var isClosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContentView(R.layout.activity_list_translucent)

        layoutExpanded = findViewById(R.id.layoutExpanded)
        viewDimScrim = findViewById(R.id.viewDimScrim)
        btnCollapse = findViewById(R.id.btnCollapse)

        initCategoryMenuViews()

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

    override fun onStart() {
        super.onStart()
        FloatingMenuManager.setSuppressed(true)
    }

    override fun onResume() {
        super.onResume()
        FloatingMenuManager.setSuppressed(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingMenuManager.setSuppressed(false)
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

    override fun onBeforeCategoryLaunch() {
        closeMenu()
    }
}
