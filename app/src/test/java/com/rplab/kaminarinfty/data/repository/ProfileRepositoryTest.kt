package com.rplab.kaminarinfty.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.preferences.SecretStore
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.testing.FakePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileRepositoryTest {

    private class FakeSecrets : SecretStore {
        val values = mutableMapOf<String, String>()
        override fun put(key: String, secret: String) {
            values[key] = secret
        }

        override fun get(key: String): String? = values[key]

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private lateinit var database: KaminariDatabase
    private lateinit var secrets: FakeSecrets
    private lateinit var preferences: FakePreferences
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        secrets = FakeSecrets()
        preferences = FakePreferences()
        repository = DefaultProfileRepository(database, secrets, preferences)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saveProfile stores the config and token in the secret store`() = runTest {
        repository.saveProfile(
            ServerConfig(
                serverUrl = "https://nfty.example.test/",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_secret",
            )
        )

        val loaded = repository.loadActiveConfig()
        assertEquals("https://nfty.example.test", loaded?.serverUrl)
        assertEquals(AuthMethod.ACCESS_TOKEN, loaded?.authMethod)
        assertEquals("tk_secret", loaded?.tokenOrPassword)
        assertEquals("tk_secret", secrets.values["primary"])
    }

    @Test
    fun `switching to guest removes the stored secret`() = runTest {
        repository.saveProfile(
            ServerConfig(
                serverUrl = "https://nfty.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_secret",
            )
        )
        repository.saveProfile(
            ServerConfig(
                serverUrl = "https://nfty.example.test",
                authMethod = AuthMethod.GUEST,
            )
        )

        assertNull(secrets.values["primary"])
        assertEquals("", repository.loadActiveConfig()?.tokenOrPassword)
    }

    @Test
    fun `loadActiveConfig is null before any profile is saved`() = runTest {
        assertNull(repository.loadActiveConfig())
    }

    @Test
    fun `topics are stored and only subscribed unpaused topics are streamed`() = runTest {
        repository.saveProfile(ServerConfig(serverUrl = "https://nfty.example.test"))
        repository.addTopic("primary", "ops")
        repository.addTopic("primary", "media")
        repository.addTopic("primary", "ops") // duplicate ignored

        assertTrue(repository.subscribedTopicNames("primary").containsAll(listOf("ops", "media")))

        repository.setTopicPaused("primary", "media", true)
        assertEquals(listOf("ops"), repository.subscribedTopicNames("primary"))

        repository.setTopicSubscribed("primary", "ops", false)
        assertTrue(repository.subscribedTopicNames("primary").isEmpty())
        assertEquals(2, repository.observeTopics("primary").first().size)
    }

    @Test
    fun `multiple profiles keep independent configs and secrets`() = runTest {
        repository.saveProfile(
            ServerConfig(
                serverUrl = "https://home.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_home",
            )
        )
        val secondId = repository.createProfile(
            ServerConfig(
                serverUrl = "https://work.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_work",
            )
        )

        assertTrue(secondId != "primary")
        assertEquals("https://home.example.test", repository.loadConfig("primary")?.serverUrl)
        assertEquals("tk_home", repository.loadConfig("primary")?.tokenOrPassword)
        assertEquals("https://work.example.test", repository.loadConfig(secondId)?.serverUrl)
        assertEquals("tk_work", repository.loadConfig(secondId)?.tokenOrPassword)
        assertEquals(2, repository.observeProfiles().first().size)
    }

    @Test
    fun `observeProfiles reports whether a secret is stored without exposing it`() = runTest {
        repository.saveProfile(
            ServerConfig(
                serverUrl = "https://home.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_home",
            )
        )

        val profile = repository.observeProfiles().first().single()

        assertEquals("primary", profile.id)
        assertTrue(profile.hasStoredSecret)
        assertTrue(!profile.toString().contains("tk_home"))
    }

    @Test
    fun `deleting the active profile clears the active selection`() = runTest {
        val id = repository.saveProfile(ServerConfig(serverUrl = "https://home.example.test"))
        repository.setActiveProfileId(id)
        repository.addTopic(id, "ops")

        repository.deleteProfile(id)

        assertNull(preferences.activeProfileId())
        assertNull(repository.loadConfig(id))
        assertTrue(repository.observeTopics(id).first().isEmpty())
        assertTrue(repository.observeProfiles().first().isEmpty())
    }
}
