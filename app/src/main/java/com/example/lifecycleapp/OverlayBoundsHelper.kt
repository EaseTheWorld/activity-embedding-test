package com.example.lifecycleapp

import android.app.Activity
import android.app.ActivityTaskManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.SparseArray
import com.example.core.item.AppConfig
import android.window.TaskFragmentOrganizer
import android.window.WindowContainerTransaction
import androidx.window.extensions.WindowExtensionsProvider
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Proxy

/**
 * Helper to manage WindowManager ActivityEmbedding Overlay Relative Bounds and TaskFragment Focus.
 *
 * Jetpack WindowManager's public [androidx.window.embedding.EmbeddingBounds] only supports
 * 4 centered alignments (ALIGN_LEFT centers vertically, ALIGN_TOP centers horizontally).
 * To position the 80dp floating circular MENU button at the TOP-LEFT corner without blocking
 * touch events elsewhere, this helper directly interacts with Android WindowManager Extension's
 * [androidx.window.extensions.embedding.ActivityEmbeddingComponent]:
 * 1. Registers an [ActivityStackAttributesCalculator] dynamic proxy to provide top-left bounds
 *    whenever WindowManager recalculates task/stack attributes.
 * 2. Directly updates relative bounds via [updateActivityStackAttributes] on state transitions.
 * 3. Requests window focus on the Secondary [TaskFragmentContainer] via
 *    `WindowContainerTransaction.requestFocusOnTaskFragment(IBinder)` so the top Secondary activity
 *    (including external app activities in separate processes) always receives Back key events.
 */
