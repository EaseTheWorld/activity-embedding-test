package com.example.lifecycleapp

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.IActivityManager
import android.app.ITaskStackListener
import android.app.TaskInfo
import android.app.TaskStackListener
import android.app.UidObserver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field

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
 * 4. Option A Event-Driven Task Visibility:
 *    Eliminates polling completely by registering system-level Binder callbacks
 *    ([TaskStackListener] via `MANAGE_ACTIVITY_TASKS` on platform-signed/RECENTS builds, or
 *    privileged [UidObserver] via `PACKAGE_USAGE_STATS` on `/system/priv-app/` builds) and
 *    verifying [ActivityManager.AppTask] `taskInfo.isVisible` instantaneously on every transition.
 */
object FloatingMenuManager {
    private const val TAG = "FloatingMenuManager"

    private const val HANDLE_TOUCH_WIDTH_DP = 40
    private const val HANDLE_TOUCH_HEIGHT_DP = 80
    private const val SWIPE_THRESHOLD_DP = 16
    private const val TRANSITION_SETTLE_EARLY_MS = 220L
    private const val TRANSITION_SETTLE_LATE_MS = 550L

    // android.app.ActivityManager constants (@hide in android-34.jar, verified on API 37)
    private const val UID_OBSERVER_PROCSTATE = 1 shl 0 // ActivityManager.UID_OBSERVER_PROCSTATE
    private const val UID_OBSERVER_GONE = 1 shl 1      // ActivityManager.UID_OBSERVER_GONE
    private const val PROCESS_STATE_TOP = 2            // ActivityManager.PROCESS_STATE_TOP

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isAttached = false
    private var isSuppressed = false
    private var isAppForeground = true
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var desiredAttached = false

    @Volatile
    private var keepAliveServiceActive = false

    @Volatile
    private var observerContext: Context? = null

    @Volatile
    private var pendingRefreshSource: String = "event"

    @Volatile
    private var homePackageUids: Set<Int> = emptySet()

    @Volatile
    private var isHomeUidTop: Boolean = false

    @Volatile
    private var taskFocusedFieldChecked = false

    @Volatile
    private var cachedTaskFocusedField: Field? = null

    private var hiddenApiExempted = false
    private var taskStackListener: ITaskStackListener? = null
    private var uidObserver: UidObserver? = null
    private var registeredActivityManager: IActivityManager? = null

    private val refreshVisibilityRunnable = createVisibilityRunnable(delayLabel = null)
    private val settleEarlyVisibilityRunnable = createVisibilityRunnable(delayLabel = "settle${TRANSITION_SETTLE_EARLY_MS}ms")
    private val settleLateVisibilityRunnable = createVisibilityRunnable(delayLabel = "settle${TRANSITION_SETTLE_LATE_MS}ms")

    private fun createVisibilityRunnable(delayLabel: String?): Runnable = Runnable {
        val ctx = floatingView?.context ?: observerContext ?: return@Runnable
        if (!isAttached) return@Runnable
        val source = if (delayLabel == null) pendingRefreshSource else "$pendingRefreshSource+$delayLabel"
        refreshVisibilityNow(ctx, source)
    }

    fun isCurrentlyAttached(): Boolean = desiredAttached

    internal fun onKeepAliveServiceStateChanged(active: Boolean) {
        keepAliveServiceActive = active
    }

    @VisibleForTesting
    internal fun computeShouldShow(
        attached: Boolean,
        suppressed: Boolean,
        appForeground: Boolean
    ): Boolean = attached && !suppressed && appForeground

    @VisibleForTesting
    internal fun isTaskForegroundActive(
        taskVisible: Boolean,
        taskFocused: Boolean?,
        isHomeUidTop: Boolean,
        hasTaskStackListener: Boolean
    ): Boolean {
        if (!taskVisible || isHomeUidTop) return false
        if (taskFocused == null) return true
        // During Quickstep Live-Tile Recents on Android 14–17, the task keeps `isVisible=true`
        // (and on Android 17 may even retain `isFocused=true`) while Home Launcher owns
        // foreground TOP (`isHomeUidTop=true`). Checking `isHomeUidTop` first guarantees hiding.
        return taskFocused || !hasTaskStackListener
    }

