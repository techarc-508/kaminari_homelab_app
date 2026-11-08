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

interface ProfileRepository {
    fun observeProfiles(): Flow<List<ServerProfile>>
    fun observeActiveProfileId(): Flow<String?>
    fun observeTopics(profileId: String): Flow<List<NftyTopic>>

    suspend fun loadActiveConfig(): ServerConfig?
    suspend fun loadConfig(profileId: String): ServerConfig?

    /** Upserts the existing primary profile (setup "Connect"). */
    suspend fun saveProfile(config: ServerConfig): String

    /** Creates an additional profile and returns its generated ID. */
    suspend fun createProfile(config: ServerConfig, displayName: String = ""): String

    suspend fun updateProfile(profileId: String, config: ServerConfig)
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

    override suspend fun saveProfile(config: ServerConfig): String {
        upsertProfile(PRIMARY_PROFILE_ID, config, fallbackName = config.username)
        return PRIMARY_PROFILE_ID
    }

    override suspend fun createProfile(config: ServerConfig, displayName: String): String {
        val id = "profile-${UUID.randomUUID()}"
        upsertProfile(id, config, fallbackName = displayName)
        return id
    }

    override suspend fun updateProfile(profileId: String, config: ServerConfig) {
        upsertProfile(profileId, config, fallbackName = config.username)
    }

    override suspend fun setActiveProfileId(id: String) = preferences.setActiveProfileId(id)

    override suspend fun deleteProfile(id: String) {
        profileDao.deleteById(id)
        secretStore.remove(id)
        if (preferences.activeProfileId() == id) {
            preferences.clearActiveProfileId()
        }
    }

    private suspend fun upsertProfile(profileId: String, config: ServerConfig, fallbackName: String) {
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
        if (config.authMethod == AuthMethod.GUEST || config.tokenOrPassword.isBlank()) {
            secretStore.remove(profileId)
        } else {
            secretStore.put(profileId, config.tokenOrPassword)
        }
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
