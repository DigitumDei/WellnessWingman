package com.wellnesswingman.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Processing status for tracked entries.
 */
@Serializable
enum class ProcessingStatus {
    @SerialName("Pending")
    PENDING,      // Entry created, analysis not started

    @SerialName("Processing")
    PROCESSING,   // Analysis in progress

    @SerialName("Completed")
    COMPLETED,    // Analysis finished successfully

    @SerialName("Failed")
    FAILED,       // Analysis failed (can be retried)

    @SerialName("Skipped")
    SKIPPED;      // Analysis skipped (no API key, unsupported provider, etc.)

    companion object {
        fun fromString(value: String?): ProcessingStatus {
            if (value.isNullOrBlank()) return PENDING

            return when (value.trim().lowercase()) {
                "pending" -> PENDING
                "processing" -> PROCESSING
                "completed" -> COMPLETED
                "failed" -> FAILED
                "skipped" -> SKIPPED
                else -> PENDING
            }
        }
    }

    fun toStorageString(): String {
        return when (this) {
            PENDING -> "Pending"
            PROCESSING -> "Processing"
            COMPLETED -> "Completed"
            FAILED -> "Failed"
            SKIPPED -> "Skipped"
        }
    }
}
