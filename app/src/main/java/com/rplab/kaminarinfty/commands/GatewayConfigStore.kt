package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.preferences.SecretStore

/** Non-secret view of the gateway configuration for the UI. */
data class GatewayConfigState(
    val url: String,
    val hasToken: Boolean,
) {
    val isConfigured: Boolean get() = url.isNotBlank() && hasToken
}

/**
 * Persists the gateway endpoint (DataStore) and token (Keystore-backed
 * [SecretStore]). The token is never returned to UI state, only used to build a
 * [GatewayConnection] at execution time.
 */
interface GatewayConfigStore {
    suspend fun state(): GatewayConfigState
    suspend fun connection(): GatewayConnection?
    suspend fun save(url: String, token: String?)
    suspend fun clear()
}

class DefaultGatewayConfigStore(
    private val preferences: AppPreferences,
    private val secrets: SecretStore,
) : GatewayConfigStore {

    override suspend fun state(): GatewayConfigState = GatewayConfigState(
        url = preferences.gatewayUrl(),
        hasToken = !secrets.get(TOKEN_KEY).isNullOrBlank(),
    )

    override suspend fun connection(): GatewayConnection? {
        val url = preferences.gatewayUrl()
        val token = secrets.get(TOKEN_KEY).orEmpty()
        if (url.isBlank() || token.isBlank()) return null
        return GatewayConnection(baseUrl = url, token = token)
    }

    override suspend fun save(url: String, token: String?) {
        val normalized = GatewayUrlPolicy.normalize(url) ?: url.trim()
        preferences.setGatewayUrl(normalized)
        val replacement = token?.trim()
        if (!replacement.isNullOrEmpty()) secrets.put(TOKEN_KEY, replacement)
    }

    override suspend fun clear() {
        secrets.remove(TOKEN_KEY)
        preferences.setGatewayUrl("")
    }

    private companion object {
        const val TOKEN_KEY = "gateway_token"
    }
}
