package com.rplab.kaminarinfty.commands

import android.util.Base64
import java.security.SecureRandom
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

const val COMMAND_SCHEMA = "rplab.command.v1"
const val MOCK_COMMAND_TARGET = "mock-pve"
const val COMMAND_REPLY_TOPIC = "rplab-command-results"
const val COMMAND_EXPIRY_MS = 5L * 60L * 1_000L
const val COMMAND_CLOCK_SKEW_MS = 60L * 1_000L

/**
 * Approved wire contract. Timestamps are epoch millis in the app and ISO-8601
 * UTC in JSON. The envelope is a value object: it never carries credentials.
 */
data class CommandEnvelope(
    val schema: String = COMMAND_SCHEMA,
    val commandId: String,
    val nonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val requestedBy: String,
    val target: String = MOCK_COMMAND_TARGET,
    val action: String,
    val args: Map<String, String> = emptyMap(),
    val replyTopic: String = COMMAND_REPLY_TOPIC,
    val requiresConfirmation: Boolean,
) {
    fun toJson(): String {
        val argsJson = JSONObject()
        args.forEach { (key, value) -> argsJson.put(key, value) }
        return JSONObject()
            .put("schema", schema)
            .put("command_id", commandId)
            .put("nonce", nonce)
            .put("issued_at", formatTimestamp(issuedAt))
            .put("expires_at", formatTimestamp(expiresAt))
            .put("requested_by", requestedBy)
            .put("target", target)
            .put("action", action)
            .put("args", argsJson)
            .put("reply_topic", replyTopic)
            .put("requires_confirmation", requiresConfirmation)
            .toString()
    }

    companion object {
        fun fromJson(raw: String): Result<CommandEnvelope> = runCatching {
            val json = JSONObject(raw)
            val argsJson = json.getJSONObject("args")
            val args = buildMap {
                argsJson.keys().forEach { key ->
                    put(key, argsJson.getString(key))
                }
            }
            CommandEnvelope(
                schema = json.getString("schema"),
                commandId = json.getString("command_id"),
                nonce = json.getString("nonce"),
                issuedAt = parseTimestamp(json.getString("issued_at")),
                expiresAt = parseTimestamp(json.getString("expires_at")),
                requestedBy = json.getString("requested_by"),
                target = json.getString("target"),
                action = json.getString("action"),
                args = args,
                replyTopic = json.getString("reply_topic"),
                requiresConfirmation = json.getBoolean("requires_confirmation"),
            )
        }
    }
}

/** 32 random bytes, Base64 URL-safe without padding (43 chars). */
object CommandNonce {
    private val random = SecureRandom()
    private val pattern = Regex("^[A-Za-z0-9_-]{43}$")

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }

    fun isValid(value: String): Boolean = pattern.matches(value)
}

private const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

private fun timestampFormat(): SimpleDateFormat = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

internal fun formatTimestamp(epochMillis: Long): String =
    timestampFormat().format(Date(epochMillis))

internal fun parseTimestamp(value: String): Long = try {
    timestampFormat().parse(value)?.time
        ?: throw IllegalArgumentException("Invalid timestamp")
} catch (_: ParseException) {
    throw IllegalArgumentException("Invalid timestamp")
}
