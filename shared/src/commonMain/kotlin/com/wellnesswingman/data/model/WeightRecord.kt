package com.wellnesswingman.data.model

import kotlinx.datetime.Instant

enum class WeightUnit(val value: String) {
    KG("kg"),
    LBS("lbs");

    override fun toString(): String = value

    companion object {
        fun fromString(s: String): WeightUnit = when (s.lowercase()) {
            "kg" -> KG
            "lbs" -> LBS
            else -> KG
        }
    }
}

enum class WeightSource(val value: String) {
    MANUAL("Manual"),
    LLM_DETECTED("LlmDetected");

    override fun toString(): String = value

    val displayLabel: String
        get() = when (this) {
            MANUAL -> "Manual"
            LLM_DETECTED -> "Auto"
        }

    companion object {
        fun fromString(s: String): WeightSource = when (s) {
            "LlmDetected" -> LLM_DETECTED
            else -> MANUAL
        }
    }
}

/**
 * Represents a single weight measurement entry.
 */
data class WeightRecord(
    val weightRecordId: Long = 0,
    val externalId: String? = null,
    val recordedAt: Instant,
    val weightValue: Double,
    val weightUnit: String,
    val source: String,
    val relatedEntryId: Long? = null
)
