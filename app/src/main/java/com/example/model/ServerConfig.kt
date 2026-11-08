package com.example.model

enum class AuthMethod(val displayName: String) {
    GUEST("Guest / Anonymous"),
    USERNAME_PASSWORD("Username & Password"),
    ACCESS_TOKEN("Access Token"),
}

enum class AuthState(val statusLabel: String, val isConnected: Boolean) {
    NOT_CONFIGURED("NOT CONFIGURED", false),
    CONNECTING("CONNECTING...", false),
    AUTHENTICATED("SUPERUSER", true),
    GUEST("GUEST SESSION", true),
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
    val autoReconnect: Boolean = true,
)
