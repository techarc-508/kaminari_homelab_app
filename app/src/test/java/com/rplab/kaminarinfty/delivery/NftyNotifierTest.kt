package com.rplab.kaminarinfty.delivery

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NftyNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: NftyNotifier
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notifier = NftyNotifier(context)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    private fun message(
        id: String = "msg_1",
        title: String? = "Disk alert",
        body: String = "Pool tank is 91% full",
        priority: NftyPriority = NftyPriority.DEFAULT,
    ) = NftyMessage(
        id = id,
        time = 1_700_000_000_000,
        topic = "ops",
        title = title,
        message = body,
        priority = priority,
    )

    @Test
    fun `all notification channels are created with distinct importance`() {
        notifier.ensureChannels()

        val channels = manager.notificationChannels.associateBy { it.id }
        assertNotNull(channels[NftyNotifier.CHANNEL_MESSAGES])
        assertNotNull(channels[NftyNotifier.CHANNEL_URGENT])
        assertNotNull(channels[NftyNotifier.CHANNEL_LIVE])
        assertTrue(
            channels.getValue(NftyNotifier.CHANNEL_URGENT).importance >
                channels.getValue(NftyNotifier.CHANNEL_MESSAGES).importance
        )
        assertTrue(
            channels.getValue(NftyNotifier.CHANNEL_LIVE).importance <
                channels.getValue(NftyNotifier.CHANNEL_MESSAGES).importance
        )
    }

    @Test
    fun `message notification carries title, body and a deep link`() {
        val notification = notifier.buildMessageNotification(message())

        assertEquals(
            "Disk alert",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            "Pool tank is 91% full",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertNotNull(notification.contentIntent)
        assertEquals(NftyNotifier.CHANNEL_MESSAGES, notification.channelId)
    }

    @Test
    fun `urgent messages route to the alerting channel`() {
        val urgent = notifier.buildMessageNotification(message(priority = NftyPriority.URGENT))
        val high = notifier.buildMessageNotification(message(priority = NftyPriority.HIGH))
        val normal = notifier.buildMessageNotification(message(priority = NftyPriority.DEFAULT))

        assertEquals(NftyNotifier.CHANNEL_URGENT, urgent.channelId)
        assertEquals(NftyNotifier.CHANNEL_URGENT, high.channelId)
        assertEquals(NftyNotifier.CHANNEL_MESSAGES, normal.channelId)
    }

    @Test
    fun `lock screen public version hides the message body`() {
        val publicVersion = notifier.buildMessageNotification(message()).publicVersion
        assertNotNull(publicVersion)
        assertEquals(
            "#ops",
            publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            "New notification",
            publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `live notification is ongoing and tied to the live channel`() {
        val live = notifier.buildLiveNotification(
            DeliveryStatus(
                engine = DeliveryEngine.INSTANT,
                phase = DeliveryPhase.LIVE,
                topicCount = 2,
                authState = com.rplab.kaminarinfty.model.AuthState.AUTHENTICATED,
            )
        )

        assertEquals(NftyNotifier.CHANNEL_LIVE, live.channelId)
        assertTrue(live.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(live.contentIntent)
        assertEquals(
            "Live delivery active — 2 topics",
            live.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `live notification never claims live while reconnecting or stopped`() {
        val reconnecting = notifier.buildLiveNotification(
            DeliveryStatus(engine = DeliveryEngine.INSTANT, phase = DeliveryPhase.RECONNECTING)
        )
        val timedOut = notifier.buildLiveNotification(
            DeliveryStatus(engine = DeliveryEngine.INSTANT, phase = DeliveryPhase.TIMED_OUT)
        )

        assertEquals(
            "Reconnecting…",
            reconnecting.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(
            "Live delivery paused by the Android foreground-service limit",
            timedOut.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun `delivery stopped notice is posted on the message channel`() {
        shadowOf(context as android.app.Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)

        notifier.postDeliveryStoppedNotification("Live delivery paused", "Android limit reached")

        val posted = shadowOf(manager).allNotifications
        assertTrue(
            posted.any {
                it.extras.getCharSequence(Notification.EXTRA_TITLE).toString() ==
                    "Live delivery paused"
            }
        )
    }

    @Test
    fun `posted message notifications use stable, reserved IDs`() {
        shadowOf(context as android.app.Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)

        notifier.postMessageNotification(message(id = "msg_42"))

        val posted = shadowOf(manager).allNotifications
        assertEquals(1, posted.size)
        assertTrue(NftyNotifier.notificationIdFor("msg_42") >= 10_000)
        assertEquals(
            NftyNotifier.notificationIdFor("msg_42"),
            NftyNotifier.notificationIdFor("msg_42"),
        )
    }
}
