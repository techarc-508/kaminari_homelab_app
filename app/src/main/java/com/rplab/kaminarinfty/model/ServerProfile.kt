package com.rplab.kaminarinfty.model

/**
 * A saved server profile. The credential itself is never part of this model:
 * it lives only in the Keystore-backed [com.rplab.kaminarinfty.data.preferences.SecretStore]
 * under the same [id].
 */
data class ServerProfile(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val authMethod: AuthMethod,
    val hasStoredSecret: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
