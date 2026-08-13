package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured JSON schema for sleep analysis results from the LLM.
 */
@Serializable
data class SleepAnalysisResult(
    @SerialName("durationHours")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val durationHours: Double? = null,

    @SerialName("sleepScore")
    @Serializable(with = LenientNullableDoubleSerializer::class)
    val sleepScore: Double? = null,

    @SerialName("qualitySummary")
    val qualitySummary: String? = null,

    @SerialName("environmentNotes")
    val environmentNotes: List<String> = emptyList(),

    @SerialName("recommendations")
    val recommendations: List<String> = emptyList()
)
