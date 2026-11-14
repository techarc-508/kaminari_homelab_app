package com.rplab.kaminarinfty.commands

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opt-in integration test against the deployed gateway.
 *
 * It uses the real production client classes, not a fake server. It runs only
 * when `KAMINARI_GATEWAY_URL` and `KAMINARI_GATEWAY_TOKEN` reach the test JVM
 * (see `app/build.gradle.kts`); otherwise it is skipped so normal builds stay
 * hermetic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GatewayLiveReadTest {

    @Test
    fun executesRealReadOnlyCommandThroughDeployedGateway() = runBlocking {
        val url = System.getProperty("kaminari.gateway.url").orEmpty()
        val token = System.getProperty("kaminari.gateway.token").orEmpty()
        assumeTrue("live gateway not configured for this run", url.isNotBlank() && token.isNotBlank())

        val connection = GatewayConnection(
            baseUrl = url,
            token = token,
            requireHttps = !GatewayUrlPolicy.isLoopback(url),
        )
        val health = GatewayProbe().probe(connection)
        assertTrue("gateway not reachable: ${health.message}", health.reachable)
        assertTrue("gateway does not report durable state", health.durableState)
        assertFalse("mutations must stay disabled", health.mutationsEnabled)

        val now = System.currentTimeMillis()
        val envelope = CommandEnvelope(
            commandId = UUID.randomUUID().toString(),
            nonce = "L".repeat(43),
            issuedAt = now,
            expiresAt = now + 120_000,
            requestedBy = "android:live-integration",
            target = GATEWAY_COMMAND_TARGET,
            action = "host.health",
            replyTopic = COMMAND_REPLY_TOPIC,
            requiresConfirmation = false,
        )

        val outcome = GatewayCommandExecutor(connections = { connection })
            .execute(envelope, null, null)

        assertTrue("live read failed: ${outcome.result.summary}", outcome.result.ok)
        assertFalse("gateway result must not be a mock", outcome.result.mock)
        assertTrue(outcome.result.summary.isNotBlank())
    }
}
