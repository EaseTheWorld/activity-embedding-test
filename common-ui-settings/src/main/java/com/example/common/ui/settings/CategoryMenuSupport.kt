package com.example.common.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ComponentInfo
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.item.AppConfig

private const val MENU_SUPPORT_TAG = "CategoryMenuSupport"

/**
 * Extracts the host-provided category menu drawer target [ComponentName] from [intent].
 * Supports [ComponentName], [ComponentInfo] (e.g. ActivityInfo), or flattened String extras.
 */
fun extractMenuComponent(
    intent: Intent?,
    extraKey: String = AppConfig.EXTRA_MENU_COMPONENT
): ComponentName? {
    if (intent == null) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(extraKey, ComponentName::class.java)?.let { return it }
        intent.getParcelableExtra(extraKey, ComponentInfo::class.java)?.let { info ->
            if (!info.packageName.isNullOrEmpty() && !info.name.isNullOrEmpty()) {
                return ComponentName(info.packageName, info.name)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        when (val extra = intent.getParcelableExtra<Parcelable>(extraKey)) {
            is ComponentName -> return extra
            is ComponentInfo -> {
                if (!extra.packageName.isNullOrEmpty() && !extra.name.isNullOrEmpty()) {
                    return ComponentName(extra.packageName, extra.name)
                }
            }
        }
    }
    intent.getStringExtra(extraKey)?.let { flattened ->
        return ComponentName.unflattenFromString(flattened)
    }
    return null
}

/**
 * Launches the host category menu drawer identified by [menuComponent] on top of [activity].
 */
fun launchMenuDrawer(
    activity: Activity,
    menuComponent: ComponentName,
    categoryId: String? = null
) {
    val menuIntent = Intent().apply {
        component = menuComponent
        if (!categoryId.isNullOrEmpty()) {
            putExtra("extra_category_id", categoryId)
        }
    }
    try {
        activity.startActivity(menuIntent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    } catch (e: Exception) {
        Log.e(MENU_SUPPORT_TAG, "Failed to launch menu drawer component=$menuComponent: $e")
    }
}

/**
 * Shared top-left header for category main screens (`Home`, `Vehicle Settings`, `Doors`, `Seats`, etc.).
 * In Narrow Mode ([showMenuButton] = true), renders an inline circular pill containing a circular
 * menu icon badge + category title (not a floating overlay) that invokes [onMenuClick].
 * In Wide Mode ([showMenuButton] = false), hides the menu button (and hides title when [showTitle] = false).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryMainHeader(
    title: String,
    subtitle: String? = null,
    showMenuButton: Boolean,
    showTitle: Boolean = true,
    onMenuClick: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!showTitle && onNavigateBack == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF1E1E2E)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (showTitle) {
                if (showMenuButton && onMenuClick != null) {
                    Surface(
                        onClick = onMenuClick,
                        shape = CircleShape,
                        color = Color(0xFFEDE7F6),
                        border = BorderStroke(1.dp, Color(0xFFD0BCFF)),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .semantics { testTagsAsResourceId = true }
                            .testTag("btnMenu")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 18.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6750A4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1E2E)
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E2E)
                        )
                    )
                }
            }
        }

        if (showTitle && !subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6B7280))
            )
        }
    }
}
