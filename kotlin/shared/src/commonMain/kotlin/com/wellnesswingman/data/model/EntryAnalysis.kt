package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents an LLM analysis of a tracked entry.
 */
@Serializable
data class EntryAnalysis(
    val analysisId: Long = 0,
    val entryId: Long,
    val externalId: String? = null,
    val providerId: String = "",
    val model: String = "",
    val capturedAt: Instant,
    val insightsJson: String = "",

    /**
     * Version of the JSON schema used in insightsJson.
     * Enables schema evolution and backward compatibility.
     */
    val schemaVersion: String = "1.0"
)
