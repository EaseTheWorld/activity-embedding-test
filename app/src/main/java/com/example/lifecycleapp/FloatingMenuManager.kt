package com.example.lifecycleapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Manages the Mini Edge Swipe Handle attached directly to the WindowManager in Narrow Mode.
 *
 * Features:
 * 1. Minimal Edge Pill: Sits along the left edge at vertical center with a tiny 6dp visual pill,
 *    eliminating screen occlusion over category titles and subscreen back buttons.
 * 2. Dual Interaction: Supports both a light tap (click) and rightward swipe/drag gesture
 *    to trigger [ListWithTranslucentActivity].
 * 3. Cross-Process / Zero-Dependency: Operates at the WindowManager overlay layer, requiring
 *    zero code modifications in internal or external plugin activities.
 */
object FloatingMenuManager {
    private const val TAG = "FloatingMenuManager"

    private const val HANDLE_TOUCH_WIDTH_DP = 32
    private const val HANDLE_TOUCH_HEIGHT_DP = 72
    private const val SWIPE_THRESHOLD_DP = 16

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isAttached = false
    private var isSuppressed = false
    private var isAppForeground = true
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    fun attach(context: Context) {
        mainHandler.post {
            if (isAttached && floatingView != null) {
                updateVisibility()
                return@post
            }
            try {
                val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm

                val density = context.resources.displayMetrics.density
                val handleWidthPx = (HANDLE_TOUCH_WIDTH_DP * density).toInt()
                val handleHeightPx = (HANDLE_TOUCH_HEIGHT_DP * density).toInt()
                val swipeThresholdPx = SWIPE_THRESHOLD_DP * density

                val params = WindowManager.LayoutParams(
                    handleWidthPx,
                    handleHeightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    x = 0
                    y = 0
                }

                val inflater = LayoutInflater.from(context.applicationContext)
                val view = inflater.inflate(R.layout.layout_floating_menu_button, null)
                val btnMore = view.findViewById<View>(R.id.btnMore)

                var startX = 0f
                var startY = 0f
                var isSwipeTriggered = false

                btnMore.setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX
                            startY = event.rawY
                            isSwipeTriggered = false
                            v.animate().scaleX(1.15f).setDuration(80).start()
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - startX
                            val dy = Math.abs(event.rawY - startY)
                            if (!isSwipeTriggered && dx > swipeThresholdPx && dx > dy * 0.7f) {
                                isSwipeTriggered = true
                                v.animate().scaleX(1.0f).setDuration(80).start()
                                Log.d(TAG, "Edge Swipe Right detected (dx=$dx, dy=$dy) -> launching overlay drawer")
                                launchDrawer(context)
                                true
                            } else {
                                false
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            v.animate().scaleX(1.0f).setDuration(80).start()
                            if (isSwipeTriggered) {
                                true
                            } else {
                                v.performClick()
                                true
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            v.animate().scaleX(1.0f).setDuration(80).start()
                            false
                        }
                        else -> false
                    }
                }

                btnMore.setOnClickListener {
                    Log.d(TAG, "Mini Edge Handle clicked -> launching ListWithTranslucentActivity")
                    launchDrawer(context)
                }

                wm.addView(view, params)
                floatingView = view
                isAttached = true
                Log.i(TAG, "Attached Mini Edge Swipe Handle (width=${handleWidthPx}px, height=${handleHeightPx}px at CenterVertical)")
                updateVisibility()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach floating window: $e", e)
            }
        }
    }

    private fun launchDrawer(context: Context) {
        val intent = Intent(context.applicationContext, ListWithTranslucentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.applicationContext.startActivity(intent)
    }

    fun detach() {
        mainHandler.post {
            try {
                floatingView?.let { view ->
                    windowManager?.removeView(view)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floating window: $e")
            } finally {
                floatingView = null
                windowManager = null
                isAttached = false
            }
        }
    }

    fun setSuppressed(suppressed: Boolean) {
        mainHandler.post {
            if (isSuppressed != suppressed) {
                isSuppressed = suppressed
                Log.d(TAG, "setSuppressed: $suppressed")
                updateVisibility()
            }
        }
    }

    fun setAppForeground(foreground: Boolean) {
        // Kept for interface compatibility; visibility is tied to attach/suppress lifecycle
    }

    private fun updateVisibility() {
        val shouldShow = isAttached && !isSuppressed
        floatingView?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        Log.d(TAG, "FloatingView visibility updated: ${if (shouldShow) "VISIBLE" else "GONE"} (isAttached=$isAttached, isSuppressed=$isSuppressed)")
    }
}