object OverlayBoundsHelper {
    private const val TAG = "OverlayBoundsHelper"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/window/")
                Log.d(TAG, "Successfully exempted Landroid/window/ hidden APIs")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to exempt hidden APIs: $e", e)
            }
        }
    }

    fun initCalculator(context: Context) {
        try {
            val providerClass = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
            val getWindowExtensionsMethod = providerClass.getDeclaredMethod("getWindowExtensions")
            val windowExtensions = getWindowExtensionsMethod.invoke(null) ?: return
            val getEmbeddingComponentMethod = windowExtensions.javaClass.getMethod("getActivityEmbeddingComponent")
            val embeddingComponent = getEmbeddingComponentMethod.invoke(windowExtensions) ?: return

            val functionClass = Class.forName("androidx.window.extensions.core.util.function.Function")
            val builderClass = Class.forName("androidx.window.extensions.embedding.ActivityStackAttributes\$Builder")

            val proxy = Proxy.newProxyInstance(
                functionClass.classLoader,
                arrayOf(functionClass)
            ) { _, method, args ->
                if (method.name == "apply" && args != null && args.isNotEmpty()) {
                    val params = args[0]
                    val getParentInfoMethod = params.javaClass.getMethod("getParentContainerInfo")
                    val parentInfo = getParentInfoMethod.invoke(params)
                    val getWindowMetricsMethod = parentInfo.javaClass.getMethod("getWindowMetrics")
                    val windowMetrics = getWindowMetricsMethod.invoke(parentInfo)
                    val getBoundsMethod = windowMetrics.javaClass.getMethod("getBounds")
                    val taskBounds = getBoundsMethod.invoke(windowMetrics) as Rect

                    val density = context.resources.displayMetrics.density
                    val buttonSizePx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * density).toInt()
                    val topOffset = getStatusBarHeight(context)

                    val targetBounds = if (SplitInitializer.isOverlayDrawerExpanded) {
                        Rect(0, 0, (taskBounds.width() * AppConfig.PRIMARY_DRAWER_RATIO).toInt(), taskBounds.height())
                    } else {
                        Rect(0, topOffset, buttonSizePx, topOffset + buttonSizePx)
                    }

                    val builder = builderClass.getDeclaredConstructor().newInstance()
                    val setRelativeBoundsMethod = builderClass.getMethod("setRelativeBounds", Rect::class.java)
                    setRelativeBoundsMethod.invoke(builder, targetBounds)
                    val buildMethod = builderClass.getMethod("build")
                    return@newProxyInstance buildMethod.invoke(builder)
                }
                null
            }

            val setCalculatorMethod = embeddingComponent.javaClass.getMethod(
                "setActivityStackAttributesCalculator",
                functionClass
            )
            setCalculatorMethod.invoke(embeddingComponent, proxy)
            Log.d(TAG, "Successfully registered ActivityStackAttributesCalculator proxy for top-left bounds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register calculator proxy: $e", e)
        }
    }

    fun updateOverlayBounds(
        context: Context,
        overlayTag: String,
        isExpanded: Boolean
    ): Boolean {
        try {
            val providerClass = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
            val getWindowExtensionsMethod = providerClass.getDeclaredMethod("getWindowExtensions")
            val windowExtensions = getWindowExtensionsMethod.invoke(null) ?: return false
            val getEmbeddingComponentMethod = windowExtensions.javaClass.getMethod("getActivityEmbeddingComponent")
            val embeddingComponent = getEmbeddingComponentMethod.invoke(windowExtensions) ?: return false

            val getTokenMethod = embeddingComponent.javaClass.getMethod("getActivityStackToken", String::class.java)
            val token = getTokenMethod.invoke(embeddingComponent, overlayTag) ?: return false

            val getParentInfoMethod = embeddingComponent.javaClass.getMethod(
                "getParentContainerInfo",
                Class.forName("androidx.window.extensions.embedding.ActivityStack\$Token")
            )
            val parentInfo = getParentInfoMethod.invoke(embeddingComponent, token) ?: return false
            val getWindowMetricsMethod = parentInfo.javaClass.getMethod("getWindowMetrics")
            val windowMetrics = getWindowMetricsMethod.invoke(parentInfo)
            val getBoundsMethod = windowMetrics.javaClass.getMethod("getBounds")
            val taskBounds = getBoundsMethod.invoke(windowMetrics) as Rect

            val density = context.resources.displayMetrics.density
            val buttonSizePx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * density).toInt()
            val topOffset = getStatusBarHeight(context)

            val targetBounds = if (isExpanded) {
                Rect(0, 0, (taskBounds.width() * AppConfig.PRIMARY_DRAWER_RATIO).toInt(), taskBounds.height())
            } else {
                Rect(0, topOffset, buttonSizePx, topOffset + buttonSizePx)
            }

            val builderClass = Class.forName("androidx.window.extensions.embedding.ActivityStackAttributes\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            val setRelativeBoundsMethod = builderClass.getMethod("setRelativeBounds", Rect::class.java)
            setRelativeBoundsMethod.invoke(builder, targetBounds)
            val buildMethod = builderClass.getMethod("build")
            val attributes = buildMethod.invoke(builder)

            val updateMethod = embeddingComponent.javaClass.getMethod(
                "updateActivityStackAttributes",
                Class.forName("androidx.window.extensions.embedding.ActivityStack\$Token"),
                Class.forName("androidx.window.extensions.embedding.ActivityStackAttributes")
            )
            updateMethod.invoke(embeddingComponent, token, attributes)
            Log.d(TAG, "Successfully updated overlay relative bounds to $targetBounds (isExpanded=$isExpanded)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay bounds via reflection: $e", e)
            return false
        }
    }

    /**
     * Prebuilt / System App Approach:
     * Directly asks [ActivityTaskManager] to apply [WindowContainerTransaction] via [android.window.IWindowOrganizerController].
     *
     * In an AOSP Prebuilt / System app environment with `android.permission.MANAGE_ACTIVITY_TASKS`,
     * this:
     * 1. Requires 0% reflection (pure compile-time type-safe call to ActivityTaskManager).
     * 2. Avoids creating a dummy TaskFragmentOrganizer and avoids organizer collision in system_server.
     * 3. Seamlessly works for both internal activities and external independent process activities.
     */
    fun requestFocusViaAtm(fragmentToken: IBinder): Boolean {
        return try {
            val atm = ActivityTaskManager.getService() ?: run {
                Log.w(TAG, "[ATM Focus] ActivityTaskManager.getService() returned null")
                return false
            }
            val woc = atm.windowOrganizerController ?: run {
                Log.w(TAG, "[ATM Focus] getWindowOrganizerController() returned null")
                return false
            }
            val wct = WindowContainerTransaction()
            wct.requestFocusOnTaskFragment(fragmentToken)
            woc.applyTransaction(wct)
            Log.d(TAG, "[ATM Focus] Successfully applied requestFocusOnTaskFragment via ActivityTaskManager -> WindowOrganizerController")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "[ATM Focus] SecurityException: ${e.message} (app lacks MANAGE_ACTIVITY_TASKS, falling back to Extension transaction)")
            false
        } catch (e: Throwable) {
            Log.w(TAG, "[ATM Focus] Direct ATM focus failed: $e, falling back to Extension transaction", e)
            false
        }
    }

    private var directOrganizer: TaskFragmentOrganizer? = null

    /**
     * Attempts to apply focus directly via [TaskFragmentOrganizer.applyTransaction]
     * using [WindowContainerTransaction.requestFocusOnTaskFragment] without reflection.
     *
     * In an AOSP Prebuilt / System app environment with `android.permission.MANAGE_ACTIVITY_TASKS`,
     * this succeeds directly and completely eliminates the need for reflection or internal locks.
     */
    fun requestFocusDirect(fragmentToken: IBinder): Boolean {
        return try {
            val organizer = directOrganizer ?: run {
                Log.d(TAG, "[Non-Reflection] Creating TaskFragmentOrganizer and calling registerOrganizer()...")
                val org = TaskFragmentOrganizer(Runnable::run)
                try {
                    org.registerOrganizer()
                    Log.d(TAG, "[Non-Reflection] registerOrganizer() succeeded!")
                } catch (regError: Throwable) {
                    Log.e(TAG, "[Non-Reflection] registerOrganizer() failed: $regError", regError)
                    throw regError
                }
                directOrganizer = org
                org
            }
            val wct = WindowContainerTransaction()
            wct.requestFocusOnTaskFragment(fragmentToken)
            organizer.applyTransaction(wct)
            Log.d(TAG, "[Non-Reflection] Successfully applied requestFocusOnTaskFragment via TaskFragmentOrganizer")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "[Non-Reflection] TaskFragmentOrganizer.applyTransaction threw SecurityException: ${e.message} (app lacks MANAGE_ACTIVITY_TASKS, falling back to Extension transaction)", e)
            false
        } catch (e: Throwable) {
            Log.w(TAG, "[Non-Reflection] Direct focus request failed: $e, falling back to Extension transaction", e)
            false
        }
    }

    /**
     * Atomically dismisses the active Category TaskFragment (whether containing internal
     * or external app activities) by calling [SplitController.finishActivityStacks].
     *
     * This ensures:
     * 1. WindowManager Extensions internal cache (`mTaskContainers`, `mSplitContainers`) remains
     *    100% in sync with `system_server`.
     * 2. [HomeActivity] (bottom container) and [PrimaryOverlayActivity] (pinned overlay container)
     *    are untouched and never destroyed.
     * 3. No intermediary [CategoryHostActivity] is needed.
     */
    fun dismissCategoryContainer(taskId: Int): Boolean {
        try {
            val providerClass = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
            val getWindowExtensionsMethod = providerClass.getDeclaredMethod("getWindowExtensions")
            val windowExtensions = getWindowExtensionsMethod.invoke(null) ?: return false
            val getEmbeddingComponentMethod = windowExtensions.javaClass.getMethod("getActivityEmbeddingComponent")
            val splitController = getEmbeddingComponentMethod.invoke(windowExtensions) ?: return false

            val lockField = splitController.javaClass.getDeclaredField("mLock").apply { isAccessible = true }
            val taskContainersField = splitController.javaClass.getDeclaredField("mTaskContainers").apply { isAccessible = true }

            val lock = lockField.get(splitController) ?: return false
            val taskContainers = taskContainersField.get(splitController) as? SparseArray<*> ?: return false

            synchronized(lock) {
                val taskContainer = taskContainers.get(taskId) ?: return false
                val targetTokens = mutableSetOf<IBinder>()

                Log.d(TAG, "[dismissCategoryContainer] Inspecting taskContainer for taskId=$taskId")

                // Helper to check if a TaskFragmentContainer holds HomeActivity
                fun isHomeContainer(container: Any): Boolean {
                    return try {
                        val getTopActMethod = container.javaClass.getDeclaredMethod("getTopNonFinishingActivity").apply {
                            isAccessible = true
                        }
                        val topAct = getTopActMethod.invoke(container) as? Activity
                        topAct != null && topAct.javaClass.simpleName.contains("HomeActivity")
                    } catch (e: Exception) {
                        false
                    }
                }

                // 1. In Wide (Split) Mode: dismiss the secondary split container if it's NOT HomeActivity
                val getSplitMethod = taskContainer.javaClass.getDeclaredMethod("getTopNonFinishingSplitContainer").apply {
                    isAccessible = true
                }
                val splitContainer = getSplitMethod.invoke(taskContainer)
                if (splitContainer != null) {
                    val getSecondaryMethod = splitContainer.javaClass.getDeclaredMethod("getSecondaryContainer").apply {
                        isAccessible = true
                    }
                    val secondary = getSecondaryMethod.invoke(splitContainer)
                    if (secondary != null) {
                        val getTokenMethod = secondary.javaClass.getDeclaredMethod("getTaskFragmentToken").apply {
                            isAccessible = true
                        }
                        val token = getTokenMethod.invoke(secondary) as? IBinder

                        val getTopActMethod = secondary.javaClass.getDeclaredMethod("getTopNonFinishingActivity").apply {
                            isAccessible = true
                        }
                        val topAct = getTopActMethod.invoke(secondary) as? Activity

                        val isHome = isHomeContainer(secondary)
                        Log.d(
                            TAG,
                            "[dismissCategoryContainer][Wide] SecondaryContainer found: token=$token, topAct=$topAct (null indicates external process activity!), isHome=$isHome"
                        )

                        if (!isHome && token != null) {
                            targetTokens.add(token)
                        }
                    }
                } else {
                    // 2. In Narrow (Overlay) Mode:
                    // Enumerate all content containers. Exclude Overlay and HomeActivity.
                    val getTfsMethod = taskContainer.javaClass.getDeclaredMethod("getTaskFragmentContainers").apply {
                        isAccessible = true
                    }
                    @Suppress("UNCHECKED_CAST")
                    val tfContainers = getTfsMethod.invoke(taskContainer) as? List<*> ?: emptyList<Any>()

                    Log.d(TAG, "[dismissCategoryContainer][Narrow] Total tfContainers=${tfContainers.size}")
                    for ((index, tf) in tfContainers.withIndex()) {
                        if (tf == null) continue
                        val getTokenMethod = tf.javaClass.getDeclaredMethod("getTaskFragmentToken").apply {
                            isAccessible = true
                        }
                        val token = getTokenMethod.invoke(tf) as? IBinder

                        val getTopActMethod = tf.javaClass.getDeclaredMethod("getTopNonFinishingActivity").apply {
                            isAccessible = true
                        }
                        val topAct = getTopActMethod.invoke(tf) as? Activity

                        val isOverlayMethod = tf.javaClass.getDeclaredMethod("isOverlay").apply { isAccessible = true }
                        val isOverlay = isOverlayMethod.invoke(tf) as? Boolean ?: false

                        val isFinishedMethod = tf.javaClass.getDeclaredMethod("isFinished").apply { isAccessible = true }
                        val isFinished = isFinishedMethod.invoke(tf) as? Boolean ?: false

                        val isHome = isHomeContainer(tf)

                        Log.d(
                            TAG,
                            "[dismissCategoryContainer][Narrow] Container[$index]: token=$token, topAct=$topAct (isExternal=${topAct == null}), isOverlay=$isOverlay, isFinished=$isFinished, isHome=$isHome"
                        )

                        if (isOverlay || isHome || isFinished) {
                            continue
                        }

                        if (token != null) {
                            targetTokens.add(token)
                        }
                    }
                }

                if (targetTokens.isNotEmpty()) {
                    val finishMethod = splitController.javaClass.getMethod("finishActivityStacks", Set::class.java)
                    finishMethod.invoke(splitController, targetTokens)
                    Log.d(TAG, "Dismissed category TaskFragments: count=${targetTokens.size}, tokens=$targetTokens (taskId=$taskId)")
                    return true
                }
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss category container: $e", e)
            return false
        }
    }

    /**
     * Shifts WindowManager focus (`mFocusedApp` and `mCurrentFocus`) from the Primary/Overlay
     * container to the top-most Secondary `TaskFragmentContainer` via
     * `WindowContainerTransaction.requestFocusOnTaskFragment(IBinder)`.
     *
     * Because this operation is executed by `WindowOrganizerController` in `system_server`,
     * it works even when the top activity inside the Secondary `TaskFragment` belongs to an
     * external app running in a separate process (e.g. `com.example.carsettings.light`).
     */
    fun requestFocusOnSecondary(activity: Activity): Boolean {
        try {
            val providerClass = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
            val getWindowExtensionsMethod = providerClass.getDeclaredMethod("getWindowExtensions")
            val windowExtensions = getWindowExtensionsMethod.invoke(null) ?: return false
            val getEmbeddingComponentMethod = windowExtensions.javaClass.getMethod("getActivityEmbeddingComponent")
            val splitController = getEmbeddingComponentMethod.invoke(windowExtensions) ?: return false

            val lockField = splitController.javaClass.getDeclaredField("mLock").apply { isAccessible = true }
            val taskContainersField = splitController.javaClass.getDeclaredField("mTaskContainers").apply { isAccessible = true }
            val transactionManagerField = splitController.javaClass.getDeclaredField("mTransactionManager").apply { isAccessible = true }

            val lock = lockField.get(splitController) ?: return false
            val taskContainers = taskContainersField.get(splitController) as? SparseArray<*> ?: return false
            val transactionManager = transactionManagerField.get(splitController) ?: return false

            synchronized(lock) {
                val taskContainer = taskContainers.get(activity.taskId) ?: return false

                // 1. In Wide (Split) Mode, look up the active SplitContainer's secondary container.
                val getSplitMethod = taskContainer.javaClass.getDeclaredMethod("getTopNonFinishingSplitContainer").apply {
                    isAccessible = true
                }
                val splitContainer = getSplitMethod.invoke(taskContainer)

                val targetContainer = if (splitContainer != null) {
                    val getSecondaryMethod = splitContainer.javaClass.getDeclaredMethod("getSecondaryContainer").apply {
                        isAccessible = true
                    }
                    getSecondaryMethod.invoke(splitContainer)
                } else {
                    // 2. In Narrow (Overlay) Mode, look up the top non-finishing content container
                    //    (includePin = false, includeOverlay = false).
                    val getTopTfMethod = taskContainer.javaClass.getDeclaredMethod(
                        "getTopNonFinishingTaskFragmentContainer",
                        Boolean::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType
                    ).apply {
                        isAccessible = true
                    }
                    getTopTfMethod.invoke(taskContainer, false, false)
                } ?: return false

                val getTokenMethod = targetContainer.javaClass.getDeclaredMethod("getTaskFragmentToken").apply {
                    isAccessible = true
                }
                val fragmentToken = getTokenMethod.invoke(targetContainer) as? IBinder ?: return false

                // 1. First try ActivityTaskManager / WindowOrganizerController direct application (Prebuilt / System app path)
                if (requestFocusViaAtm(fragmentToken)) {
                    Log.d(TAG, "Requested focus on secondary TaskFragment (token=$fragmentToken) via ActivityTaskManager")
                    return true
                }

                // 2. Second try direct TaskFragmentOrganizer application
                if (requestFocusDirect(fragmentToken)) {
                    Log.d(TAG, "Requested focus on secondary TaskFragment (token=$fragmentToken) directly without reflection")
                    return true
                }

                val wctClass = Class.forName("android.window.WindowContainerTransaction")
                val requestFocusMethod = wctClass.getDeclaredMethod("requestFocusOnTaskFragment", IBinder::class.java).apply {
                    isAccessible = true
                }

                val startTxMethod = transactionManager.javaClass.getDeclaredMethod("startNewTransaction").apply {
                    isAccessible = true
                }
                val txRecord = startTxMethod.invoke(transactionManager) ?: return false

                try {
                    val getTxMethod = txRecord.javaClass.getDeclaredMethod("getTransaction").apply {
                        isAccessible = true
                    }
                    val wct = getTxMethod.invoke(txRecord) ?: return false

                    requestFocusMethod.invoke(wct, fragmentToken)

                    val applyMethod = txRecord.javaClass.getDeclaredMethod("apply", Boolean::class.javaPrimitiveType).apply {
                        isAccessible = true
                    }
                    applyMethod.invoke(txRecord, false)
                } catch (txError: Throwable) {
                    try {
                        val abortMethod = txRecord.javaClass.getDeclaredMethod("abort").apply {
                            isAccessible = true
                        }
                        abortMethod.invoke(txRecord)
                    } catch (_: Throwable) {}
                    throw txError
                }

                Log.d(TAG, "Requested focus on secondary TaskFragment (token=$fragmentToken) from ${activity.javaClass.simpleName}")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request focus on secondary TaskFragment: $e", e)
            return false
        }
    }

    /**
     * Shifts WindowManager focus (`mFocusedApp` and `mCurrentFocus`) to the Pinned Overlay
     * `TaskFragmentContainer` when the overlay drawer is expanded, so pressing Back collapses
     * the expanded drawer first.
     */
    fun requestFocusOnOverlay(activity: Activity): Boolean {
        // 1. Try clean non-reflection lookup via public Extension API + ActivityTaskManager / TaskFragmentOrganizer
        try {
            val token = WindowExtensionsProvider.getWindowExtensions()
                ?.activityEmbeddingComponent
                ?.getActivityStackToken(AppConfig.TAG_PRIMARY_OVERLAY)
                ?.rawToken
            if (token != null) {
                if (requestFocusViaAtm(token)) {
                    Log.d(TAG, "Requested focus on overlay TaskFragment (token=$token) via ActivityTaskManager")
                    return true
                }
                if (requestFocusDirect(token)) {
                    Log.d(TAG, "Requested focus on overlay TaskFragment (token=$token) without reflection")
                    return true
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Direct non-reflection overlay focus attempt failed, using fallback: $e")
        }

        try {
            val providerClass = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
            val getWindowExtensionsMethod = providerClass.getDeclaredMethod("getWindowExtensions")
            val windowExtensions = getWindowExtensionsMethod.invoke(null) ?: return false
            val getEmbeddingComponentMethod = windowExtensions.javaClass.getMethod("getActivityEmbeddingComponent")
            val splitController = getEmbeddingComponentMethod.invoke(windowExtensions) ?: return false

            val lockField = splitController.javaClass.getDeclaredField("mLock").apply { isAccessible = true }
            val taskContainersField = splitController.javaClass.getDeclaredField("mTaskContainers").apply { isAccessible = true }
            val transactionManagerField = splitController.javaClass.getDeclaredField("mTransactionManager").apply { isAccessible = true }

            val lock = lockField.get(splitController) ?: return false
            val taskContainers = taskContainersField.get(splitController) as? SparseArray<*> ?: return false
            val transactionManager = transactionManagerField.get(splitController) ?: return false

            synchronized(lock) {
                val taskContainer = taskContainers.get(activity.taskId) ?: return false

                val getTopTfMethod = taskContainer.javaClass.getDeclaredMethod(
                    "getTopNonFinishingTaskFragmentContainer",
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                ).apply {
                    isAccessible = true
                }
                val overlayContainer = getTopTfMethod.invoke(taskContainer, true, true) ?: return false

                val getTokenMethod = overlayContainer.javaClass.getDeclaredMethod("getTaskFragmentToken").apply {
                    isAccessible = true
                }
                val fragmentToken = getTokenMethod.invoke(overlayContainer) as? IBinder ?: return false

                val wctClass = Class.forName("android.window.WindowContainerTransaction")
                val requestFocusMethod = wctClass.getDeclaredMethod("requestFocusOnTaskFragment", IBinder::class.java).apply {
                    isAccessible = true
                }

                val startTxMethod = transactionManager.javaClass.getDeclaredMethod("startNewTransaction").apply {
                    isAccessible = true
                }
                val txRecord = startTxMethod.invoke(transactionManager) ?: return false

                try {
                    val getTxMethod = txRecord.javaClass.getDeclaredMethod("getTransaction").apply {
                        isAccessible = true
                    }
                    val wct = getTxMethod.invoke(txRecord) ?: return false

                    requestFocusMethod.invoke(wct, fragmentToken)

                    val applyMethod = txRecord.javaClass.getDeclaredMethod("apply", Boolean::class.javaPrimitiveType).apply {
                        isAccessible = true
                    }
                    applyMethod.invoke(txRecord, false)
                } catch (txError: Throwable) {
                    try {
                        val abortMethod = txRecord.javaClass.getDeclaredMethod("abort").apply {
                            isAccessible = true
                        }
                        abortMethod.invoke(txRecord)
                    } catch (_: Throwable) {}
                    throw txError
                }

                Log.d(TAG, "Requested focus on overlay TaskFragment (token=$fragmentToken) from ${activity.javaClass.simpleName}")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request focus on overlay TaskFragment: $e", e)
            return false
        }
    }

    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
