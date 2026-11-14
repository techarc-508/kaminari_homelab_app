package com.rplab.kaminarinfty.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KfPayloadTest {

    private val meta = KfMeta(
        eventId = "evt-1",
        occurredAt = 1_700_000_000_000L,
        source = "kf_monitor",
        correlationId = "run-1",
    )

    @Test
    fun `infrastructure alert round trips`() {
        val alert = KfInfrastructureAlert(
            meta = meta,
            kind = KfInfrastructureKind.STORAGE,
            priority = KfPriority.HIGH,
            title = "Pool degraded",
            summary = "One disk is unavailable",
            details = mapOf("pool" to "safedrive"),
        )
        assertEquals(emptyList<String>(), alert.validate())
        val parsed = KfInfrastructureAlert.fromJson(alert.toJson()).getOrThrow()
        assertEquals(alert, parsed)
        assertEquals(KfInfrastructureAlert.SCHEMA, alert.schema)
    }

    @Test
    fun `infrastructure details reject credential-like keys`() {
        val alert = KfInfrastructureAlert(
            meta = meta,
            kind = KfInfrastructureKind.SERVICE,
            priority = KfPriority.NORMAL,
            title = "Service down",
            summary = "Container stopped",
            details = mapOf("api_token" to "should-not-be-here"),
        )
        assertTrue(alert.validate().any { it.contains("credential") })
    }

    @Test
    fun `music report rejects negative and inconsistent counters`() {
        val negative = KfMusicReport(
            meta = meta,
            runId = "run-1",
            status = KfPipelineStatus.RUNNING,
            startedAt = 1_700_000_000_000L,
            requested = 2,
            downloaded = -1,
            skipped = 0,
            failed = 0,
            indexed = 0,
        )
        assertTrue(negative.validate().any { it.contains("negative") })

        val overCount = negative.copy(downloaded = 2, failed = 1)
        assertTrue(overCount.validate().any { it.contains("exceed") })
    }

    @Test
    fun `failed pipeline state requires an error and round trips`() {
        val failed = KfMusicReport(
            meta = meta,
            runId = "run-1",
            status = KfPipelineStatus.FAILED,
            startedAt = 1_700_000_000_000L,
            finishedAt = 1_700_000_060_000L,
            requested = 3,
            downloaded = 1,
            skipped = 0,
            failed = 2,
            indexed = 1,
        )
        assertTrue(failed.validate().any { it.contains("error") })

        val complete = failed.copy(
            status = KfPipelineStatus.PARTIAL,
            error = "two downloads failed",
        )
        assertEquals(emptyList<String>(), complete.validate())
        assertEquals(complete, KfMusicReport.fromJson(complete.toJson()).getOrThrow())
    }

    @Test
    fun `news card accepts only safe http urls`() {
        val card = KfNewsCard(
            meta = meta,
            headline = "Headline",
            newsSource = "Example",
            category = "tech",
            summary = "Summary",
            url = "javascript:alert(1)",
        )
        assertTrue(card.validate().any { it.contains("http") })
        val safe = card.copy(url = "https://example.test/story")
        assertEquals(emptyList<String>(), safe.validate())
        assertEquals(safe, KfNewsCard.fromJson(safe.toJson()).getOrThrow())
    }

    @Test
    fun `command result enforces correlation and failure semantics`() {
        val mismatched = KfCommandResult(
            meta = meta.copy(correlationId = "other"),
            commandId = "cmd-1",
            status = KfCommandResultStatus.COMPLETED,
            ok = true,
            summary = "done",
        )
        assertTrue(mismatched.validate().any { it.contains("correlation_id") })

        val failedWithoutCode = mismatched.copy(
            meta = meta.copy(correlationId = "cmd-1"),
            status = KfCommandResultStatus.FAILED,
            ok = false,
        )
        assertTrue(failedWithoutCode.validate().any { it.contains("error_code") })

        val accepted = failedWithoutCode.copy(
            status = KfCommandResultStatus.ACCEPTED,
            errorCode = null,
        )
        assertEquals(emptyList<String>(), accepted.validate())
        assertEquals(accepted, KfCommandResult.fromJson(accepted.toJson()).getOrThrow())
    }

    @Test
    fun `audit event excludes credential details and round trips`() {
        val audit = KfAdminAuditEvent(
            meta = meta,
            actor = "kf_gateway",
            operationId = "container.stop",
            target = "pve/200",
            risk = KfRisk.L2,
            outcome = KfAuditOutcome.COMPLETED,
            auditId = "audit-1",
            details = mapOf("node" to "pve"),
        )
        assertEquals(emptyList<String>(), audit.validate())
        assertEquals(audit, KfAdminAuditEvent.fromJson(audit.toJson()).getOrThrow())

        val unsafe = audit.copy(details = mapOf("admin_secret" to "x"))
        assertTrue(unsafe.validate().any { it.contains("credential") })
    }

    @Test
    fun `digest and diary payloads validate`() {
        val digest = KfDigest(
            meta = meta,
            periodStart = 1_700_000_000_000L,
            periodEnd = 1_700_086_400_000L,
            counts = mapOf("alerts" to 3),
            summary = "3 alerts",
        )
        assertEquals(emptyList<String>(), digest.validate())
        assertEquals(digest, KfDigest.fromJson(digest.toJson()).getOrThrow())

        val diary = KfDiaryEvent(
            meta = meta,
            entryId = "entry-1",
            title = "Entry",
            body = "Body",
            tags = listOf("homelab"),
        )
        assertEquals(emptyList<String>(), diary.validate())
        assertEquals(diary, KfDiaryEvent.fromJson(diary.toJson()).getOrThrow())
    }

    @Test
    fun `reserved sticker schemas are explicit`() {
        val request = KfStickerRequest(
            meta = meta,
            runId = "run-1",
            requestRef = "ref-1",
            status = KfPipelineStatus.ACCEPTED,
            stage = KfStickerStage.REQUEST,
        )
        assertEquals(KfStickerRequest.SCHEMA, request.schema)
        assertEquals(emptyList<String>(), request.validate())
        assertEquals(request, KfStickerRequest.fromJson(request.toJson()).getOrThrow())
    }

    @Test
    fun `malformed payload is a failure result`() {
        assertTrue(KfNewsCard.fromJson(org.json.JSONObject("{}")).isFailure)
    }

    @Test
    fun `delivery vocabulary does not equate publish with delivery`() {
        assertFalse(KfDeliveryState.MESSAGE_PUBLISHED == KfDeliveryState.RECIPIENT_DELIVERED)
        assertTrue(KfDeliveryState.entries.contains(KfDeliveryState.RECIPIENT_DELIVERED))
    }
}
