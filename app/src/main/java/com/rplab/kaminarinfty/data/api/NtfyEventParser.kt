package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.NftyAction
import com.rplab.kaminarinfty.model.NftyAttachment
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed representation of a documented ntfy JSON stream event.
 * Docs: https://docs.ntfy.sh/subscribe/api/#json-messages
 */
sealed interface NtfyEvent {
    data class Open(val timestamp: Long) : NtfyEvent
    data class KeepAlive(val timestamp: Long) : NtfyEvent
    data class Message(val message: NftyMessage) : NtfyEvent
    data class MessageDeleted(val messageId: String, val topic: String?) : NtfyEvent
    data class TopicCleared(val topic: String?) : NtfyEvent
    data class PollRequest(val topic: String?) : NtfyEvent
    data class Unknown(val eventType: String) : NtfyEvent
}

sealed interface NtfyParseResult {
    data class Parsed(val event: NtfyEvent) : NtfyParseResult
    data class Invalid(val reason: String) : NtfyParseResult
}

/**
 * Validates one NDJSON line. Unparseable or incomplete lines are reported as
 * [NtfyParseResult.Invalid] so the transport can count them instead of silently
 * dropping data (AGENTS.md section 6).
 */
object NtfyEventParser {

    /** Bounds a single stream line so a hostile/broken server cannot exhaust memory. */
    const val MAX_LINE_BYTES = 512 * 1024

    private const val MAX_TAGS = 64
    private const val MAX_ACTIONS = 16
    private const val MAX_BODY_CHARS = 64 * 1024
    private const val MAX_TITLE_CHARS = 1_024

    /** ntfy sends Unix seconds; tolerate millisecond timestamps from proxies/tests. */
    private const val MILLIS_THRESHOLD = 10_000_000_000L

    fun parse(line: String): NtfyParseResult {
        if (line.isBlank()) return NtfyParseResult.Invalid("empty stream line")
        if (line.toByteArray(Charsets.UTF_8).size > MAX_LINE_BYTES) {
            return NtfyParseResult.Invalid("stream line exceeds $MAX_LINE_BYTES bytes")
        }

        val json = try {
            JSONObject(line)
        } catch (e: Exception) {
            return NtfyParseResult.Invalid("unparseable JSON (${e.javaClass.simpleName})")
        }

        val eventType = json.optString("event", "")
        if (eventType.isBlank()) return NtfyParseResult.Invalid("missing event type")

        return try {
            when (eventType) {
                "open" -> NtfyParseResult.Parsed(NtfyEvent.Open(normalizeTimestamp(json.optLong("time", 0))))
                "keepalive" -> NtfyParseResult.Parsed(
                    NtfyEvent.KeepAlive(normalizeTimestamp(json.optLong("time", 0)))
                )
                "message" -> parseMessage(json)
                "message_delete" -> {
                    val id = json.optString("id", "")
                    if (id.isBlank()) NtfyParseResult.Invalid("message_delete missing message id")
                    else NtfyParseResult.Parsed(
                        NtfyEvent.MessageDeleted(id, json.optString("topic").ifBlank { null })
                    )
                }
                "message_clear" -> NtfyParseResult.Parsed(
                    NtfyEvent.TopicCleared(json.optString("topic").ifBlank { null })
                )
                "poll_request" -> NtfyParseResult.Parsed(
                    NtfyEvent.PollRequest(json.optString("topic").ifBlank { null })
                )
                else -> NtfyParseResult.Parsed(NtfyEvent.Unknown(eventType))
            }
        } catch (e: Exception) {
            NtfyParseResult.Invalid("invalid $eventType event (${e.javaClass.simpleName})")
        }
    }

    private fun parseMessage(json: JSONObject): NtfyParseResult {
        val id = json.optString("id", "")
        if (id.isBlank()) return NtfyParseResult.Invalid("message missing id")

        val topic = json.optString("topic", "")
        if (topic.isBlank()) return NtfyParseResult.Invalid("message missing topic")

        val rawTime = json.optLong("time", -1)
        if (rawTime <= 0) return NtfyParseResult.Invalid("message missing timestamp")

        val body = json.optString("message", "")
        if (body.length > MAX_BODY_CHARS) {
            return NtfyParseResult.Invalid("message body exceeds $MAX_BODY_CHARS characters")
        }

        val title = json.optString("title", "").ifBlank { null }?.take(MAX_TITLE_CHARS)
        val priority = NftyPriority.fromLevel(json.optInt("priority", 3).coerceIn(1, 5))

        val message = NftyMessage(
            id = id,
            time = normalizeTimestamp(rawTime),
            topic = topic,
            title = title,
            message = body,
            priority = priority,
            tags = parseTags(json.optJSONArray("tags")),
            click = json.optString("click").ifBlank { null },
            attachment = parseAttachment(json.optJSONObject("attachment")),
            actions = parseActions(json.optJSONArray("actions")),
            expiry = json.optLong("expires", 0L).takeIf { it > 0 },
            isSample = false,
        )
        return NtfyParseResult.Parsed(NtfyEvent.Message(message))
    }

    fun normalizeTimestamp(raw: Long): Long =
        if (raw in 1 until MILLIS_THRESHOLD) raw * 1_000L else raw

    private fun parseTags(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), MAX_TAGS)) {
                // Only accept real strings: org.json coerces numbers/NULL into text.
                val tag = array.opt(i)
                if (tag is String && tag.isNotBlank()) add(tag)
            }
        }
    }

    private fun parseAttachment(obj: JSONObject?): NftyAttachment? {
        if (obj == null) return null
        val url = obj.optString("url", "")
        if (url.isBlank()) return null
        return NftyAttachment(
            name = obj.optString("name", "attachment").ifBlank { "attachment" },
            url = url,
            type = obj.optString("type").ifBlank { null },
            size = if (obj.has("size") && !obj.isNull("size")) obj.optLong("size") else null,
        )
    }

    private fun parseActions(array: JSONArray?): List<NftyAction> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until minOf(array.length(), MAX_ACTIONS)) {
                val act = array.optJSONObject(i) ?: continue
                add(
                    NftyAction(
                        id = act.optString("id", "act_$i"),
                        action = act.optString("action", "view"),
                        label = act.optString("label", "Action"),
                        url = act.optString("url").ifBlank { null },
                    )
                )
            }
        }
    }
}
