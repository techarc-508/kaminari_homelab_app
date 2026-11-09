package com.rplab.kaminarinfty.delivery

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rplab.kaminarinfty.MainActivity
import com.rplab.kaminarinfty.R
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority

/**
 * Builds channel-scoped, deep-linked Android notifications for received
 * messages and for the Instant-mode foreground service.
 *
 * Privacy: message notifications are `VISIBILITY_PRIVATE` with a minimal public
 * version, so lock screens show the topic instead of the message body unless the
 * device is unlocked.
 */
class NftyNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_URGENT,
                "High priority alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Urgent and high-priority ntfy messages"
            },
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Notifications",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Normal and low-priority ntfy messages"
            },
            NotificationChannel(
                CHANNEL_LIVE,
                "Live connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent status while Instant mode is running"
                setShowBadge(false)
            },
        )
        system.createNotificationChannels(channels)
    }

    fun notificationsEnabled(): Boolean = manager.areNotificationsEnabled()

    /** Notification permission can be revoked at any time, including mid-session. */
    private fun canPost(): Boolean {
        if (!notificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Posts a message notification; safe to call when permission is denied. */
    fun postMessageNotification(message: NftyMessage) {
        if (!canPost()) return
        ensureChannels()
        try {
            manager.notify(notificationIdFor(message.id), buildMessageNotification(message))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    fun buildMessageNotification(message: NftyMessage): Notification {
        val urgent = message.priority.isAlerting()
        val channel = if (urgent) CHANNEL_URGENT else CHANNEL_MESSAGES
        val title = message.title?.takeIf { it.isNotBlank() } ?: "#${message.topic}"
        val body = message.message.ifBlank { "(no message body)" }

        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(message.time)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(
                if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(buildPublicNotification(message))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(message.id))
            .build()
    }

    /** Lock-screen-safe stub: topic only, never the message body. */
    private fun buildPublicNotification(message: NftyMessage): Notification =
        NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("#${message.topic}")
            .setContentText("New notification")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    /** Ongoing notification shown while the Instant foreground service runs. */
    fun buildLiveNotification(status: DeliveryStatus): Notification =
        NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kaminari live connection")
            .setContentText(status.displayText())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(null))
            .build()

    /**
     * Replaces the foreground notification after a state change. The foreground
     * notification itself does not require POST_NOTIFICATIONS, so this is not
     * gated on [canPost]; a revoked permission still throws and is swallowed.
     */
    fun updateLiveNotification(status: DeliveryStatus) {
        try {
            manager.notify(LIVE_NOTIFICATION_ID, buildLiveNotification(status))
        } catch (_: SecurityException) {
            // Permission revoked mid-session; the stream and service stay alive.
        }
    }

    /**
     * User-visible terminal notice after the foreground service stops (timeout,
     * auth failure, foreground-start refusal). Deliberately not ongoing.
     */
    fun postDeliveryStoppedNotification(title: String, text: String) {
        if (!canPost()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(null))
            .build()
        try {
            manager.notify(DELIVERY_NOTICE_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    /**
     * Android 15+ does not allow a dataSync foreground service to be started
     * from BOOT_COMPLETED, so a reboot drops Instant mode until the user opens
     * the app. Say that plainly instead of silently failing to deliver.
     */
    fun postInstantResumeNotification() {
        if (!canPost()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Instant mode paused after reboot")
            .setContentText("Open Kaminari to resume live delivery.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(null))
            .build()
        try {
            manager.notify(RESUME_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    fun openAppIntent(messageId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (messageId != null) putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, messageId?.hashCode() ?: 0, intent, flags)
    }

    companion object {
        const val CHANNEL_MESSAGES = "kaminari_messages"
        const val CHANNEL_URGENT = "kaminari_urgent"
        const val CHANNEL_LIVE = "kaminari_live"

        const val EXTRA_MESSAGE_ID = "com.rplab.kaminarinfty.extra.MESSAGE_ID"

        const val LIVE_NOTIFICATION_ID = 1001
        const val RESUME_NOTIFICATION_ID = 1002
        const val DELIVERY_NOTICE_ID = 1003

        /** Message notifications live above the reserved service IDs. */
        fun notificationIdFor(messageId: String): Int {
            val hash = messageId.hashCode() and 0x3FFFFFFF
            return MESSAGE_ID_OFFSET + hash
        }

        private const val MESSAGE_ID_OFFSET = 10_000
    }
}

/** Urgent/high use the alerting channel; everything else stays quiet. */
fun NftyPriority.isAlerting(): Boolean = this == NftyPriority.URGENT || this == NftyPriority.HIGH
