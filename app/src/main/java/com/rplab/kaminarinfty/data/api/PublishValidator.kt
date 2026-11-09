package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pure publish validation, deliberately separate from networking so an invalid
 * server/topic is rejected before any request is built.
 */
object PublishValidator {

    /** Returns a human-readable reason for the first failure, or null when valid. */
    fun validate(config: ServerConfig, request: PublishRequest): String? {
        if (!NtfyEndpoint.isValidTopic(request.topic)) {
            return "Invalid topic \"${request.topic}\": use 1-64 letters, digits, '-' or '_'"
        }
        if (request.message.isBlank()) return "Message must not be blank"
        if (request.priority !in PublishRequest.PRIORITY_MIN..PublishRequest.PRIORITY_MAX) {
            return "Priority must be between ${PublishRequest.PRIORITY_MIN} and ${PublishRequest.PRIORITY_MAX}"
        }
        request.tags.forEach { tag ->
            if (tag.isBlank() || tag.contains(',') || tag.contains('\n') || tag.contains('\r')) {
                return "Invalid tag \"$tag\": tags must be non-blank without commas or newlines"
            }
        }
        request.clickUrl?.let { value ->
            if (!isAbsoluteHttpUrl(value)) return "Click URL must be an absolute http:// or https:// URL"
        }
        request.attachUrl?.let { value ->
            if (!isAbsoluteHttpUrl(value)) return "Attachment URL must be an absolute http:// or https:// URL"
        }
        request.delay?.let { value ->
            if (!isValidDelay(value)) return "Invalid delay \"$value\": use a duration like 30m or a Unix timestamp"
        }
        if (config.serverUrl.isBlank()) return "Server URL is required"

        // A method that needs a secret must have one before we touch the network.
        when (config.authMethod) {
            AuthMethod.GUEST -> Unit
            AuthMethod.USERNAME_PASSWORD -> {
                if (config.username.isBlank() || config.tokenOrPassword.isBlank()) {
                    return "Username and password are required for basic authentication"
                }
            }
            AuthMethod.ACCESS_TOKEN -> {
                if (config.tokenOrPassword.isBlank()) {
                    return "An access token is required for bearer authentication"
                }
            }
        }
        return null
    }

    private fun isAbsoluteHttpUrl(raw: String): Boolean {
        val parsed = raw.toHttpUrlOrNull() ?: return false
        return (parsed.scheme == "http" || parsed.scheme == "https") && parsed.host.isNotBlank()
    }

    private fun isValidDelay(raw: String): Boolean =
        DELAY_DURATION_PATTERN.matches(raw) || UNIX_TIMESTAMP_PATTERN.matches(raw)

    private val DELAY_DURATION_PATTERN = Regex("^[0-9]{1,6}[smhd]$")
    private val UNIX_TIMESTAMP_PATTERN = Regex("^[0-9]{9,11}$")
}
