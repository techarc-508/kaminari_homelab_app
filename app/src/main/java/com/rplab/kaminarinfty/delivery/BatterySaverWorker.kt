package com.rplab.kaminarinfty.delivery

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rplab.kaminarinfty.KaminariApp
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.data.repository.NftyRepository
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.StreamUpdate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Battery Saver mode: one bounded `poll=1` catch-up per WorkManager period.
 *
 * This is explicitly best-effort. WorkManager scheduling is inexact under Doze,
 * so delivery may lag by minutes or longer. Each message is deduplicated by the
 * durable Room index, so a repeated poll never duplicates history or
 * notifications.
 */
class BatterySaverWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? KaminariApp ?: return Result.success()
        val container = app.container

        // The mode may have changed while this work was queued.
        if (container.preferences.data.first().deliveryMode != DeliveryMode.BATTERY_SAVER) {
            return Result.success()
        }

        val profileId = container.preferences.activeProfileId() ?: return Result.success()
        val config = container.profileRepository.loadConfig(profileId) ?: return Result.success()
        val topics = container.profileRepository.subscribedTopicNames(profileId)
        if (topics.isEmpty()) return Result.success()

        val notifier = NftyNotifier(applicationContext)
        notifier.ensureChannels()

        val repository = NftyRepository()
        var stored = 0
        try {
            coroutineScope {
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    repository.updates.collect { update ->
                        if (update is StreamUpdate.NewMessage) {
                            if (container.messageRepository.store(profileId, update.message)) {
                                stored++
                                notifier.postMessageNotification(update.message)
                            }
                        }
                    }
                }
                try {
                    val since = container.messageRepository.latestCursor(profileId)
                        ?: NtfyEndpoint.DEFAULT_SINCE
                    withTimeoutOrNull(POLL_TIMEOUT_MS) {
                        repository.catchUp(config, topics, since)
                    }
                } finally {
                    collector.cancel()
                }
            }
        } finally {
            repository.close()
        }

        // A failed poll is not retried immediately: the next scheduled period is
        // the retry. Result.retry() would add extra attempts on top of the period.
        return Result.success(workDataOf(KEY_STORED to stored))
    }

    companion object {
        const val UNIQUE_NAME = "kaminari_battery_saver"
        const val POLL_TIMEOUT_MS = 60_000L
        const val KEY_STORED = "stored"
    }
}
