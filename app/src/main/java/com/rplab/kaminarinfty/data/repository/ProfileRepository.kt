package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.ServerProfileEntity
import com.rplab.kaminarinfty.data.local.TopicSubscriptionEntity
import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.preferences.SecretStore
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.NftyTopic
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Stable ID of the first profile created by the setup flow. */
const val PRIMARY_PROFILE_ID = "primary"

/**
 * Outcome of persisting a profile.
 *
 * [credentialError] is non-null when the profile row was written but the secret
 * could not be stored (e.g. Keystore unavailable). Callers must surface this
 * instead of pretending the profile is ready to authenticate (G-06).
 */
data class ProfileSaveResult(
    val profileId: String,
    val credentialStored: Boolean = true,
    val credentialError: String? = null,
)

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ServerProfile>>
    fun observeActiveProfileId(): Flow<String?>
    fun observeTopics(profileId: String): Flow<List<NftyTopic>>

    suspend fun loadActiveConfig(): ServerConfig?
    suspend fun loadConfig(profileId: String): ServerConfig?

    /** Upserts the existing primary profile (setup "Connect"). */
    suspend fun saveProfile(config: ServerConfig): ProfileSaveResult

    /** Creates an additional profile and returns its generated ID. */
    suspend fun createProfile(config: ServerConfig, displayName: String = ""): ProfileSaveResult

    suspend fun updateProfile(profileId: String, config: ServerConfig): ProfileSaveResult
    suspend fun setActiveProfileId(id: String)
    suspend fun deleteProfile(id: String)

    suspend fun addTopic(profileId: String, topic: String)
    suspend fun setTopicSubscribed(profileId: String, topic: String, subscribed: Boolean)
    suspend fun setTopicPaused(profileId: String, topic: String, paused: Boolean)
    suspend fun subscribedTopicNames(profileId: String): List<String>
}

class DefaultProfileRepository(
    database: KaminariDatabase,
    private val secretStore: SecretStore,
    private val preferences: AppPreferences,
) : ProfileRepository {

    private val profileDao = database.serverProfileDao()
    private val topicDao = database.topicSubscriptionDao()

    override fun observeProfiles(): Flow<List<ServerProfile>> =
        profileDao.observeAll().map { rows -> rows.map(::toDomain) }

    override fun observeActiveProfileId(): Flow<String?> =
        preferences.data.map { it.activeProfileId }

    override fun observeTopics(profileId: String): Flow<List<NftyTopic>> =
        topicDao.observeForProfile(profileId).map { rows ->
            rows.map {
                NftyTopic(
                    name = it.topic,
                    description = "Subscribed on this device",
                    isSubscribed = it.isSubscribed,
                    isPaused = it.isPaused,
                    lastActivityTime = it.createdAt,
                )
            }
        }

    override suspend fun loadActiveConfig(): ServerConfig? {
        val activeId = preferences.activeProfileId() ?: PRIMARY_PROFILE_ID
        return loadConfig(activeId)
    }

    override suspend fun loadConfig(profileId: String): ServerConfig? {
        val profile = profileDao.getById(profileId) ?: return null
        return ServerConfig(
            serverUrl = profile.baseUrl,
            authMethod = parseAuthMethod(profile.authMethod),
            username = profile.displayName,
            tokenOrPassword = secretStore.get(profile.id).orEmpty(),
        )
    }

    override suspend fun saveProfile(config: ServerConfig): ProfileSaveResult =
        upsertProfile(PRIMARY_PROFILE_ID, config, fallbackName = config.username)

    override suspend fun createProfile(config: ServerConfig, displayName: String): ProfileSaveResult {
        val id = "profile-${UUID.randomUUID()}"
        return upsertProfile(id, config, fallbackName = displayName)
    }

    override suspend fun updateProfile(profileId: String, config: ServerConfig): ProfileSaveResult =
        upsertProfile(profileId, config, fallbackName = config.username)

    override suspend fun setActiveProfileId(id: String) = preferences.setActiveProfileId(id)

    override suspend fun deleteProfile(id: String) {
        profileDao.deleteById(id)
        runCatching { secretStore.remove(id) }
        if (preferences.activeProfileId() == id) {
            preferences.clearActiveProfileId()
        }
    }

    private suspend fun upsertProfile(
        profileId: String,
        config: ServerConfig,
        fallbackName: String,
    ): ProfileSaveResult {
        val now = System.currentTimeMillis()
        val existing = profileDao.getById(profileId)
        val name = when {
            config.username.isNotBlank() -> config.username
            existing?.displayName?.isNotBlank() == true -> existing.displayName
            fallbackName.isNotBlank() -> fallbackName
            else -> "ntfy"
        }
        profileDao.upsert(
            ServerProfileEntity(
                id = profileId,
                displayName = name,
                baseUrl = config.serverUrl.trim().trimEnd('/'),
                authMethod = config.authMethod.name,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        // Gate finding G-06: a blank credential means "leave the stored secret
        // alone", never "erase it". Only an explicit guest profile clears it.
        return when {
            config.authMethod == AuthMethod.GUEST ->
                runCatching { secretStore.remove(profileId) }.fold(
                    onSuccess = { ProfileSaveResult(profileId) },
                    onFailure = { failure ->
                        ProfileSaveResult(
                            profileId,
                            credentialStored = false,
                            credentialError = failure.storageMessage(),
                        )
                    },
                )
            config.tokenOrPassword.isNotBlank() ->
                runCatching { secretStore.put(profileId, config.tokenOrPassword) }.fold(
                    onSuccess = { ProfileSaveResult(profileId) },
                    onFailure = { failure ->
                        ProfileSaveResult(
                            profileId,
                            credentialStored = false,
                            credentialError = failure.storageMessage(),
                        )
                    },
                )
            else -> ProfileSaveResult(profileId)
        }
    }

    /** Torn down to a short, non-secret message the UI can show. */
    private fun Throwable.storageMessage(): String = when (this) {
        is IllegalArgumentException -> "Credential is too long to store securely"
        else -> message?.takeIf { it.isNotBlank() } ?: "Secure credential storage is unavailable"
    }

    override suspend fun addTopic(profileId: String, topic: String) {
        topicDao.insert(TopicSubscriptionEntity(profileId = profileId, topic = topic))
    }

    override suspend fun setTopicSubscribed(profileId: String, topic: String, subscribed: Boolean) =
        topicDao.setSubscribed(profileId, topic, subscribed)

    override suspend fun setTopicPaused(profileId: String, topic: String, paused: Boolean) =
        topicDao.setPaused(profileId, topic, paused)

    override suspend fun subscribedTopicNames(profileId: String): List<String> =
        topicDao.getForProfile(profileId)
            .filter { it.isSubscribed && !it.isPaused }
            .map { it.topic }

    private fun toDomain(entity: ServerProfileEntity): ServerProfile = ServerProfile(
        id = entity.id,
        displayName = entity.displayName,
        baseUrl = entity.baseUrl,
        authMethod = parseAuthMethod(entity.authMethod),
        hasStoredSecret = secretStore.get(entity.id)?.isNotBlank() == true,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun parseAuthMethod(raw: String): AuthMethod =
        runCatching { AuthMethod.valueOf(raw) }.getOrDefault(AuthMethod.GUEST)
}
