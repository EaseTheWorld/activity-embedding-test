package com.example.lifecycleapp

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.example.core.item.AppConfig

object FloatingMenuManager {
    private const val TAG = "FloatingMenuManager"

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isAttached = false
    private var isSuppressed = false
    private var isAppForeground = true
    private val mainHandler = Handler(Looper.getMainLooper())

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
                val buttonSizePx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * density).toInt()
                val topOffset = OverlayBoundsHelper.getStatusBarHeight(context)

                val params = WindowManager.LayoutParams(
                    buttonSizePx,
                    buttonSizePx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = topOffset
                }

                val inflater = LayoutInflater.from(context.applicationContext)
                val view = inflater.inflate(R.layout.layout_floating_menu_button, null)
                val btnMore = view.findViewById<View>(R.id.btnMore)
                btnMore.setOnClickListener {
                    Log.d(TAG, "Floating MENU button clicked -> launching ListWithTranslucentActivity")
                    val intent = Intent(context.applicationContext, ListWithTranslucentActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.applicationContext.startActivity(intent)
                }

                wm.addView(view, params)
                floatingView = view
                isAttached = true
                Log.i(TAG, "Attached FloatingMenuButton (buttonSize=${buttonSizePx}px, y=$topOffset)")
                updateVisibility()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach floating window: $e", e)
            }
        }
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
