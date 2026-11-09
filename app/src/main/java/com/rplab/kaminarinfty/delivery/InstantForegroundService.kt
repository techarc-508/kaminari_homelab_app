package com.rplab.kaminarinfty.delivery

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import com.rplab.kaminarinfty.KaminariApp
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.data.repository.NftyRepository
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.StreamUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Instant delivery mode: a user-visible foreground service owns the live SSE
 * connection, persists every message to Room and posts notifications.
 *
 * While this service is the stream owner the ViewModel deliberately does not
 * open a second subscription. The service publishes its real lifecycle through
 * [DeliveryStatusStore] so the UI never shows "offline" over a live connection
 * and never shows "live" over a dead one.
 *
 * Android platform limits are documented, not avoided: `dataSync` foreground
 * services are capped at 6 hours in a rolling 24-hour window on Android 15+.
 * [onTimeout] stops the service before the system's fatal deadline and hands
 * delivery over to Battery Saver.
 */
class InstantForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engineJob: Job? = null
    private var streamRepository: NftyRepository? = null
    private val notifier by lazy { NftyNotifier(this) }

    private val container by lazy { (application as KaminariApp).container }
    private val statusStore by lazy { container.deliveryStatusStore }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Publish STARTING before startForeground so the first notification
        // (and any status observer) describes the real state.
        statusStore.publish(
            engine = DeliveryEngine.INSTANT,
            phase = DeliveryPhase.STARTING,
        )

        if (!startForegroundSafely()) {
            val detail = "Instant mode could not start its foreground service. " +
                "Android may be restricting background starts; open the app and try again."
            statusStore.publish(
                engine = DeliveryEngine.INSTANT,
                phase = DeliveryPhase.FAILED,
                detail = detail,
            )
            notifier.postDeliveryStoppedNotification("Instant mode did not start", detail)
            stopSelf()
            return START_NOT_STICKY
        }

        // A fresh start command means "re-read profile/topics" (mode restart).
        engineJob?.cancel()
        streamRepository?.close()
        streamRepository = null
        engineJob = scope.launch { runEngine() }
        return START_STICKY
    }

    private fun startForegroundSafely(): Boolean = runCatching {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NftyNotifier.LIVE_NOTIFICATION_ID,
            notifier.buildLiveNotification(statusStore.status.value),
            type,
        )
    }.isSuccess

    /**
     * Android 15+ only. The system calls this after the app has spent its
     * `dataSync` foreground budget (6 hours in a rolling 24-hour window). The
     * app has seconds to stop or the system throws a fatal
     * `RemoteServiceException`. We stop immediately, record an honest state and
     * fall back to Battery Saver catch-up. The budget itself is not extended or
     * avoided; it resets only after the app comes back to the foreground.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        val detail = "Android reached its 6-hour dataSync limit for this app. " +
            "Live delivery was paused and Battery Saver catch-up was scheduled. " +
            "Open Kaminari to resume Instant mode."
        statusStore.publish(
            engine = DeliveryEngine.INSTANT,
            phase = DeliveryPhase.TIMED_OUT,
            detail = detail,
        )
        notifier.postDeliveryStoppedNotification("Live delivery paused", detail)

        engineJob?.cancel()
        engineJob = null
        streamRepository?.close()
        streamRepository = null

        // Persist and schedule outside the service scope: onDestroy cancels it.
        container.appScope.launch {
            InstantTimeoutFallback(container.preferences, container.deliveryController)
                .switchToBatterySaver()
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private suspend fun runEngine() {
        // The persisted mode is authoritative: if the user switched away while
        // this service was starting, stop instead of delivering behind their back.
        val mode = container.preferences.data.first().deliveryMode
        if (mode != DeliveryMode.INSTANT) {
            stopSelf()
            return
        }

        val profileId = container.preferences.activeProfileId()
        val config = profileId?.let { container.profileRepository.loadConfig(it) }
        val topics = profileId?.let { container.profileRepository.subscribedTopicNames(it) }.orEmpty()
        if (profileId == null || config == null || topics.isEmpty()) {
            failAndStop(
                "Instant mode needs an active profile with at least one subscribed topic.",
            )
            return
        }

        val repository = NftyRepository().also { streamRepository = it }
        publish(DeliveryPhase.CATCHING_UP, topicCount = topics.size)

        try {
            coroutineScope {
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    repository.updates.collect { update ->
                        if (update is StreamUpdate.NewMessage) {
                            val inserted = container.messageRepository.store(profileId, update.message)
                            if (inserted) notifier.postMessageNotification(update.message)
                        }
                    }
                }
                val statusObserver = launch {
                    observeRepository(repository, topics.size)
                }
                try {
                    val since = container.messageRepository.latestCursor(profileId)
                        ?: NtfyEndpoint.DEFAULT_SINCE
                    withTimeoutOrNull(CATCH_UP_TIMEOUT_MS) {
                        repository.catchUp(config, topics, since)
                    }
                    repository.startSubscription(config, topics) {
                        container.messageRepository.latestCursor(profileId)
                            ?: NtfyEndpoint.DEFAULT_SINCE
                    }
                    publish(DeliveryPhase.CONNECTING, topicCount = topics.size)
                    awaitCancellation()
                } finally {
                    statusObserver.cancel()
                    collector.cancel()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failAndStop(error.message ?: "Live delivery stopped unexpectedly")
        }
    }

    /**
     * Mirrors the service-owned stream state into [DeliveryStatusStore] and, on
     * a permanent stop, notifies the user and shuts the service down. Terminal
     * failures are values in [NftyRepository], not exceptions, so observing
     * [NftyRepository.isTerminal] is what prevents a dead stream from keeping a
     * "live delivery" notification alive.
     */
    private suspend fun observeRepository(repository: NftyRepository, topicCount: Int) {
        combine(
            repository.connectionState,
            repository.isTerminal,
            repository.lastError,
        ) { state, terminal, error -> Triple(state, terminal, error) }
            .collect { (state, terminal, error) ->
                if (terminal) {
                    if (statusStore.status.value.isTerminal) return@collect
                    val phase = when (error?.first) {
                        NftyErrorCategory.AUTH -> DeliveryPhase.AUTH_FAILED
                        NftyErrorCategory.FORBIDDEN -> DeliveryPhase.PERMISSION_DENIED
                        else -> DeliveryPhase.FAILED
                    }
                    val detail = error?.second ?: "Live subscription stopped"
                    statusStore.publish(
                        engine = DeliveryEngine.INSTANT,
                        phase = phase,
                        detail = detail,
                        topicCount = topicCount,
                    )
                    notifier.postDeliveryStoppedNotification("Live delivery stopped", detail)
                    stopSelf()
                    return@collect
                }

                val phase = when (state) {
                    AuthState.AUTHENTICATED, AuthState.GUEST -> DeliveryPhase.LIVE
                    AuthState.CONNECTING -> DeliveryPhase.CONNECTING
                    AuthState.RECONNECTING -> DeliveryPhase.RECONNECTING
                    AuthState.AUTHENTICATION_FAILED -> DeliveryPhase.AUTH_FAILED
                    AuthState.PERMISSION_DENIED -> DeliveryPhase.PERMISSION_DENIED
                    else -> null
                } ?: return@collect

                publish(phase, topicCount = topicCount, authState = state)
            }
    }

    /** Records a terminal failure once, tells the user, and stops the service. */
    private fun failAndStop(detail: String) {
        statusStore.publish(
            engine = DeliveryEngine.INSTANT,
            phase = DeliveryPhase.FAILED,
            detail = detail,
        )
        notifier.postDeliveryStoppedNotification("Live delivery stopped", detail)
        stopSelf()
    }

    /**
     * Publishes only real changes and refreshes the foreground notification.
     * The foreground notification is the user's only always-visible evidence of
     * what the service is doing, so it must not lie.
     */
    private fun publish(
        phase: DeliveryPhase,
        detail: String? = null,
        topicCount: Int = 0,
        authState: AuthState = AuthState.DISCONNECTED,
    ) {
        val current = statusStore.status.value
        val next = DeliveryStatus(
            engine = DeliveryEngine.INSTANT,
            phase = phase,
            detail = detail,
            topicCount = topicCount,
            authState = authState,
        )
        if (current.copy(sequence = 0) == next) return
        val stamped = statusStore.update(next)
        notifier.updateLiveNotification(stamped)
    }

    override fun onDestroy() {
        engineJob?.cancel()
        engineJob = null
        streamRepository?.close()
        streamRepository = null
        scope.cancel()
        // Do not overwrite a terminal reason (timeout/auth/start failure) with
        // a generic idle state: the UI must keep showing why delivery stopped.
        if (!statusStore.status.value.isTerminal) {
            statusStore.publish(
                engine = DeliveryEngine.NONE,
                phase = DeliveryPhase.IDLE,
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CATCH_UP_TIMEOUT_MS = 30_000L
    }
}
