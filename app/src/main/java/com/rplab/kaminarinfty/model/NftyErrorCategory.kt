package com.rplab.kaminarinfty.model

enum class NftyErrorCategory(val label: String, val code: String) {
    DNS("DNS Resolution Failure", "ERR_NAME_NOT_RESOLVED"),
    TLS("TLS / Certificate Handshake Failure", "ERR_SSL_PROTOCOL_ERROR"),
    AUTH("Authentication Failed", "HTTP_401_UNAUTHORIZED"),
    FORBIDDEN("Server-Enforced Access Denied", "HTTP_403_FORBIDDEN"),
    TIMEOUT("Connection Timed Out", "ERR_CONNECTION_TIMED_OUT"),
    UNREACHABLE("Server Unreachable", "ERR_HOST_UNREACHABLE"),
    STREAM("Stream Interrupted", "ERR_SSE_DISCONNECTED"),
}
