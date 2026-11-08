package com.example.lifecycleapp

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.window.TaskFragmentOrganizer
import android.window.WindowContainerTransaction
import androidx.window.extensions.WindowExtensionsProvider
import androidx.window.extensions.embedding.ActivityStackAttributes
import com.example.core.item.AppConfig
import com.example.feature.home.CategoryStackManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Helper to manage WindowManager ActivityEmbedding Overlay Relative Bounds and TaskFragment Focus.
 *
 * 1. Top-Left Overlay Bounds (0% Reflection, No ATMS):
 *    Uses public OEM WindowExtensions APIs ([androidx.window.extensions.embedding.ActivityEmbeddingComponent],
 *    [ActivityStackAttributes.Builder.setRelativeBounds]) via `framework-stubs` to position the 80dp
 *    floating circular MENU button at the TOP-LEFT corner and expand/collapse the 35% drawer.
 * 2. TaskFragment Focus Control (No ATMS / No MANAGE_ACTIVITY_TASKS permission required):
 *    Uses [TaskFragmentOrganizer.applyTransaction] on the process's active organizer
 *    (`SplitController.mPresenter`, cached once) with a standalone
 *    [WindowContainerTransaction.requestFocusOnTaskFragment] without touching `SplitController.mLock`
 *    or `SplitController.mTransactionManager`.
 */
object OverlayBoundsHelper {
    private const val TAG = "OverlayBoundsHelper"
    private const val KEY_ACTIVITY_STACK_TOKEN =
        "androidx.window.extensions.embedding.ActivityStack.Token"

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

    /**
     * Computes the target relative bounds for the Narrow Mode overlay [ActivityStack].
     *
     * - When collapsed (`isExpanded == false`): returns an `80dp × 80dp` square at the top-left corner
     *   (`Rect(0, topOffset, buttonSizePx, topOffset + buttonSizePx)`).
     * - When expanded (`isExpanded == true`): returns the full parent Task bounds
     *   (`Rect(0, 0, taskWidth, taskHeight)`) so the left 35% drawer and the right 65% `viewDimScrim`
     *   cover the entire Task and intercept/consume outside touches.
     */
    fun calculateOverlayBounds(
        taskWidth: Int,
        taskHeight: Int,
        topOffset: Int,
        buttonSizePx: Int,
        isExpanded: Boolean
    ): Rect {
        return if (isExpanded) {
            Rect(0, 0, taskWidth, taskHeight)
        } else {
            Rect(0, topOffset, buttonSizePx, topOffset + buttonSizePx)
        }
    }

