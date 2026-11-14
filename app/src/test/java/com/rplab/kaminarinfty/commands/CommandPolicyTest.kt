package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.contract.KfActionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        replyTopic: String = COMMAND_REPLY_TOPIC,
    ) = CommandEnvelope(
        commandId = "11111111-2222-4333-8444-555555555555",
        nonce = CommandNonce.generate(),
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        requestedBy = requestedBy,
        target = MOCK_COMMAND_TARGET,
        action = action,
        args = args,
        replyTopic = replyTopic,
        requiresConfirmation = requiresConfirmation,
    )

    @Test
    fun `catalogue matches the aligned fourteen gateway actions`() {
        assertEquals(14, CommandCatalog.actions.size)
        assertEquals(
            KfActionCatalog.actions.map { it.id }.toSet(),
            CommandCatalog.actions.map { it.id }.toSet(),
        )
        assertFalse(CommandCatalog.actions.any { it.id == "service.restart" })
        assertFalse(CommandCatalog.actions.any { it.id == "shell.exec" })
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
            envelope = envelope(
                action = "vm.status",
                args = mapOf("node" to "pve", "vmid" to "100", "extra" to "y"),
            ),
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
            envelope = envelope(action = "vm.status", args = mapOf("node" to "pve")),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = true,
        )
        assertTrue(result is CommandValidation.Invalid)
        assertTrue((result as CommandValidation.Invalid).reason.contains("Missing", ignoreCase = true))
    }

    @Test
    fun `invalid node is rejected by the shared pattern`() {
        val result = CommandPolicy.validate(
            envelope = envelope(action = "vm.list", args = mapOf("node" to "PVE ROOT")),
            nowMillis = 1_000L,
            expectedTarget = MOCK_COMMAND_TARGET,
            enforceFreshIssue = true,
        )
        assertTrue(result is CommandValidation.Invalid)
        assertTrue((result as CommandValidation.Invalid).reason.contains("Node"))
    }

    @Test
    fun `reply topic defaults to the approved kf topic`() {
        assertEquals("kf-command-result", COMMAND_REPLY_TOPIC)
        val envelope = envelope()
        assertEquals("kf-command-result", envelope.replyTopic)
        val legacy = envelope.copy(replyTopic = LEGACY_COMMAND_REPLY_TOPIC)
        assertTrue(
            CommandPolicy.validate(
                envelope = legacy,
                nowMillis = 1_000L,
                expectedTarget = MOCK_COMMAND_TARGET,
            ) is CommandValidation.Valid
        )
        val rejected = envelope.copy(replyTopic = "rplab-command-result")
        assertTrue(
            CommandPolicy.validate(
                envelope = rejected,
                nowMillis = 1_000L,
                expectedTarget = MOCK_COMMAND_TARGET,
            ) is CommandValidation.Invalid
        )
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
            action = "container.restart",
            args = mapOf("node" to "pve", "vmid" to "200"),
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
        val original = envelope(
            action = "container.status",
            args = mapOf("node" to "pve", "vmid" to "200"),
        )
        val parsed = CommandEnvelope.fromJson(original.toJson()).getOrNull()
        assertEquals(original.commandId, parsed?.commandId)
        assertEquals(original.nonce, parsed?.nonce)
        assertEquals(original.action, parsed?.action)
        assertEquals(original.args, parsed?.args)
        assertEquals("kf-command-result", parsed?.replyTopic)
    }

    @Test
    fun `malformed json is rejected`() {
        assertTrue(CommandEnvelope.fromJson("{not json").isFailure)
    }
}
