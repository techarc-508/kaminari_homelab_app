package com.rplab.kaminarinfty.delivery

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.rplab.kaminarinfty.model.NftyMessage
import java.util.concurrent.TimeUnit

/**
 * Platform boundary for background delivery so the ViewModel can be tested
 * without Android services or WorkManager.
 */
interface DeliveryController {
    fun ensureNotificationChannels()

    /** Starts (or restarts) the Instant foreground service. False if Android refused. */
    fun startInstant(): Boolean

    fun stopInstant()

    fun scheduleBatterySaver()

    fun cancelBatterySaver()

    /** Tells the user Instant mode did not resume after reboot (Android 15+ rule). */
    fun postInstantResumeNotification()

    fun postMessageNotification(message: NftyMessage)
}

class AndroidDeliveryController(context: Context) : DeliveryController {

    private val appContext = context.applicationContext
    private val notifier = NftyNotifier(appContext)

    override fun ensureNotificationChannels() = notifier.ensureChannels()

    override fun startInstant(): Boolean = runCatching {
        notifier.ensureChannels()
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, InstantForegroundService::class.java),
        )
    }.isSuccess

    override fun stopInstant() {
        runCatching {
            appContext.stopService(Intent(appContext, InstantForegroundService::class.java))
        }
    }

    override fun scheduleBatterySaver() {
        val request = PeriodicWorkRequestBuilder<BatterySaverWorker>(
            BATTERY_SAVER_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            BatterySaverWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancelBatterySaver() {
        WorkManager.getInstance(appContext).cancelUniqueWork(BatterySaverWorker.UNIQUE_NAME)
    }

    override fun postInstantResumeNotification() {
        notifier.postInstantResumeNotification()
    }

    override fun postMessageNotification(message: NftyMessage) {
        notifier.postMessageNotification(message)
    }

    companion object {
        /** WorkManager's floor for periodic work; delivery is explicitly inexact. */
        const val BATTERY_SAVER_INTERVAL_MINUTES = 15L
    }
}
