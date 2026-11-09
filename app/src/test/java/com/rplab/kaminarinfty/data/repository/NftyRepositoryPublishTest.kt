package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.api.NtfyPublish
import com.rplab.kaminarinfty.data.api.PublishRequest
import com.rplab.kaminarinfty.data.api.PublishResult
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.testing.FakeProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NftyRepositoryPublishTest {

    /** Profile repository that resolves a distinct config per profile id. */
    private class MapProfileRepository(
        private val configs: Map<String, ServerConfig>,
    ) : ProfileRepository by FakeProfileRepository() {
        override suspend fun loadConfig(profileId: String): ServerConfig? = configs[profileId]
        override suspend fun loadActiveConfig(): ServerConfig? = configs.values.firstOrNull()
    }

    private class RecordingPublisher : NtfyPublish {
        val calls = mutableListOf<Pair<ServerConfig, PublishRequest>>()
        var result: PublishResult = PublishResult.Accepted("id")

        override suspend fun publish(config: ServerConfig, request: PublishRequest): PublishResult {
            calls += config to request
            return result
        }
    }

    private fun config(token: String) = ServerConfig(
        serverUrl = "https://ntfy.example.com",
        authMethod = AuthMethod.ACCESS_TOKEN,
        tokenOrPassword = token,
    )

    private val request = PublishRequest(topic = "alerts", message = "hello")

    @Test
    fun `publishes with the selected profile config`() = runBlocking {
        val publisher = RecordingPublisher()
        val repository = NftyRepository(
            profileRepository = MapProfileRepository(mapOf("a" to config("token-a"))),
            publisher = publisher,
        )

        val result = repository.publish("a", request)

        assertTrue(result is PublishResult.Accepted)
        assertEquals(1, publisher.calls.size)
        val usedConfig = publisher.calls.single().first
        assertEquals("token-a", usedConfig.tokenOrPassword)
        assertEquals("https://ntfy.example.com", usedConfig.serverUrl)
    }

    @Test
    fun `never falls back across profiles`() = runBlocking {
        val publisher = RecordingPublisher()
        val repository = NftyRepository(
            profileRepository = MapProfileRepository(
                mapOf("a" to config("token-a"), "b" to config("token-b")),
            ),
            publisher = publisher,
        )

        repository.publish("b", request)

        assertEquals(1, publisher.calls.size)
        assertEquals("token-b", publisher.calls.single().first.tokenOrPassword)
    }

    @Test
    fun `unknown profile is rejected without publishing`() = runBlocking {
        val publisher = RecordingPublisher()
        val repository = NftyRepository(
            profileRepository = MapProfileRepository(mapOf("a" to config("token-a"))),
            publisher = publisher,
        )

        val result = repository.publish("missing", request)

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, publisher.calls.size)
    }

    @Test
    fun `blank profile id is rejected without publishing`() = runBlocking {
        val publisher = RecordingPublisher()
        val repository = NftyRepository(
            profileRepository = MapProfileRepository(mapOf("a" to config("token-a"))),
            publisher = publisher,
        )

        val result = repository.publish("  ", request)

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, publisher.calls.size)
    }

    @Test
    fun `missing profile storage is rejected`() = runBlocking {
        val publisher = RecordingPublisher()
        val repository = NftyRepository(
            profileRepository = null,
            publisher = publisher,
        )

        val result = repository.publish("a", request)

        assertTrue(result is PublishResult.ValidationFailed)
        assertEquals(0, publisher.calls.size)
    }
}