    /**
     * Registers an [ActivityEmbeddingComponent.setActivityStackAttributesCalculator] callback
     * using public OEM WindowExtensions APIs (0% reflection) to position the collapsed overlay
     * button at the top-left corner.
     */
    fun initCalculator(context: Context) {
        try {
            val embeddingComponent = WindowExtensionsProvider.getWindowExtensions()
                ?.activityEmbeddingComponent ?: return

            embeddingComponent.setActivityStackAttributesCalculator { params ->
                val taskBounds = params.parentContainerInfo.windowMetrics.bounds
                val density = context.resources.displayMetrics.density
                val buttonSizePx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * density).toInt()
                val topOffset = getStatusBarHeight(context)

                val targetBounds = calculateOverlayBounds(
                    taskWidth = taskBounds.width(),
                    taskHeight = taskBounds.height(),
                    topOffset = topOffset,
                    buttonSizePx = buttonSizePx,
                    isExpanded = SplitInitializer.isOverlayDrawerExpanded
                )

                ActivityStackAttributes.Builder()
                    .setRelativeBounds(targetBounds)
                    .build()
            }
            Log.d(TAG, "Successfully registered ActivityStackAttributesCalculator for top-left bounds")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register ActivityStackAttributesCalculator: $e", e)
        }
    }

    /**
     * Updates the overlay stack's relative bounds directly via public OEM WindowExtensions APIs
     * ([ActivityEmbeddingComponent.updateActivityStackAttributes]) with 0% reflection.
     */
    fun updateOverlayBounds(
        context: Context,
        overlayTag: String,
        isExpanded: Boolean
    ): Boolean {
        return try {
            val embeddingComponent = WindowExtensionsProvider.getWindowExtensions()
                ?.activityEmbeddingComponent ?: return false

            val token = embeddingComponent.getActivityStackToken(overlayTag) ?: return false
            val parentInfo = embeddingComponent.getParentContainerInfo(token) ?: return false
            val taskBounds = parentInfo.windowMetrics.bounds

            val density = context.resources.displayMetrics.density
            val buttonSizePx = (AppConfig.COLLAPSED_BUTTON_SIZE_DP * density).toInt()
            val topOffset = getStatusBarHeight(context)

            val targetBounds = calculateOverlayBounds(
                taskWidth = taskBounds.width(),
                taskHeight = taskBounds.height(),
                topOffset = topOffset,
                buttonSizePx = buttonSizePx,
                isExpanded = isExpanded
            )

            val attributes = ActivityStackAttributes.Builder()
                .setRelativeBounds(targetBounds)
                .build()

            embeddingComponent.updateActivityStackAttributes(token, attributes)
            Log.d(TAG, "Successfully updated overlay relative bounds to $targetBounds (isExpanded=$isExpanded)")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update overlay bounds: $e", e)
            false
        }
    }

    @Volatile
    private var cachedOrganizer: TaskFragmentOrganizer? = null

    /**
     * Applies focus via [TaskFragmentOrganizer.applyTransaction] on the process's active
     * [TaskFragmentOrganizer] (`SplitController.mPresenter`, looked up once and cached) using a
     * standalone [WindowContainerTransaction.requestFocusOnTaskFragment] without touching
     * `SplitController.mLock`, `mTaskContainers`, or `mTransactionManager`.
     *
     * Because `SplitPresenter` extends `android.window.TaskFragmentOrganizer` and owns all
     * embedded TaskFragments in this process, `ITaskFragmentOrganizerController.applyTransaction`
     * in `system_server` succeeds without requiring `MANAGE_ACTIVITY_TASKS`.
     */
    fun requestFocusDirect(fragmentToken: IBinder): Boolean {
        return try {
            val organizer = cachedOrganizer ?: run {
                val windowExtensions = WindowExtensionsProvider.getWindowExtensions() ?: return false
                val splitController = windowExtensions.activityEmbeddingComponent ?: return false
                val presenterField = splitController.javaClass.getDeclaredField("mPresenter").apply {
                    isAccessible = true
                }
                val presenter = presenterField.get(splitController) as? TaskFragmentOrganizer ?: return false
                cachedOrganizer = presenter
                presenter
            }
            val wct = WindowContainerTransaction()
            wct.requestFocusOnTaskFragment(fragmentToken)
            organizer.applyTransaction(
                wct,
                TaskFragmentOrganizer.TASK_FRAGMENT_TRANSIT_CHANGE,
                false
            )
            Log.d(TAG, "[Organizer Focus] Successfully applied requestFocusOnTaskFragment via TaskFragmentOrganizer.applyTransaction")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "[Organizer Focus] Direct focus request failed: $e", e)
            false
        }
    }

    /**
     * Atomically dismisses the active Category ActivityStack (whether containing internal
     * or external app activities) by delegating to [CategoryStackManager.dismissCurrentCategory].
     *
     * Utilizes 100% public Jetpack WindowManager API [androidx.window.embedding.ActivityEmbeddingController.finishActivityStacks]
     * without ANY reflection on internal SplitController fields.
     */
    @Suppress("UNUSED_PARAMETER")
    fun dismissCategoryContainer(taskId: Int = -1): Boolean {
        val app = try {
            LifecycleApp.instance
        } catch (_: Throwable) {
            null
        }
        return if (app != null) {
            CategoryStackManager.dismissCurrentCategory(app)
        } else {
            false
        }
    }

    /**
     * Shifts WindowManager focus (`mFocusedApp` and `mCurrentFocus`) from the Primary/Overlay
     * container to the active content `TaskFragment` (Category or Home) via
     * [WindowContainerTransaction.requestFocusOnTaskFragment].
     */
    @Suppress("UNUSED_PARAMETER")
    fun requestFocusOnSecondary(activity: Activity): Boolean {
        val contentToken = CategoryStackManager.getActiveContentToken() ?: return false
        return if (requestFocusDirect(contentToken)) {
            Log.d(TAG, "Requested focus on content TaskFragment (token=$contentToken) via TaskFragmentOrganizer")
            true
        } else {
            false
        }
    }

    /**
     * Shifts WindowManager focus (`mFocusedApp` and `mCurrentFocus`) to the Pinned Overlay
     * `TaskFragment` when the overlay drawer is expanded, so pressing Back collapses
     * the expanded drawer first.
     */
    fun requestFocusOnOverlay(activity: Activity): Boolean {
        val token = try {
            WindowExtensionsProvider.getWindowExtensions()
                ?.activityEmbeddingComponent
                ?.getActivityStackToken(AppConfig.TAG_PRIMARY_OVERLAY)
                ?.toBundle()
                ?.getBinder(KEY_ACTIVITY_STACK_TOKEN)
        } catch (_: Throwable) {
            null
        } ?: CategoryStackManager.getActivityStackToken(activity) ?: return false

        return if (requestFocusDirect(token)) {
            Log.d(TAG, "Requested focus on overlay TaskFragment (token=$token) via TaskFragmentOrganizer")
            true
        } else {
            false
        }
    }

    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
