package com.rplab.kaminarinfty.model

enum class AuthMethod(val displayName: String) {
    GUEST("Guest / Anonymous"),
    USERNAME_PASSWORD("Username & Password"),
    ACCESS_TOKEN("Access Token"),
}

enum class AuthState(val statusLabel: String, val isConnected: Boolean) {
    NOT_CONFIGURED("NOT CONFIGURED", false),
    CONNECTING("CONNECTING...", false),

    /** Credentials accepted by the server for the subscribed topics. */
    AUTHENTICATED("AUTHENTICATED", true),
    GUEST("GUEST SESSION", true),

    /** Endpoint answered, but no credential was proven (health is public). */
    REACHABLE("REACHABLE — AUTH UNVERIFIED", true),
    AUTHENTICATION_FAILED("AUTH FAILED", false),
    PERMISSION_DENIED("PERMISSION DENIED", false),
    DISCONNECTED("OFFLINE", false),
    RECONNECTING("RECONNECTING...", false),
}

data class ServerConfig(
    val serverUrl: String = "https://nfty.rplab.qzz.io",
    val authMethod: AuthMethod = AuthMethod.GUEST,
    val username: String = "",
    val tokenOrPassword: String = "",
    val useHttpsOnly: Boolean = true,
) {
    /** A secret is present for a method that needs one. */
    fun hasCredentials(): Boolean = tokenOrPassword.isNotBlank()

    /** True when the selected auth method requires a credential. */
    fun requiresCredentials(): Boolean = authMethod.usesCredentials()
}

fun AuthMethod.usesCredentials(): Boolean = this != AuthMethod.GUEST
