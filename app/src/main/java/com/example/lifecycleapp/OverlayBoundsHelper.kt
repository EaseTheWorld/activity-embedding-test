package com.example.lifecycleapp

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.example.core.item.AppConfig
import java.lang.reflect.Proxy

/**
 * Helper to manage WindowManager ActivityEmbedding Overlay Relative Bounds.
 *
 * Jetpack WindowManager's public [androidx.window.embedding.EmbeddingBounds] only supports
 * 4 centered alignments (ALIGN_LEFT centers vertically, ALIGN_TOP centers horizontally).
 * To position the 80dp floating circular MENU button at the TOP-LEFT corner without blocking
 * touch events elsewhere, this helper interacts with Android WindowManager Extension's
 * [androidx.window.extensions.embedding.ActivityEmbeddingComponent]:
 * 1. Registers an [ActivityStackAttributesCalculator] dynamic proxy to provide top-left bounds
 *    whenever WindowManager recalculates task/stack attributes.
 * 2. Directly updates relative bounds via [updateActivityStackAttributes] on state transitions.
 */
object OverlayBoundsHelper {
    private const val TAG = "OverlayBoundsHelper"

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

    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
