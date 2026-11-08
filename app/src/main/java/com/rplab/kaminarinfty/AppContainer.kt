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

/** Minimal manual DI container; one instance per process. */
class AppContainer(context: Context) {
    val database: KaminariDatabase = KaminariDatabase.build(context)
    val preferences: AppPreferences = DataStoreAppPreferences(context)
    val secretStore: SecretStore = KeystoreSecretStore(context)
    val messageRepository: MessageRepository = RoomMessageRepository(database)
    val profileRepository: ProfileRepository =
        DefaultProfileRepository(database, secretStore, preferences)
    val streamRepository: NftyRepository = NftyRepository()
}

class KaminariApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
