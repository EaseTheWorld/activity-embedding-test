package com.example.lifecycleapp

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.SparseArray
import com.example.core.item.AppConfig
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
