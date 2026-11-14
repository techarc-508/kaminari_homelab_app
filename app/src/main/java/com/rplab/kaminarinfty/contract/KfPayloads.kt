package com.rplab.kaminarinfty.contract

import java.net.URI
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit outcome vocabulary. A publish acceptance is not recipient delivery:
 * [RECIPIENT_DELIVERED] may only be claimed when an independent delivery signal
 * exists, never from a successful HTTP publish.
 */
enum class KfDeliveryState {
    REQUEST_ACCEPTED,
    MESSAGE_PUBLISHED,
    GATEWAY_ACCEPTED,
    EXECUTING,
    COMPLETED,
    FAILED,
    RECIPIENT_DELIVERED,
}

enum class KfPipelineStatus {
    ACCEPTED,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED,
    SKIPPED;

    companion object {
        fun fromWire(value: String): KfPipelineStatus? = entries.firstOrNull { it.name == value }
    }
}

enum class KfMusicStage {
    REQUEST,
    VALIDATION,
    DUPLICATE_CHECK,
    SOURCE_RESOLUTION,
    DOWNLOAD,
    FILE_VALIDATION,
    METADATA,
    LIBRARY,
    NAVIDROME_SCAN,
    REPORT;

    companion object {
        fun fromWire(value: String): KfMusicStage? = entries.firstOrNull { it.name == value }
    }
}

enum class KfStickerStage {
    REQUEST,
    VALIDATION,
    GENERATION,
    REVIEW,
    BACKGROUND_REMOVAL,
    PNG_VALIDATION,
    RESIZE,
    PACK_ASSEMBLY,
    DUPLICATE_CHECK,
    DIARY_SYNC,
    REPORT;

    companion object {
        fun fromWire(value: String): KfStickerStage? = entries.firstOrNull { it.name == value }
    }
}

enum class KfInfrastructureKind {
    HOST,
    GUEST,
    STORAGE,
    BACKUP,
    NETWORK,
    SERVICE,
    SECURITY,
    NTFY;

    companion object {
        fun fromWire(value: String): KfInfrastructureKind? = entries.firstOrNull { it.name == value }
    }
}

enum class KfCommandResultStatus {
    ACCEPTED,
    EXECUTING,
    COMPLETED,
    FAILED,
    REJECTED,
    EXPIRED;

    companion object {
        fun fromWire(value: String): KfCommandResultStatus? =
            entries.firstOrNull { it.name == value }
    }
}

enum class KfAuditOutcome {
    ALLOWED,
    DENIED,
    COMPLETED,
    FAILED,
    EXPIRED,
}

/** Common event envelope: identity, time, source and correlation. */
data class KfMeta(
    val eventId: String,
    val occurredAt: Long,
    val source: String,
    val correlationId: String? = null,
) {
    fun validate(schema: String): List<String> = buildList {
        if (eventId.isBlank()) add("$schema: event_id is required")
        if (occurredAt <= 0L) add("$schema: occurred_at must be a positive epoch-millis value")
        if (source.isBlank()) add("$schema: source is required")
        if (correlationId != null && correlationId.isBlank()) {
            add("$schema: correlation_id cannot be blank when present")
        }
    }

    fun writeTo(json: JSONObject): JSONObject = json
        .put("event_id", eventId)
        .put("occurred_at", formatKfTimestamp(occurredAt))
        .put("source", source)
        .apply { correlationId?.let { put("correlation_id", it) } }

    companion object {
        fun read(json: JSONObject): KfMeta = KfMeta(
            eventId = json.optString("event_id"),
            occurredAt = parseKfTimestamp(json.optString("occurred_at")),
            source = json.optString("source"),
            correlationId = json.optString("correlation_id").takeIf { it.isNotBlank() },
        )
    }
}

interface KfPayload {
    val schema: String
    val meta: KfMeta
    fun validate(): List<String>
    fun toJson(): JSONObject
}

