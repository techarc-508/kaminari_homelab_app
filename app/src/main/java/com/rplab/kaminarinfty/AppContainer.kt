package com.rplab.kaminarinfty

import android.app.Application
import android.content.Context
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.preferences.DataStoreAppPreferences
import com.rplab.kaminarinfty.data.preferences.KeystoreSecretStore
import com.rplab.kaminarinfty.data.preferences.SecretStore
import com.rplab.kaminarinfty.data.repository.ActivityRepository
import com.rplab.kaminarinfty.data.repository.DefaultProfileRepository
import com.rplab.kaminarinfty.data.repository.MessageRepository
import com.rplab.kaminarinfty.data.repository.NftyRepository
import com.rplab.kaminarinfty.data.repository.OutboxRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.data.repository.RoomActivityRepository
import com.rplab.kaminarinfty.commands.MockCommandExecutor
import com.rplab.kaminarinfty.data.repository.CommandRepository
import com.rplab.kaminarinfty.data.repository.RoomCommandRepository
import com.rplab.kaminarinfty.data.repository.RoomMessageRepository
import com.rplab.kaminarinfty.data.repository.RoomOutboxRepository
import com.rplab.kaminarinfty.delivery.AndroidDeliveryController
import com.rplab.kaminarinfty.delivery.DeliveryController
import com.rplab.kaminarinfty.delivery.DeliveryStatusStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Minimal manual DI container; one instance per process. */
class AppContainer(context: Context) {
    val database: KaminariDatabase = KaminariDatabase.build(context)
    val preferences: AppPreferences = DataStoreAppPreferences(context)
    val secretStore: SecretStore = KeystoreSecretStore(context)
    val messageRepository: MessageRepository = RoomMessageRepository(database)
    val profileRepository: ProfileRepository =
        DefaultProfileRepository(database, secretStore, preferences)
    val activityRepository: ActivityRepository = RoomActivityRepository(database)
    val streamRepository: NftyRepository = NftyRepository(profileRepository = profileRepository)
    val outboxRepository: OutboxRepository = RoomOutboxRepository(
        database = database,
        profileRepository = profileRepository,
        activityRepository = activityRepository,
        publish = { profileId, request -> streamRepository.publish(profileId, request) },
    )
    val commandRepository: CommandRepository = RoomCommandRepository(
        database = database,
        activityRepository = activityRepository,
        executor = MockCommandExecutor(),
    )
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
        // A row left SENDING by a killed process is uncertain: move it to
        // AMBIGUOUS before any UI can offer a retry. This never sends anything.
        container.appScope.launch {
            try {
                container.profileRepository.observeProfiles().firstOrNull()?.forEach { profile ->
                    container.outboxRepository.recoverStuck(profile.id)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Best effort: rows stay SENDING and the next explicit send or
                // recovery pass retries this safe, non-network transition.
            }
        }
    }
}
