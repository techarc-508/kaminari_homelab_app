package com.rplab.kaminarinfty.model

import com.rplab.kaminarinfty.contract.KfTopicCategory

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
    /** Approved `kf-` category, or OTHER for legacy/user-entered topics. */
    val category: KfTopicCategory = KfTopicCategory.OTHER,
    /** Default notification priority for this topic (1–5). */
    val priority: Int = 3,
)
