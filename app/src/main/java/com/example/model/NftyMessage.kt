package com.example.model

data class NftyAttachment(
    val name: String,
    val url: String,
    val type: String? = null,
    val size: Long? = null,
)

data class NftyAction(
    val id: String,
    val action: String, // "view", "broadcast", "http"
    val label: String,
    val url: String? = null,
)

data class NftyMessage(
    val id: String,
    val time: Long,
    val event: String = "message",
    val topic: String,
    val title: String? = null,
    val message: String,
    val priority: NftyPriority = NftyPriority.DEFAULT,
    val tags: List<String> = emptyList(),
    val click: String? = null,
    val attachment: NftyAttachment? = null,
    val actions: List<NftyAction> = emptyList(),
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val isSample: Boolean = true,
)
