package com.rplab.kaminarinfty

import android.app.Application
import android.content.Context
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.preferences.DataStoreAppPreferences
import com.rplab.kaminarinfty.data.preferences.KeystoreSecretStore
import com.rplab.kaminarinfty.data.preferences.SecretStore
import com.rplab.kaminarinfty.data.repository.DefaultProfileRepository
import com.rplab.kaminarinfty.data.repository.MessageRepository
import com.rplab.kaminarinfty.data.repository.NftyRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.data.repository.RoomMessageRepository
import com.rplab.kaminarinfty.delivery.AndroidDeliveryController
import com.rplab.kaminarinfty.delivery.DeliveryController
import com.rplab.kaminarinfty.delivery.DeliveryStatusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal manual DI container; one instance per process. */
class AppContainer(context: Context) {
    val database: KaminariDatabase = KaminariDatabase.build(context)
    val preferences: AppPreferences = DataStoreAppPreferences(context)
    val secretStore: SecretStore = KeystoreSecretStore(context)
    val messageRepository: MessageRepository = RoomMessageRepository(database)
    val profileRepository: ProfileRepository =
        DefaultProfileRepository(database, secretStore, preferences)
    val streamRepository: NftyRepository = NftyRepository()
    val deliveryController: DeliveryController = AndroidDeliveryController(context)

    /** Process-wide delivery state shared by the service, receiver and ViewModel. */
    val deliveryStatusStore: DeliveryStatusStore = DeliveryStatusStore()

    /**
     * App-owned scope for short, bounded background work (boot restore and the
     * Instant timeout fallback). SupervisorJob keeps one failure from cancelling
     * unrelated work; it lives for the process, not for a screen.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class KaminariApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
