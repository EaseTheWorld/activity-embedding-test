package com.example.lifecycleapp

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Lifecycle-scoped Foreground Service kept alive strictly while [FloatingMenuManager] is
 * attached in Narrow Mode.
 *
 * Why this is needed on Android 12–17 (verified on Pixel Tablet API 37):
 * When the floating handle hides via `View.GONE` (while leaving to Home or another app),
 * WindowManager drops `hasOverlayUi` and OomAdjuster demotes the host process to `CACHED`
 * (`oom_adj >= 900`), causing `CachedAppsFreezer` to freeze the host PID ~10 seconds later.
 * If the user subsequently returns directly to an external plugin Activity (e.g.
 * `LightSettingsActivity` running in its own process) via Back or Recents, only the plugin PID
 * unfreezes while the host PID stays frozen — blocking any polling loop, `TaskStackListener`, or
 * `IUidObserver` callback from restoring the floating handle.
 *
 * Keeping this minimal `specialUse` foreground service active while `isAttached == true`
 * maintains `PERCEPTIBLE` (`oom_adj ~200`), preventing freezer suspension until [detach] (Wide
 * Mode transition) or [onTaskRemoved] (user swipes app from Recents).
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlayService"
        private const val CHANNEL_ID = "floating_overlay_observer"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, FloatingOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to start FloatingOverlayService now: $t")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, FloatingOverlayService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to stop FloatingOverlayService: $t")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            FloatingMenuManager.onKeepAliveServiceStateChanged(true)
            Log.d(TAG, "Foreground observer keep-alive active (isAttached=${FloatingMenuManager.isCurrentlyAttached()})")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enter foreground: $t", t)
            FloatingMenuManager.onKeepAliveServiceStateChanged(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!FloatingMenuManager.isCurrentlyAttached()) {
            Log.d(TAG, "Overlay already detached before onStartCommand; stopping service")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val remainingTasks = am?.appTasks?.size ?: 0
        Log.i(TAG, "onTaskRemoved: remainingTasks=$remainingTasks")
        if (remainingTasks == 0) {
            FloatingMenuManager.detach()
            stopSelf()
        }
    }

    override fun onDestroy() {
        FloatingMenuManager.onKeepAliveServiceStateChanged(false)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping foreground notification: $t")
        }
        super.onDestroy()
        Log.d(TAG, "Destroyed FloatingOverlayService")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Quick Edge Handle",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps the Narrow Mode quick edge handle responsive across categories."
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        }
        return builder
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Quick edge handle active")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }
}
