package com.wellnesswingman.data.model

import kotlinx.datetime.Instant

/**
 * Represents a single weight measurement entry.
 */
data class WeightRecord(
    val weightRecordId: Long = 0,
    val externalId: String? = null,
    val recordedAt: Instant,
    val weightValue: Double,
    val weightUnit: String,      // "kg" or "lbs"
    val source: String,          // "Manual" or "LlmDetected"
    val relatedEntryId: Long? = null
)
