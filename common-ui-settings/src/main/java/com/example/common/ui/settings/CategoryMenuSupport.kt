package com.example.common.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Build
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
 * Extracts the host-provided category menu drawer [Intent] from [intent].
 */
fun extractMenuIntent(
    intent: Intent?,
    extraKey: String = AppConfig.EXTRA_MENU_INTENT
): Intent? {
    if (intent == null) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(extraKey, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(extraKey)
    }
}

/**
 * Launches the host-provided category menu drawer [menuIntent] on top of [activity].
 */
fun launchMenuDrawer(
    activity: Activity,
    menuIntent: Intent
) {
    try {
        activity.startActivity(Intent(menuIntent))
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    } catch (e: Exception) {
        Log.e(MENU_SUPPORT_TAG, "Failed to launch menu drawer intent=$menuIntent: $e")
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