/** Infrastructure, host, guest, storage, backup, network, service and security alert. */
data class KfInfrastructureAlert(
    override val meta: KfMeta,
    val kind: KfInfrastructureKind,
    val priority: KfPriority,
    val title: String,
    val summary: String,
    val deliveryState: KfDeliveryState = KfDeliveryState.MESSAGE_PUBLISHED,
    val details: Map<String, String> = emptyMap(),
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (title.isBlank()) add("$schema: title is required")
        if (summary.isBlank()) add("$schema: summary is required")
        if (details.keys.any { it.isSensitiveKey() }) {
            add("$schema: details must never contain credential-like keys")
        }
    }

    override fun toJson(): JSONObject {
        val detailJson = JSONObject()
        details.forEach { (key, value) -> detailJson.put(key, value) }
        return meta.writeTo(
            JSONObject()
                .put("schema", schema)
                .put("kind", kind.name)
                .put("priority", priority.name)
                .put("title", title)
                .put("summary", summary)
                .put("delivery_state", deliveryState.name)
                .put("details", detailJson),
        )
    }

    companion object {
        const val SCHEMA = "kf.infrastructure.v1"

        fun fromJson(json: JSONObject): Result<KfInfrastructureAlert> = runCatching {
            val kind = KfInfrastructureKind.fromWire(json.getString("kind"))
                ?: error("unknown infrastructure kind")
            val priority = KfPriority.entries.firstOrNull { it.name == json.getString("priority") }
                ?: error("unknown priority")
            val delivery = KfDeliveryState.entries.firstOrNull {
                it.name == json.optString("delivery_state", KfDeliveryState.MESSAGE_PUBLISHED.name)
            } ?: error("unknown delivery state")
            val detailsJson = json.optJSONObject("details") ?: JSONObject()
            val details = buildMap {
                detailsJson.keys().forEach { key -> put(key, detailsJson.getString(key)) }
            }
            KfInfrastructureAlert(
                meta = KfMeta.read(json),
                kind = kind,
                priority = priority,
                title = json.getString("title"),
                summary = json.getString("summary"),
                deliveryState = delivery,
                details = details,
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** Stage-level music request/progress event. */
data class KfMusicRequest(
    override val meta: KfMeta,
    val runId: String,
    val requestRef: String,
    val status: KfPipelineStatus,
    val stage: KfMusicStage,
    val error: String? = null,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (runId.isBlank()) add("$schema: run_id is required")
        if (requestRef.isBlank()) add("$schema: request_ref is required")
        if (status == KfPipelineStatus.FAILED && error.isNullOrBlank()) {
            add("$schema: error is required when status is FAILED")
        }
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("run_id", runId)
            .put("request_ref", requestRef)
            .put("status", status.name)
            .put("stage", stage.name)
            .apply { error?.let { put("error", it) } },
    )

    companion object {
        const val SCHEMA = "kf.music.request.v1"

        fun fromJson(json: JSONObject): Result<KfMusicRequest> = runCatching {
            KfMusicRequest(
                meta = KfMeta.read(json),
                runId = json.getString("run_id"),
                requestRef = json.getString("request_ref"),
                status = KfPipelineStatus.fromWire(json.getString("status"))
                    ?: error("unknown pipeline status"),
                stage = KfMusicStage.fromWire(json.getString("stage"))
                    ?: error("unknown music stage"),
                error = json.optString("error").takeIf { it.isNotBlank() },
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** One final report per music run or batch. */
data class KfMusicReport(
    override val meta: KfMeta,
    val runId: String,
    val status: KfPipelineStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val requested: Int,
    val downloaded: Int,
    val skipped: Int,
    val failed: Int,
    val indexed: Int,
    val error: String? = null,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (runId.isBlank()) add("$schema: run_id is required")
        if (startedAt <= 0L) add("$schema: started_at must be positive")
        if (finishedAt != null && finishedAt < startedAt) {
            add("$schema: finished_at cannot precede started_at")
        }
        if (listOf(requested, downloaded, skipped, failed, indexed).any { it < 0 }) {
            add("$schema: counters cannot be negative")
        }
        if (downloaded + skipped + failed > requested) {
            add("$schema: downloaded + skipped + failed cannot exceed requested")
        }
        if (status == KfPipelineStatus.FAILED && error.isNullOrBlank()) {
            add("$schema: error is required when status is FAILED")
        }
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("run_id", runId)
            .put("status", status.name)
            .put("started_at", formatKfTimestamp(startedAt))
            .put("requested", requested)
            .put("downloaded", downloaded)
            .put("skipped", skipped)
            .put("failed", failed)
            .put("indexed", indexed)
            .apply {
                finishedAt?.let { put("finished_at", formatKfTimestamp(it)) }
                error?.let { put("error", it) }
            },
    )

    companion object {
        const val SCHEMA = "kf.music.report.v1"

        fun fromJson(json: JSONObject): Result<KfMusicReport> = runCatching {
            KfMusicReport(
                meta = KfMeta.read(json),
                runId = json.getString("run_id"),
                status = KfPipelineStatus.fromWire(json.getString("status"))
                    ?: error("unknown pipeline status"),
                startedAt = parseKfTimestamp(json.getString("started_at")),
                finishedAt = json.optString("finished_at").takeIf { it.isNotBlank() }
                    ?.let(::parseKfTimestamp),
                requested = json.getInt("requested"),
                downloaded = json.getInt("downloaded"),
                skipped = json.getInt("skipped"),
                failed = json.getInt("failed"),
                indexed = json.getInt("indexed"),
                error = json.optString("error").takeIf { it.isNotBlank() },
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** Reserved stickers pipeline stage event. Never claim it is implemented. */
data class KfStickerRequest(
    override val meta: KfMeta,
    val runId: String,
    val requestRef: String,
    val status: KfPipelineStatus,
    val stage: KfStickerStage,
    val error: String? = null,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (runId.isBlank()) add("$schema: run_id is required")
        if (requestRef.isBlank()) add("$schema: request_ref is required")
        if (status == KfPipelineStatus.FAILED && error.isNullOrBlank()) {
            add("$schema: error is required when status is FAILED")
        }
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("run_id", runId)
            .put("request_ref", requestRef)
            .put("status", status.name)
            .put("stage", stage.name)
            .apply { error?.let { put("error", it) } },
    )

    companion object {
        const val SCHEMA = "kf.stickers.request.v1"

        fun fromJson(json: JSONObject): Result<KfStickerRequest> = runCatching {
            KfStickerRequest(
                meta = KfMeta.read(json),
                runId = json.getString("run_id"),
                requestRef = json.getString("request_ref"),
                status = KfPipelineStatus.fromWire(json.getString("status"))
                    ?: error("unknown pipeline status"),
                stage = KfStickerStage.fromWire(json.getString("stage"))
                    ?: error("unknown sticker stage"),
                error = json.optString("error").takeIf { it.isNotBlank() },
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** Reserved stickers run summary. */
data class KfStickerReport(
    override val meta: KfMeta,
    val runId: String,
    val status: KfPipelineStatus,
    val generated: Int,
    val reviewed: Int,
    val rejected: Int,
    val packed: Int,
    val failed: Int,
    val error: String? = null,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (runId.isBlank()) add("$schema: run_id is required")
        if (listOf(generated, reviewed, rejected, packed, failed).any { it < 0 }) {
            add("$schema: counters cannot be negative")
        }
        if (status == KfPipelineStatus.FAILED && error.isNullOrBlank()) {
            add("$schema: error is required when status is FAILED")
        }
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("run_id", runId)
            .put("status", status.name)
            .put("generated", generated)
            .put("reviewed", reviewed)
            .put("rejected", rejected)
            .put("packed", packed)
            .put("failed", failed)
            .apply { error?.let { put("error", it) } },
    )

    companion object {
        const val SCHEMA = "kf.stickers.report.v1"

        fun fromJson(json: JSONObject): Result<KfStickerReport> = runCatching {
            KfStickerReport(
                meta = KfMeta.read(json),
                runId = json.getString("run_id"),
                status = KfPipelineStatus.fromWire(json.getString("status"))
                    ?: error("unknown pipeline status"),
                generated = json.getInt("generated"),
                reviewed = json.getInt("reviewed"),
                rejected = json.getInt("rejected"),
                packed = json.getInt("packed"),
                failed = json.getInt("failed"),
                error = json.optString("error").takeIf { it.isNotBlank() },
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** News card. The full article stays in the news application. */
data class KfNewsCard(
    override val meta: KfMeta,
    val headline: String,
    val newsSource: String,
    val category: String,
    val summary: String,
    val url: String,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (headline.isBlank()) add("$schema: headline is required")
        if (newsSource.isBlank()) add("$schema: news_source is required")
        if (category.isBlank()) add("$schema: category is required")
        if (summary.isBlank()) add("$schema: summary is required")
        if (!url.isSafeHttpUrl()) add("$schema: url must be an http(s) URL")
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("headline", headline)
            .put("news_source", newsSource)
            .put("category", category)
            .put("summary", summary)
            .put("url", url),
    )

    companion object {
        const val SCHEMA = "kf.news.card.v1"

        fun fromJson(json: JSONObject): Result<KfNewsCard> = runCatching {
            KfNewsCard(
                meta = KfMeta.read(json),
                headline = json.getString("headline"),
                newsSource = json.getString("news_source"),
                category = json.getString("category"),
                summary = json.getString("summary"),
                url = json.getString("url"),
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** News scraper run report. */
data class KfNewsReport(
    override val meta: KfMeta,
    val runId: String,
    val status: KfPipelineStatus,
    val scanned: Int,
    val accepted: Int,
    val duplicates: Int,
    val failed: Int,
    val error: String? = null,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (runId.isBlank()) add("$schema: run_id is required")
        if (listOf(scanned, accepted, duplicates, failed).any { it < 0 }) {
            add("$schema: counters cannot be negative")
        }
        if (accepted + duplicates + failed > scanned) {
            add("$schema: accepted + duplicates + failed cannot exceed scanned")
        }
        if (status == KfPipelineStatus.FAILED && error.isNullOrBlank()) {
            add("$schema: error is required when status is FAILED")
        }
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("run_id", runId)
            .put("status", status.name)
            .put("scanned", scanned)
            .put("accepted", accepted)
            .put("duplicates", duplicates)
            .put("failed", failed)
            .apply { error?.let { put("error", it) } },
    )

    companion object {
        const val SCHEMA = "kf.news.report.v1"

        fun fromJson(json: JSONObject): Result<KfNewsReport> = runCatching {
            KfNewsReport(
                meta = KfMeta.read(json),
                runId = json.getString("run_id"),
                status = KfPipelineStatus.fromWire(json.getString("status"))
                    ?: error("unknown pipeline status"),
                scanned = json.getInt("scanned"),
                accepted = json.getInt("accepted"),
                duplicates = json.getInt("duplicates"),
                failed = json.getInt("failed"),
                error = json.optString("error").takeIf { it.isNotBlank() },
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/** Correlated command result. [auditId] points at the audit record. */
data class KfCommandResult(
    override val meta: KfMeta,
    val commandId: String,
    val status: KfCommandResultStatus,
    val ok: Boolean,
    val summary: String,
    val errorCode: String? = null,
    val auditId: String? = null,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (commandId.isBlank()) add("$schema: command_id is required")
        if (summary.isBlank()) add("$schema: summary is required")
        if (meta.correlationId.isNullOrBlank()) {
            add("$schema: correlation_id is required")
        } else if (meta.correlationId != commandId) {
            add("$schema: correlation_id must equal command_id")
        }
        if (!ok && status == KfCommandResultStatus.COMPLETED) {
            add("$schema: ok=false cannot carry COMPLETED status")
        }
        if (!ok && status in setOf(
                KfCommandResultStatus.FAILED,
                KfCommandResultStatus.REJECTED,
                KfCommandResultStatus.EXPIRED,
            ) && errorCode.isNullOrBlank()
        ) {
            add("$schema: error_code is required for a failed result")
        }
        if (ok && errorCode != null) {
            add("$schema: error_code must be absent for a successful result")
        }
        if (status == KfCommandResultStatus.ACCEPTED && ok) {
            add("$schema: ACCEPTED is not a completed success")
        }
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("command_id", commandId)
            .put("status", status.name)
            .put("ok", ok)
            .put("summary", summary)
            .apply {
                errorCode?.let { put("error_code", it) }
                auditId?.let { put("audit_id", it) }
            },
    )

    companion object {
        const val SCHEMA = "kf.command.result.v1"

        fun fromJson(json: JSONObject): Result<KfCommandResult> = runCatching {
            KfCommandResult(
                meta = KfMeta.read(json),
                commandId = json.getString("command_id"),
                status = KfCommandResultStatus.fromWire(json.getString("status"))
                    ?: error("unknown command result status"),
                ok = json.getBoolean("ok"),
                summary = json.getString("summary"),
                errorCode = json.optString("error_code").takeIf { it.isNotBlank() },
                auditId = json.optString("audit_id").takeIf { it.isNotBlank() },
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

/**
 * Administrative audit event. Deliberately excludes credentials and free-form
 * request payloads. This model is designed for a durable append-only store; the
 * Phase 1 gateway sink is explicitly in-memory.
 */
data class KfAdminAuditEvent(
    override val meta: KfMeta,
    val actor: String,
    val operationId: String,
    val target: String,
    val risk: KfRisk,
    val outcome: KfAuditOutcome,
    val auditId: String,
    val details: Map<String, String> = emptyMap(),
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (actor.isBlank()) add("$schema: actor is required")
        if (operationId.isBlank()) add("$schema: operation_id is required")
        if (target.isBlank()) add("$schema: target is required")
        if (auditId.isBlank()) add("$schema: audit_id is required")
        if (details.keys.any { it.isSensitiveKey() }) {
            add("$schema: details must never contain credential-like keys")
        }
    }

    override fun toJson(): JSONObject {
        val detailJson = JSONObject()
        details.forEach { (key, value) -> detailJson.put(key, value) }
        return meta.writeTo(
            JSONObject()
                .put("schema", schema)
                .put("actor", actor)
                .put("operation_id", operationId)
                .put("target", target)
                .put("risk", risk.name)
                .put("outcome", outcome.name)
                .put("audit_id", auditId)
                .put("details", detailJson),
        )
    }

    companion object {
        const val SCHEMA = "kf.admin.audit.v1"

        fun fromJson(json: JSONObject): Result<KfAdminAuditEvent> = runCatching {
            val detailJson = json.optJSONObject("details") ?: JSONObject()
            val details = buildMap {
                detailJson.keys().forEach { key -> put(key, detailJson.getString(key)) }
            }
            KfAdminAuditEvent(
                meta = KfMeta.read(json),
                actor = json.getString("actor"),
                operationId = json.getString("operation_id"),
                target = json.getString("target"),
                risk = KfRisk.entries.firstOrNull { it.name == json.getString("risk") }
                    ?: error("unknown risk"),
                outcome = KfAuditOutcome.entries.firstOrNull { it.name == json.getString("outcome") }
                    ?: error("unknown outcome"),
                auditId = json.getString("audit_id"),
                details = details,
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

data class KfDiaryEvent(
    override val meta: KfMeta,
    val entryId: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (entryId.isBlank()) add("$schema: entry_id is required")
        if (title.isBlank()) add("$schema: title is required")
    }

    override fun toJson(): JSONObject = meta.writeTo(
        JSONObject()
            .put("schema", schema)
            .put("entry_id", entryId)
            .put("title", title)
            .put("body", body)
            .put("tags", JSONArray(tags)),
    )

    companion object {
        const val SCHEMA = "kf.diary.v1"

        fun fromJson(json: JSONObject): Result<KfDiaryEvent> = runCatching {
            val tagsJson = json.optJSONArray("tags") ?: JSONArray()
            val tags = buildList {
                for (index in 0 until tagsJson.length()) add(tagsJson.getString(index))
            }
            KfDiaryEvent(
                meta = KfMeta.read(json),
                entryId = json.getString("entry_id"),
                title = json.getString("title"),
                body = json.optString("body"),
                tags = tags,
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

data class KfDigest(
    override val meta: KfMeta,
    val periodStart: Long,
    val periodEnd: Long,
    val counts: Map<String, Int>,
    val summary: String,
) : KfPayload {
    override val schema: String get() = SCHEMA

    override fun validate(): List<String> = buildList {
        addAll(meta.validate(schema))
        if (periodStart <= 0L || periodEnd <= 0L) add("$schema: period bounds must be positive")
        if (periodEnd < periodStart) add("$schema: period_end cannot precede period_start")
        if (counts.values.any { it < 0 }) add("$schema: counts cannot be negative")
        if (summary.isBlank()) add("$schema: summary is required")
    }

    override fun toJson(): JSONObject {
        val countJson = JSONObject()
        counts.forEach { (key, value) -> countJson.put(key, value) }
        return meta.writeTo(
            JSONObject()
                .put("schema", schema)
                .put("period_start", formatKfTimestamp(periodStart))
                .put("period_end", formatKfTimestamp(periodEnd))
                .put("counts", countJson)
                .put("summary", summary),
        )
    }

    companion object {
        const val SCHEMA = "kf.digest.v1"

        fun fromJson(json: JSONObject): Result<KfDigest> = runCatching {
            val countJson = json.optJSONObject("counts") ?: JSONObject()
            val counts = buildMap {
                countJson.keys().forEach { key -> put(key, countJson.getInt(key)) }
            }
            KfDigest(
                meta = KfMeta.read(json),
                periodStart = parseKfTimestamp(json.getString("period_start")),
                periodEnd = parseKfTimestamp(json.getString("period_end")),
                counts = counts,
                summary = json.getString("summary"),
            ).also { payload -> require(payload.validate().isEmpty()) { payload.validate().joinToString("; ") } }
        }
    }
}

private const val KF_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

private fun kfTimestampFormat(): SimpleDateFormat =
    SimpleDateFormat(KF_TIMESTAMP_PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

internal fun formatKfTimestamp(epochMillis: Long): String =
    kfTimestampFormat().format(Date(epochMillis))

internal fun parseKfTimestamp(value: String): Long = try {
    kfTimestampFormat().parse(value)?.time ?: error("invalid timestamp")
} catch (_: ParseException) {
    error("invalid timestamp")
}

private fun String.isSensitiveKey(): Boolean {
    val normalized = lowercase()
    return listOf("token", "secret", "password", "authorization", "private_key", "apikey", "api_key")
        .any { normalized.contains(it) }
}

private fun String.isSafeHttpUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
