package com.example.model

enum class TopicPermission(val label: String) {
    GUEST_READ("GUEST READ"),
    AUTH_REQUIRED("AUTH ONLY"),
    RESTRICTED("ADMIN"),
}

data class NftyTopic(
    val name: String,
    val description: String,
    val messageCount: Int = 0,
    val isSubscribed: Boolean = true,
    val isPaused: Boolean = false,
    val lastActivityTime: Long = System.currentTimeMillis(),
    val permission: TopicPermission = TopicPermission.GUEST_READ,
)
