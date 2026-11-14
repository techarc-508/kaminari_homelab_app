package com.rplab.kaminarinfty.commands

import java.net.URI

/**
 * One concrete gateway endpoint + credential pair.
 *
 * [requireHttps] is false only for an explicitly loopback test connection; the
 * production settings store always leaves it true.
 */
data class GatewayConnection(
    val baseUrl: String,
    val token: String,
    val requireHttps: Boolean = true,
)

/** Resolved on every execution so a settings change takes effect immediately. */
fun interface GatewayConnectionProvider {
    fun current(): GatewayConnection?
}

/**
 * URL rules for the gateway endpoint. A gateway URL must be an absolute
 * http(s) URL with a host and no userinfo, query or fragment. Plain http is
 * accepted only for a loopback address used in a controlled test.
 */
object GatewayUrlPolicy {

    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        if (!uri.userInfo.isNullOrEmpty()) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        val port = uri.port
        if (port != -1 && port !in 1..65535) return null
        if (uri.rawPath?.let { it.isNotEmpty() && it != "/" } == true) return null
        return trimmed
    }

    fun isLoopback(raw: String): Boolean {
        val host = runCatching { URI(raw.trim()).host }.getOrNull()?.lowercase() ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"
    }

    /** Human-readable reason the URL cannot be used, or null when it is valid. */
    fun validationError(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return "Enter the gateway URL"
        val normalized = normalize(value) ?: return "Enter an absolute http(s) URL without a path or query"
        if (normalized.startsWith("http://") && !isLoopback(normalized)) {
            return "Plain http is only allowed for localhost. Use https:// for a real gateway."
        }
        return null
    }
}