    private fun ensureHiddenApiExemptions() {
        if (hiddenApiExempted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/app/", "Landroid/os/")
                hiddenApiExempted = true
                Log.d(TAG, "Successfully exempted Landroid/app/ and Landroid/os/ hidden APIs")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to exempt hidden APIs: $t", t)
            }
        } else {
            hiddenApiExempted = true
        }
    }

    private fun cancelPendingVisibilityChecks() {
        mainHandler.removeCallbacks(refreshVisibilityRunnable)
        mainHandler.removeCallbacks(settleEarlyVisibilityRunnable)
        mainHandler.removeCallbacks(settleLateVisibilityRunnable)
    }

    private fun scheduleVisibilityRefresh(context: Context, source: String) {
        observerContext = context.applicationContext
        pendingRefreshSource = source
        cancelPendingVisibilityChecks()
        mainHandler.post(refreshVisibilityRunnable)
        // Re-evaluate briefly after OS transition animations commit TaskInfo.isVisible
        // (especially for persistent uid=1000 overlays such as System Settings). No periodic loop.
        mainHandler.postDelayed(settleEarlyVisibilityRunnable, TRANSITION_SETTLE_EARLY_MS)
        mainHandler.postDelayed(settleLateVisibilityRunnable, TRANSITION_SETTLE_LATE_MS)
    }

    private fun refreshVisibilityNow(context: Context, source: String) {
        val visible = isAppTaskVisible(context)
        if (isAppForeground != visible) {
            isAppForeground = visible
            Log.i(TAG, "Task visibility changed ($source) -> isAppForeground=$visible")
            updateVisibility()
        }
    }

    private fun registerVisibilityObserver(context: Context) {
        val appCtx = context.applicationContext
        observerContext = appCtx
        if (taskStackListener != null || uidObserver != null) {
            return
        }

        ensureHiddenApiExemptions()

        // Path 1: Primary TaskStackListener via IActivityTaskManager (platform-signed / RECENTS)
        val tsListener = object : TaskStackListener() {
            override fun onTaskStackChanged() {
                scheduleVisibilityRefresh(appCtx, "TaskStack:onTaskStackChanged")
            }

            override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
                scheduleVisibilityRefresh(appCtx, "TaskStack:onTaskMovedToFront(${taskInfo.taskId})")
            }

            override fun onTaskMovedToBack(taskInfo: ActivityManager.RunningTaskInfo) {
                scheduleVisibilityRefresh(appCtx, "TaskStack:onTaskMovedToBack(${taskInfo.taskId})")
            }

            override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
                scheduleVisibilityRefresh(appCtx, "TaskStack:onTaskFocusChanged(task=$taskId,focused=$focused)")
            }

            override fun onTaskRemoved(taskId: Int) {
                scheduleVisibilityRefresh(appCtx, "TaskStack:onTaskRemoved($taskId)")
            }

            override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
                scheduleVisibilityRefresh(appCtx, "TaskStack:onTaskCreated($taskId)")
            }
        }

        try {
            ActivityTaskManager.getService().registerTaskStackListener(tsListener)
            taskStackListener = tsListener
            Log.i(TAG, "Option A: Registered TaskStackListener via ActivityTaskManager (event-driven, 0ms polling)")
            return
        } catch (se: SecurityException) {
            Log.w(
                TAG,
                "TaskStackListener unavailable without platform signature/RECENTS (${se.message}); falling back to privileged IUidObserver (PACKAGE_USAGE_STATS)"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "TaskStackListener registration failed: $t; falling back to privileged IUidObserver", t)
        }

        // Path 2: Privileged IUidObserver via IActivityManager + PACKAGE_USAGE_STATS (/system/priv-app/)
        try {
            val amBinder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            val am = IActivityManager.Stub.asInterface(amBinder)
            if (am == null) {
                Log.e(TAG, "ServiceManager.getService(activity) returned null")
                return
            }

            homePackageUids = resolveHomePackageUids(appCtx)
            isHomeUidTop = false

            val observer = object : UidObserver() {
                override fun onUidStateChanged(
                    uid: Int,
                    procState: Int,
                    procStateSeq: Long,
                    capability: Int
                ) {
                    if (uid in homePackageUids) {
                        isHomeUidTop = (procState <= PROCESS_STATE_TOP)
                    }
                    scheduleVisibilityRefresh(
                        appCtx,
                        "UidObserver:onUidStateChanged(uid=$uid,procState=$procState,homeTop=$isHomeUidTop)"
                    )
                }

                override fun onUidGone(uid: Int, disabled: Boolean) {
                    if (uid in homePackageUids) {
                        isHomeUidTop = false
                    }
                    scheduleVisibilityRefresh(appCtx, "UidObserver:onUidGone(uid=$uid)")
                }
            }

            am.registerUidObserver(
                observer,
                UID_OBSERVER_PROCSTATE or UID_OBSERVER_GONE,
                PROCESS_STATE_TOP,
                appCtx.opPackageName
            )
            uidObserver = observer
            registeredActivityManager = am
            Log.i(
                TAG,
                "Option A: Registered privileged IUidObserver via PACKAGE_USAGE_STATS " +
                    "(cutpoint=PROCESS_STATE_TOP, homeUids=$homePackageUids, event-driven)"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register privileged IUidObserver: $t", t)
        }
    }

    private fun resolveHomePackageUids(context: Context): Set<Int> {
        return try {
            val pm = context.packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val uids = LinkedHashSet<Int>()
            val resolved = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            val resolvedUid = resolved?.activityInfo?.applicationInfo?.uid
            if (resolvedUid != null && resolvedUid != android.os.Process.SYSTEM_UID) {
                uids.add(resolvedUid)
            }
            @Suppress("DEPRECATION")
            val candidates = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (candidate in candidates) {
                val uid = candidate.activityInfo?.applicationInfo?.uid ?: continue
                if (uid != android.os.Process.SYSTEM_UID) {
                    uids.add(uid)
                }
            }
            uids
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve home UIDs: $t")
            emptySet()
        }
    }

    private fun readTaskFocused(info: TaskInfo): Boolean? {
        ensureHiddenApiExemptions()
        if (!taskFocusedFieldChecked) {
            synchronized(this) {
                if (!taskFocusedFieldChecked) {
                    cachedTaskFocusedField = try {
                        TaskInfo::class.java.getDeclaredField("isFocused").apply {
                            isAccessible = true
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "TaskInfo.isFocused unavailable: $t")
                        null
                    }
                    taskFocusedFieldChecked = true
                }
            }
        }
        return try {
            cachedTaskFocusedField?.getBoolean(info)
        } catch (t: Throwable) {
            null
        }
    }

    private fun unregisterVisibilityObserver() {
        taskStackListener?.let { listener ->
            try {
                ActivityTaskManager.getService().unregisterTaskStackListener(listener)
                Log.d(TAG, "Unregistered TaskStackListener")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to unregister TaskStackListener: $t")
            }
            taskStackListener = null
        }

        uidObserver?.let { observer ->
            try {
                registeredActivityManager?.unregisterUidObserver(observer)
                Log.d(TAG, "Unregistered IUidObserver")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to unregister IUidObserver: $t")
            }
            uidObserver = null
            registeredActivityManager = null
        }

        isHomeUidTop = false
        homePackageUids = emptySet()
        observerContext = null
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(context: Context) {
        val appCtx = context.applicationContext
        desiredAttached = true
        observerContext = appCtx
        if (!keepAliveServiceActive) {
            FloatingOverlayService.start(appCtx)
        }
        mainHandler.post {
            if (!desiredAttached) {
                return@post
            }
            if (isAttached && floatingView != null) {
                registerVisibilityObserver(appCtx)
                isAppForeground = isAppTaskVisible(appCtx)
                updateVisibility()
                return@post
            }
            try {
                val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm

                val density = appCtx.resources.displayMetrics.density
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

                val inflater = LayoutInflater.from(appCtx)
                val view = inflater.inflate(R.layout.layout_floating_menu_button, null)
                val btnMore = view.findViewById<View>(R.id.btnMore)

                // Exclude this handle region from Android 10+ system gesture navigation (Edge Back Gesture)
                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && view.width > 0 && view.height > 0) {
                        view.systemGestureExclusionRects = listOf(
                            android.graphics.Rect(0, 0, view.width, view.height)
                        )
                    }
                }

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
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - startX
                            val dy = Math.abs(event.rawY - startY)
                            if (!isSwipeTriggered && dx > swipeThresholdPx && dx > dy * 0.7f) {
                                isSwipeTriggered = true
                                v.animate().scaleX(1.0f).setDuration(80).start()
                                Log.d(TAG, "Edge Swipe Right detected (dx=$dx, dy=$dy) -> launching overlay drawer")
                                launchDrawer(appCtx)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            v.animate().scaleX(1.0f).setDuration(80).start()
                            if (!isSwipeTriggered) {
                                val dx = Math.abs(event.rawX - startX)
                                val dy = Math.abs(event.rawY - startY)
                                if (dx < swipeThresholdPx && dy < swipeThresholdPx) {
                                    v.performClick()
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            v.animate().scaleX(1.0f).setDuration(80).start()
                            true
                        }
                        else -> false
                    }
                }

                btnMore.setOnClickListener {
                    Log.d(TAG, "Mini Edge Handle clicked -> launching ListWithTranslucentActivity")
                    launchDrawer(appCtx)
                }

                wm.addView(view, params)
                floatingView = view
                isAttached = true
                Log.i(TAG, "Attached Mini Edge Swipe Handle (width=${handleWidthPx}px, height=${handleHeightPx}px at CenterVertical)")

                registerVisibilityObserver(appCtx)
                isAppForeground = isAppTaskVisible(appCtx)
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
        desiredAttached = false
        val immediateCtx = observerContext ?: floatingView?.context?.applicationContext
        if (immediateCtx != null) {
            FloatingOverlayService.stop(immediateCtx)
        }
        mainHandler.post {
            val stopCtx = observerContext ?: floatingView?.context?.applicationContext
            cancelPendingVisibilityChecks()
            unregisterVisibilityObserver()
            if (stopCtx != null) {
                FloatingOverlayService.stop(stopCtx)
            }
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

    /**
     * Event-driven safeguard invoked when any host process Activity resumes, ensuring initial
     * cold-start transitions (e.g. `ListActivity.onCreate` -> `HomeActivity.onResume`) always
     * reconcile [isAppForeground] with [isAppTaskVisible] without any polling timer.
     */
    fun onHostActivityResumed(context: Context) {
        if (!desiredAttached) return
        val appCtx = context.applicationContext
        if (!keepAliveServiceActive) {
            FloatingOverlayService.start(appCtx)
        }
        if (!isAttached) return
        scheduleVisibilityRefresh(appCtx, "hostResumed")
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

    @Suppress("UNUSED_PARAMETER")
    fun setAppForeground(foreground: Boolean) {
        // Kept for interface compatibility; foreground visibility is driven by OS task/UID events
    }

    fun isAppTaskVisible(context: Context): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appTasks = am.appTasks
            val hasTsListener = taskStackListener != null
            for (task in appTasks) {
                val info = task.taskInfo
                val isVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isVisible
                } else {
                    true
                }
                val isFocused = readTaskFocused(info)
                val active = isTaskForegroundActive(
                    taskVisible = isVisible,
                    taskFocused = isFocused,
                    isHomeUidTop = isHomeUidTop,
                    hasTaskStackListener = hasTsListener
                )
                if (isVisible) {
                    Log.d(
                        TAG,
                        "Inspect task[${info.taskId}] top=${info.topActivity?.shortClassName}: " +
                            "isVisible=$isVisible, isFocused=$isFocused, homeTop=$isHomeUidTop -> active=$active"
                    )
                }
                if (active) return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "isAppTaskVisible error: $e", e)
        }
        return false
    }

    private fun updateVisibility() {
        val shouldShow = computeShouldShow(isAttached, isSuppressed, isAppForeground)
        floatingView?.visibility = if (shouldShow) View.VISIBLE else View.GONE
        Log.d(
            TAG,
            "FloatingView visibility updated: ${if (shouldShow) "VISIBLE" else "GONE"} " +
                "(isAttached=$isAttached, isSuppressed=$isSuppressed, isAppForeground=$isAppForeground)"
        )
    }
}
