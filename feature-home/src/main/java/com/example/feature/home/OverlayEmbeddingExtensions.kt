package com.example.feature.home

import android.app.ActivityOptions
import android.os.Bundle
import com.example.core.item.AppConfig

/**
 * Type-safe extension to configure whether an overlay ActivityStack should be associated
 * with the launching activity.
 *
 * Setting this to `false` instructs WindowManager Extensions to treat the overlay container
 * as an Always-On-Top Pinned Overlay decoupled from the launcher's lifecycle.
 */
fun Bundle.setOverlayAssociateWithLaunchingActivity(associate: Boolean = false): Bundle {
    putBoolean(AppConfig.KEY_OVERLAY_SHOULD_ASSOCIATE_WITH_LAUNCHING_ACTIVITY, associate)
    return this
}

fun ActivityOptions.setOverlayAssociateWithLaunchingActivity(associate: Boolean = false): ActivityOptions {
    toBundle().setOverlayAssociateWithLaunchingActivity(associate)
    return this
}
