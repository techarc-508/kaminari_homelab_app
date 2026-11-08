package com.rplab.kaminarinfty.model

enum class NftyPriority(val level: Int, val label: String) {
    MIN(1, "MIN"),
    LOW(2, "LOW"),
    DEFAULT(3, "DEFAULT"),
    HIGH(4, "HIGH"),
    URGENT(5, "URGENT");

    companion object {
        fun fromLevel(level: Int?): NftyPriority {
            return when (level) {
                1 -> MIN
                2 -> LOW
                4 -> HIGH
                5 -> URGENT
                else -> DEFAULT
            }
        }
    }
}
