package com.rplab.kaminarinfty.commands

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import com.rplab.kaminarinfty.model.CommandStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommandPolicyTest {

    private fun envelope(
        action: String = "host.health",
        args: Map<String, String> = emptyMap(),
        issuedAt: Long = 1_000L,
        expiresAt: Long = issuedAt + COMMAND_EXPIRY_MS,
        requestedBy: String = "android:primary",
        requiresConfirmation: Boolean = false,
        confirmedAt: Long? = null,
    ) = CommandEnvelope(
        commandId = "11111111-2222-4333-8444-555555555555",
        nonce = CommandNonce.generate(),
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        requestedBy = requestedBy,
        target = MOCK_COMMAND_TARGET,
        action = action,
        args = args,
        replyTopic = COMMAND_REPLY_TOPIC,
        requiresConfirmation = requiresConfirmation,
    )

    @Test
    fun `catalogue has exactly the six approved actions`() {
        assertEquals(6, CommandCatalog.actions.size)
        assertEquals(
            setOf(
                "host.health",
                "service.status",
                "storage.summary",
                "backup.latest_status",
                "container.status",
                "service.restart",
            ),
            CommandCatalog.actions.map { it.id }.toSet(),
        )
    }

    @Test
    fun `valid non-destructive envelope passes`() {
        val result = CommandPolicy.validate(
            envelope = envelope(),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = true,
        )
        assertTrue(result is CommandValidation.Valid)
    }

    @Test
    fun `unknown action is rejected`() {
        val result = CommandPolicy.validate(
            envelope = envelope(action = "shell.exec"),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = true,
        )
        assertTrue(result is CommandValidation.Invalid)
        assertTrue((result as CommandValidation.Invalid).reason.contains("Unknown"))
    }

    @Test
    fun `unknown argument is rejected`() {
        val result = CommandPolicy.validate(
            envelope = envelope(action = "service.status", args = mapOf("service" to "x", "extra" to "y")),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = true,
        )
        assertTrue(result is CommandValidation.Invalid)
        assertTrue((result as CommandValidation.Invalid).reason.contains("Unknown argument"))
    }

    @Test
    fun `missing required argument is rejected`() {
        val result = CommandPolicy.validate(
            envelope = envelope(action = "service.status", args = emptyMap()),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = true,
        )
        assertTrue(result is CommandValidation.Invalid)
        assertTrue((result as CommandValidation.Invalid).reason.contains("Missing", ignoreCase = true))
    }

    @Test
    fun `nonce must be 43 url-safe characters`() {
        assertTrue(CommandNonce.isValid(CommandNonce.generate()))
        assertFalse(CommandNonce.isValid("short"))
        assertFalse(CommandNonce.isValid("a".repeat(42)))
        assertFalse(CommandNonce.isValid("a".repeat(44)))
        assertFalse(CommandNonce.isValid("!".repeat(43)))
    }

    @Test
    fun `expired envelope is rejected`() {
        val result = CommandPolicy.validate(
            envelope = envelope(issuedAt = 0L, expiresAt = 1L),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = false,
        )
        assertTrue(result is CommandValidation.Invalid)
        assertTrue((result as CommandValidation.Invalid).reason.contains("expired", ignoreCase = true))
    }

    @Test
    fun `clock skew beyond 60 seconds is rejected only when requested`() {
        val skewed = envelope(issuedAt = 0L, expiresAt = COMMAND_EXPIRY_MS)
        val withoutSkew = CommandPolicy.validate(
            envelope = skewed,
            nowMillis = 1_000L + 61_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = false,
        )
        assertTrue(withoutSkew is CommandValidation.Valid)
    }

    @Test
    fun `destructive action requires confirmation binding`() {
        val destructive = envelope(
            action = "service.restart",
            args = mapOf("service" to "navidrome"),
            requiresConfirmation = true,
        )
        val noConfirmation = CommandPolicy.validate(
            envelope = destructive,
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = false,
            confirmedAt = null,
        )
        assertTrue(noConfirmation is CommandValidation.Invalid)

        val confirmed = CommandPolicy.validate(
            envelope = destructive,
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = false,
            confirmedAt = 1_000L,
        )
        assertTrue(confirmed is CommandValidation.Valid)
    }

    @Test
    fun `round trip json preserves fields`() {
        val original = envelope(action = "service.status", args = mapOf("service" to "navidrome"))
        val parsed = CommandEnvelope.fromJson(original.toJson()).getOrNull()
        assertEquals(original.commandId, parsed?.commandId)
        assertEquals(original.nonce, parsed?.nonce)
        assertEquals(original.action, parsed?.action)
        assertEquals(original.args, parsed?.args)
    }

    @Test
    fun `malformed json is rejected`() {
        assertTrue(CommandEnvelope.fromJson("{not json").isFailure)
    }
}
