package com.rplab.kaminarinfty.commands

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import com.rplab.kaminarinfty.model.CommandResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GatewayCommandExecutorTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun executesSuccessfulGatewayCommand() = runBlocking {
        server.start()
        val response = JSONObject()
            .put("status", "completed")
            .put("expired", false)
            .put(
                "result",
                JSONObject()
                    .put("mock", false)
                    .put("ok", true)
                    .put("summary", "VM 100 started")
                    .put("detail", JSONObject().put("task", "UPID:1")),
            )
        server.enqueue(MockResponse().setResponseCode(200).setBody(response.toString()))

        val executor = GatewayCommandExecutor(
            baseUrl = server.url("/").toString().trimEnd('/'),
            gatewayToken = "token",
            requireHttps = false,
        )
        val envelope = CommandEnvelope(
            commandId = java.util.UUID.randomUUID().toString(),
            nonce = "A".repeat(43),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000,
            requestedBy = "android:profile-1",
            target = "proxmox-gateway",
            action = "host.health",
            replyTopic = "rplab-command-results",
            requiresConfirmation = false,
        )

        val outcome = executor.execute(envelope, null)
        assertTrue(outcome.result.ok)
        assertTrue(!outcome.result.mock)
        assertEquals("VM 100 started", outcome.result.summary)
        assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun returnsFailureOnHttpError() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setResponseCode(403).setBody("{\"error\":\"forbidden\"}"))

        val executor = GatewayCommandExecutor(
            baseUrl = server.url("/").toString().trimEnd('/'),
            gatewayToken = "token",
            requireHttps = false,
        )
        val envelope = CommandEnvelope(
            commandId = java.util.UUID.randomUUID().toString(),
            nonce = "B".repeat(43),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000,
            requestedBy = "android:profile-1",
            target = "proxmox-gateway",
            action = "host.health",
            replyTopic = "rplab-command-results",
            requiresConfirmation = false,
        )

        val outcome = executor.execute(envelope, null)
        assertTrue(!outcome.result.ok)
        assertTrue(outcome.result.summary.contains("HTTP 403"))
    }

    @Test
    fun refusesInsecureUrlWhenHttpsRequired() = runBlocking {
        val executor = GatewayCommandExecutor(
            baseUrl = "http://example.com",
            gatewayToken = "token",
            requireHttps = true,
        )
        val envelope = CommandEnvelope(
            commandId = java.util.UUID.randomUUID().toString(),
            nonce = "C".repeat(43),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000,
            requestedBy = "android:profile-1",
            target = "proxmox-gateway",
            action = "host.health",
            replyTopic = "rplab-command-results",
            requiresConfirmation = false,
        )
        val outcome = executor.execute(envelope, null)
        assertTrue(!outcome.result.ok)
        assertTrue(outcome.result.summary.contains("https://"))
    }

    @Test
    fun rejectsOversizedResponses() = runBlocking {
        server.start()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{}".repeat(1_200_000)),
        )

        val executor = GatewayCommandExecutor(
            baseUrl = server.url("/").toString().trimEnd('/'),
            gatewayToken = "token",
            requireHttps = false,
        )
        val envelope = CommandEnvelope(
            commandId = java.util.UUID.randomUUID().toString(),
            nonce = "D".repeat(43),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000,
            requestedBy = "android:profile-1",
            target = "proxmox-gateway",
            action = "host.health",
            replyTopic = "rplab-command-results",
            requiresConfirmation = false,
        )
        val outcome = executor.execute(envelope, null)
        assertTrue(!outcome.result.ok)
        assertTrue(
            outcome.result.summary.contains("too large") ||
            outcome.result.summary.contains("Gateway request failed"),
        )
    }

    @Test
    fun rejectsEmptyGatewayToken() = runBlocking {
        val executor = GatewayCommandExecutor(
            baseUrl = "https://example.com",
            gatewayToken = "",
            requireHttps = true,
        )
        val envelope = CommandEnvelope(
            commandId = java.util.UUID.randomUUID().toString(),
            nonce = "E".repeat(43),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000,
            requestedBy = "android:profile-1",
            target = "proxmox-gateway",
            action = "host.health",
            replyTopic = "rplab-command-results",
            requiresConfirmation = false,
        )
        val outcome = executor.execute(envelope, null)
        assertTrue(!outcome.result.ok)
        assertTrue(outcome.result.summary.contains("token"))
    }

    @Test
    fun reportsGatewayNetworkFailure() = runBlocking {
        val executor = GatewayCommandExecutor(
            baseUrl = "https://localhost:1",
            gatewayToken = "token",
            connectTimeoutSeconds = 1,
            readTimeoutSeconds = 1,
            requireHttps = true,
        )
        val envelope = CommandEnvelope(
            commandId = java.util.UUID.randomUUID().toString(),
            nonce = "F".repeat(43),
            issuedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000,
            requestedBy = "android:profile-1",
            target = "proxmox-gateway",
            action = "host.health",
            replyTopic = "rplab-command-results",
            requiresConfirmation = false,
        )
        val outcome = executor.execute(envelope, null)
        assertTrue(!outcome.result.ok)
        assertTrue(outcome.result.summary.contains("Gateway request failed"))
    }
}
