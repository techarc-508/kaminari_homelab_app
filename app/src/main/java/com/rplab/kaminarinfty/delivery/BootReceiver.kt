package com.rplab.kaminarinfty.delivery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rplab.kaminarinfty.KaminariApp
import kotlinx.coroutines.launch

/**
 * Restores the persisted delivery mode after a reboot.
 *
 * Battery Saver is re-scheduled through WorkManager. Instant mode cannot be
 * restarted here: Android 15+ forbids launching a dataSync foreground service
 * from BOOT_COMPLETED, so the user gets a real notification asking them to open
 * the app — never a silent gap.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? KaminariApp ?: return

        val pending = goAsync()
        // App-owned scope: bounded by the goAsync() result and independent of
        // whichever component happens to be alive at boot.
        app.container.appScope.launch {
            try {
                BootDeliveryRestorer(
                    preferences = app.container.preferences,
                    controller = AndroidDeliveryController(context),
                ).restore()
            } catch (_: Exception) {
                // Never crash the boot path. WorkManager restores periodic work
                // on its own even if this process is not allowed to schedule.
            } finally {
                pending.finish()
            }
        }
    }
}
