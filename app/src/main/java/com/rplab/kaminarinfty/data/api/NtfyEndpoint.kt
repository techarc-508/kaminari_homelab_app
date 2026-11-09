package com.rplab.kaminarinfty.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validates and builds documented ntfy URLs.
 *
 * Topic names are restricted to the documented ntfy topic charset so they can
 * be placed in a comma-separated path segment without percent-encoding, which
 * some reverse proxies would not decode before routing.
 *
 * Docs: https://docs.ntfy.sh/subscribe/api/
 */
object NtfyEndpoint {

    /** ntfy topics: 1-64 chars of letters, digits, `-` and `_`. */
    private val TOPIC_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** ntfy accepts `all`, a duration (`30m`, `12h`, `7d`), a Unix timestamp or a message ID. */
    private val DURATION_PATTERN = Regex("^[0-9]{1,6}[smhd]$")
    private val TIMESTAMP_PATTERN = Regex("^[0-9]{1,12}$")
    private val MESSAGE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

    const val DEFAULT_SINCE = "12h"

    fun isValidTopic(topic: String): Boolean = TOPIC_PATTERN.matches(topic)

    fun isValidSince(since: String): Boolean = when {
        since == "all" -> true
        DURATION_PATTERN.matches(since) -> true
        TIMESTAMP_PATTERN.matches(since) -> true
        MESSAGE_ID_PATTERN.matches(since) -> true
        else -> false
    }

    /**
     * Normalizes a user-entered base URL and rejects unsafe shapes:
     * credentials in the URL, query strings, fragments, non-HTTP schemes and
     * cleartext HTTP when [requireHttps] is set.
     */
    fun normalizeBaseUrl(raw: String, requireHttps: Boolean): Result<String> {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Server URL is required"))

        val parsed = trimmed.toHttpUrlOrNull()
            ?: return Result.failure(
                IllegalArgumentException("Server URL must include http:// or https://")
            )

        if (parsed.scheme != "http" && parsed.scheme != "https") {
            return Result.failure(IllegalArgumentException("Server URL must use http:// or https://"))
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException("Credentials must not be embedded in the server URL")
            )
        }
        if (parsed.query != null || parsed.fragment != null) {
            return Result.failure(
                IllegalArgumentException("Server URL must not contain a query string or fragment")
            )
        }
        if (requireHttps && !parsed.isHttps) {
            return Result.failure(
                IllegalArgumentException("HTTPS is enforced — use an https:// URL")
            )
        }
        return Result.success(parsed.toString().trimEnd('/'))
    }

    /**
     * `GET {base}/{topic1,topic2}/json?since=...` with optional `poll=1` for a
     * bounded catch-up request that the server closes when history is drained.
     */
    fun streamUrl(
        rawBaseUrl: String,
        topics: List<String>,
        since: String,
        requireHttps: Boolean,
        poll: Boolean,
    ): Result<String> {
        val base = normalizeBaseUrl(rawBaseUrl, requireHttps).getOrElse { return Result.failure(it) }

        val activeTopics = topics.map { it.trim() }.filter { it.isNotEmpty() }
        if (activeTopics.isEmpty()) {
            return Result.failure(IllegalArgumentException("No topics configured for subscription"))
        }
        activeTopics.firstOrNull { !isValidTopic(it) }?.let { invalid ->
            return Result.failure(
                IllegalArgumentException(
                    "Invalid topic name \"$invalid\": use 1-64 letters, digits, '-' or '_'"
                )
            )
        }
        if (!isValidSince(since)) {
            return Result.failure(IllegalArgumentException("Invalid history cursor \"$since\""))
        }

        val baseUrl = base.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Server URL is invalid"))

        val builder = baseUrl.newBuilder()
            .addPathSegment(activeTopics.joinToString(","))
            .addPathSegment("json")
            .addQueryParameter("since", since)
        if (poll) builder.addQueryParameter("poll", "1")
        return Result.success(builder.build().toString())
    }

    fun healthUrl(rawBaseUrl: String, requireHttps: Boolean): Result<String> {
        val base = normalizeBaseUrl(rawBaseUrl, requireHttps).getOrElse { return Result.failure(it) }
        val url: HttpUrl = base.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Server URL is invalid"))
        return Result.success(url.newBuilder().addPathSegments("v1/health").build().toString())
    }

    fun rootUrl(rawBaseUrl: String, requireHttps: Boolean): Result<String> =
        normalizeBaseUrl(rawBaseUrl, requireHttps)

    /**
     * `POST {base}/{topic}` — the documented ntfy publish endpoint. The topic is
     * validated and added as a single path segment so no user input can escape
     * the base path or inject a query/fragment.
     */
    fun publishUrl(rawBaseUrl: String, topic: String, requireHttps: Boolean): Result<String> {
        val base = normalizeBaseUrl(rawBaseUrl, requireHttps).getOrElse { return Result.failure(it) }
        if (!isValidTopic(topic)) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid topic name \"$topic\": use 1-64 letters, digits, '-' or '_'"
                )
            )
        }
        val baseUrl = base.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Server URL is invalid"))
        return Result.success(baseUrl.newBuilder().addPathSegment(topic).build().toString())
    }
}
