package com.appsfolder.livebridge.liveupdate

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlin.math.abs

/**
 * Extension functions for LiveUpdateNotifier to handle lock state changes and refresh logic.
 * This separates concerns and keeps the main notifier class focused on its primary purpose.
 */

/**
 * Refresh all active Live Updates after device unlock.
 *
 * This function:
 * 1. Retrieves all active notifications from the system
 * 2. Re-processes them through the normal mirror logic
 * 3. This causes them to be rebuilt with current lock state (unlocked)
 * 4. Real notification content is shown instead of redacted
 * 5. Handles edge cases like notifications updated while locked
 *
 * Safe to call even if there are no active notifications.
 */
fun LiveUpdateNotifier.refreshAllMirrorsAfterUnlock(context: Context) {
    if (!ConverterPrefs(context).getConverterEnabled()) {
        Log.d("LiveUpdateNotifier", "Converter disabled, skipping refresh")
        return
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

    val activeNotifications = try {
        notificationManager.activeNotifications
    } catch (e: Throwable) {
        Log.e("LiveUpdateNotifier", "Failed to get active notifications for unlock refresh", e)
        return
    }

    if (activeNotifications.isEmpty()) {
        Log.d("LiveUpdateNotifier", "No active notifications to refresh")
        return
    }

    Log.i("LiveUpdateNotifier", "Refreshing ${activeNotifications.size} active notifications after unlock")

    val prefs = ConverterPrefs(context)
    val manager = NotificationManagerCompat.from(context)
    var refreshCount = 0

    for (statusBarNotification in activeNotifications) {
        try {
            // Skip our own notifications
            if (statusBarNotification.packageName == context.packageName) {
                continue
            }

            // Skip if not a mirror channel
            if (!isMirrorNotificationChannel(statusBarNotification.notification.channelId)) {
                continue
            }

            // Re-process through normal mirror logic
            // This will rebuild the notification with current lock state (unlocked)
            val result = maybeMirror(context, prefs, statusBarNotification)
            if (result.mirrored) {
                refreshCount++
                Log.d("LiveUpdateNotifier", "Refreshed: ${statusBarNotification.key}")
            }
        } catch (e: Throwable) {
            Log.e("LiveUpdateNotifier", "Failed to refresh notification: ${statusBarNotification.key}", e)
        }
    }

    Log.i("LiveUpdateNotifier", "Completed refresh of $refreshCount notifications after unlock")
}

/**
 * Check if a channel ID is a mirror notification channel.
 * Internal helper for lock refresh logic.
 */
private fun isMirrorNotificationChannel(channelId: String?): Boolean {
    val normalized = channelId?.trim().orEmpty()
    if (normalized.isEmpty()) {
        return false
    }

    return normalized in setOf(
        "livebridge_promoted_updates",
        "livebridge_progress_notifications",
        "livebridge_otp_codes",
        "livebridge_smart_conversions",
        "livebridge_media_playback",
        "livebridge_calls",
        "livebridge_network_connections",
        "livebridge_miscellaneous_conversions",
        "livebridge_bypass_applications"
    )
}
