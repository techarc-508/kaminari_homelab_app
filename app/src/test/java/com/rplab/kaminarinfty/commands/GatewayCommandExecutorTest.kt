package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.model.CommandResult
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        runCatching { server.shutdown() }
    }

    private fun envelope(
        action: String = "host.health",
        requiresConfirmation: Boolean = false,
    ): CommandEnvelope {
        val now = System.currentTimeMillis()
        return CommandEnvelope(
            commandId = UUID.randomUUID().toString(),
            nonce = "A".repeat(43),
            issuedAt = now,
            expiresAt = now + 60_000,
            requestedBy = "android:profile-1",
            target = GATEWAY_COMMAND_TARGET,
            action = action,
            replyTopic = COMMAND_REPLY_TOPIC,
            requiresConfirmation = requiresConfirmation,
        )
    }

    private fun executor(
        url: String,
        token: String = "token",
        requireHttps: Boolean = false,
    ) = GatewayCommandExecutor(
        connections = { GatewayConnection(baseUrl = url, token = token, requireHttps = requireHttps) },
    )

    private fun successBody() = JSONObject()
        .put("status", "completed")
        .put("expired", false)
        .put(
            "result",
            JSONObject()
                .put("mock", false)
                .put("ok", true)
                .put("summary", "Proxmox gateway healthy")
                .put("detail", JSONObject().put("gateway", "ok")),
        )

    @Test
    fun executesReadOnlyCommandAgainstGateway() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody().toString()))

        val outcome = executor(server.url("/").toString().trimEnd('/')).execute(envelope(), null, null)

        assertTrue(outcome.result.ok)
        assertFalse(outcome.result.mock)
        assertEquals("Proxmox gateway healthy", outcome.result.summary)
        val request = server.takeRequest()
        assertEquals("/v1/commands/execute", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(GATEWAY_COMMAND_TARGET, body.getJSONObject("envelope").getString("target"))
        assertFalse(body.has("approval"))
    }

    @Test
    fun surfacesGatewayErrorCode() = runBlocking {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("{\"error\":\"Mutations are disabled on this gateway\",\"code\":\"forbidden\"}")
        )

        val outcome = executor(server.url("/").toString().trimEnd('/')).execute(envelope(), null, null)

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("HTTP 403"))
        assertTrue(outcome.result.summary.contains("Mutations are disabled"))
    }

    @Test
    fun refusesInsecureRemoteUrl() = runBlocking {
        val outcome = executor("http://example.com", requireHttps = true)
            .execute(envelope(), null, null)

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("https://"))
    }

    @Test
    fun refusesWhenGatewayIsNotConfigured() = runBlocking {
        val unconfigured = GatewayCommandExecutor(connections = { null })

        val outcome = unconfigured.execute(envelope(), null, null)

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("not configured"))
    }

    @Test
    fun rejectsOversizedResponse() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}".repeat(1_200_000)))

        val outcome = executor(server.url("/").toString().trimEnd('/')).execute(envelope(), null, null)

        assertFalse(outcome.result.ok)
    }

    @Test
    fun rejectsEmptyGatewayToken() = runBlocking {
        val outcome = executor("https://example.com", token = "", requireHttps = true)
            .execute(envelope(), null, null)

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("token"))
    }

    @Test
    fun reportsNetworkFailure() = runBlocking {
        val outcome = executor("http://127.0.0.1:1").execute(envelope(), null, null)

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("failed"))
    }

    @Test
    fun mutationWithoutCodeIsRefusedLocally() = runBlocking {
        val outcome = executor("http://127.0.0.1:1")
            .execute(envelope(action = "vm.start", requiresConfirmation = true), null, null)

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("6-digit"))
    }

    @Test
    fun mutationRequestsChallengeThenSubmitsCode() = runBlocking {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("{\"challenge_id\":\"challenge-1\",\"expires_at\":\"2026-01-01T00:00:00.000Z\"}")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(successBody().toString()))

        val outcome = executor(server.url("/").toString().trimEnd('/'))
            .execute(envelope(action = "vm.start", requiresConfirmation = true), 1L, "123456")

        assertTrue(outcome.result.ok)
        val challengeRequest = server.takeRequest()
        assertEquals("/v1/stepup/challenge", challengeRequest.path)
        val challengeBody = JSONObject(challengeRequest.body.readUtf8())
        assertEquals("vm.start", challengeBody.getString("action"))
        val executeRequest = server.takeRequest()
        val executeBody = JSONObject(executeRequest.body.readUtf8())
        assertEquals("challenge-1", executeBody.getJSONObject("approval").getString("challenge_id"))
        assertEquals("123456", executeBody.getJSONObject("approval").getString("code"))
    }

    @Test
    fun challengeRejectionStopsBeforeExecution() = runBlocking {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("{\"error\":\"Step-up is unavailable\",\"code\":\"step_up_unavailable\"}")
        )

        val outcome = executor(server.url("/").toString().trimEnd('/'))
            .execute(envelope(action = "vm.start", requiresConfirmation = true), 1L, "123456")

        assertFalse(outcome.result.ok)
        assertTrue(outcome.result.summary.contains("403"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun probeReportsRealGatewayState() = runBlocking {
        server.start()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"status\":\"ready\",\"durable_state\":true,\"mutations_enabled\":false," +
                    "\"step_up_enrolled\":false}"
            )
        )

        val health = GatewayProbe().probe(
            GatewayConnection(server.url("/").toString().trimEnd('/'), "token", requireHttps = false)
        )

        assertTrue(health.reachable)
        assertTrue(health.durableState)
        assertFalse(health.mutationsEnabled)
        assertFalse(health.stepUpEnrolled)
    }

    @Test
    fun probeReportsRejectedToken() = runBlocking {
        server.start()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"Invalid gateway token\"}"))

        val health = GatewayProbe().probe(
            GatewayConnection(server.url("/").toString().trimEnd('/'), "bad", requireHttps = false)
        )

        assertFalse(health.reachable)
        assertTrue(health.message.contains("401"))
    }

    private fun unused(): CommandResult = CommandResult(false, true, "", "{}")
}
