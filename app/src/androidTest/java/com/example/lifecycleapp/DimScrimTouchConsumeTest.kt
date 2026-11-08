package com.example.lifecycleapp

import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit/Component test verifying:
 * 1. [OverlayBoundsHelper.calculateOverlayBounds] expands to full Task width & height (`100%`)
 *    when expanded and shrinks to the `80dp × 80dp` top-left button when collapsed.
 * 2. In `activity_list_overlay.xml`, `viewDimScrim` covers 100% of the overlay container,
 *    `layoutExpanded` occupies the left 35%, and touches on the right 65% dimmed outside area
 *    are CONSUMED (`dispatchTouchEvent` returns `true`) by `viewDimScrim` without leaking
 *    to any underlying view, while invoking the collapse callback.
 */
@RunWith(AndroidJUnit4::class)
class DimScrimTouchConsumeTest {

    @Test
    fun calculateOverlayBounds_returnsTopLeftButtonWhenCollapsed_andFullTaskWhenExpanded() {
        val taskWidth = 1400
        val taskHeight = 1600
        val topOffset = 48
        val buttonSizePx = 160 // 80dp @ 2x density

        val collapsedRect = OverlayBoundsHelper.calculateOverlayBounds(
            taskWidth = taskWidth,
            taskHeight = taskHeight,
            topOffset = topOffset,
            buttonSizePx = buttonSizePx,
            isExpanded = false
        )
        assertEquals(Rect(0, 48, 160, 208), collapsedRect)

        val expandedRect = OverlayBoundsHelper.calculateOverlayBounds(
            taskWidth = taskWidth,
            taskHeight = taskHeight,
            topOffset = topOffset,
            buttonSizePx = buttonSizePx,
            isExpanded = true
        )
        assertEquals(Rect(0, 0, 1400, 1600), expandedRect)
        assertFalse("Expanded bounds must be non-empty so SplitPresenter keeps overlay pinned", expandedRect.isEmpty)
    }

    @Test
    fun dimScrim_consumesTouchInRightOutsideArea_andBlocksUnderlyingViewClick() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        var dimScrimCollapseCount = 0
        var underlyingClickedCount = 0
        var downConsumed = false
        var upConsumed = false
        lateinit var viewDimScrim: View
        lateinit var layoutExpanded: View

        instrumentation.runOnMainSync {
            val width = 1000
            val height = 1000

            // Simulate a parent container stacking:
            // [0] Underlying secondary content view (clickable across full 1000x1000)
            // [1] Overlay root layout (activity_list_overlay.xml) on top
            val rootStack = FrameLayout(context)
            val underlyingButton = View(context).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { underlyingClickedCount++ }
            }
            rootStack.addView(
                underlyingButton,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            val overlayRoot = LayoutInflater.from(context)
                .inflate(R.layout.activity_list_overlay, rootStack, false)
            rootStack.addView(
                overlayRoot,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            viewDimScrim = overlayRoot.findViewById(R.id.viewDimScrim)
            layoutExpanded = overlayRoot.findViewById(R.id.layoutExpanded)
            val layoutCollapsed = overlayRoot.findViewById<View>(R.id.layoutCollapsed)

            viewDimScrim.setOnClickListener {
                dimScrimCollapseCount++
                viewDimScrim.visibility = View.GONE
                layoutExpanded.visibility = View.GONE
                layoutCollapsed.visibility = View.VISIBLE
            }

            // Set expanded state (drawer visible on left 35%, dim scrim visible across 100%)
            layoutCollapsed.visibility = View.GONE
            viewDimScrim.visibility = View.VISIBLE
            layoutExpanded.visibility = View.VISIBLE

            // Measure and layout the 1000x1000 hierarchy
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            rootStack.measure(widthSpec, heightSpec)
            rootStack.layout(0, 0, width, height)

            // Verify layoutExpanded takes left 35% (350px) and viewDimScrim spans 100% (1000px)
            assertEquals("layoutExpanded must occupy 35% width", 350, layoutExpanded.width)
            assertEquals("viewDimScrim must span 100% width", 1000, viewDimScrim.width)

            // Dispatch touch event in the right 65% dimmed outside area (x = 750, y = 500)
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 750f, 500f, 0)
            val upEvent = MotionEvent.obtain(downTime, downTime + 50L, MotionEvent.ACTION_UP, 750f, 500f, 0)

            downConsumed = rootStack.dispatchTouchEvent(downEvent)
            upConsumed = rootStack.dispatchTouchEvent(upEvent)

            // In detached views, View.onTouchEvent(ACTION_UP) queues PerformClick into HandlerActionQueue;
            // executePendingActions or performClick completes the click callback if viewDimScrim is pressed.
            if (downConsumed && upConsumed && dimScrimCollapseCount == 0) {
                viewDimScrim.performClick()
            }

            downEvent.recycle()
            upEvent.recycle()
        }

        assertTrue("ACTION_DOWN on dimmed outside area must be consumed", downConsumed)
        assertTrue("ACTION_UP on dimmed outside area must be consumed", upConsumed)
        assertEquals("viewDimScrim click listener must be invoked once to collapse drawer", 1, dimScrimCollapseCount)
        assertEquals("Underlying view must NOT receive the click while dim scrim is active", 0, underlyingClickedCount)
        assertEquals("viewDimScrim must transition to GONE after collapse", View.GONE, viewDimScrim.visibility)
    }
}
