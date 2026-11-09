package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import okhttp3.Credentials
import okhttp3.Request

/**
 * Single place that turns a [ServerConfig] into request authentication.
 *
 * Credentials are only ever emitted as an `Authorization` header — never in a
 * URL, a log line or a body. A method that is missing its secret yields no
 * header at all; callers that must not send an unauthenticated request validate
 * first (see [PublishValidator]).
 */
object NtfyAuth {

    fun apply(builder: Request.Builder, config: ServerConfig) {
        header(config)?.let { builder.header("Authorization", it) }
    }

    /** The header value for [config], or null when no credential should be sent. */
    fun header(config: ServerConfig): String? = when (config.authMethod) {
        AuthMethod.GUEST -> null
        AuthMethod.USERNAME_PASSWORD -> {
            if (config.username.isBlank() || config.tokenOrPassword.isBlank()) {
                null
            } else {
                Credentials.basic(config.username, sanitizeHeaderValue(config.tokenOrPassword))
            }
        }
        AuthMethod.ACCESS_TOKEN -> config.tokenOrPassword
            .takeIf { it.isNotBlank() }
            ?.let { "Bearer ${sanitizeHeaderValue(it)}" }
    }

    /** Prevents header injection through a pasted credential containing CR/LF. */
    fun sanitizeHeaderValue(raw: String): String =
        raw.trim().replace("\r", "").replace("\n", "")
}
