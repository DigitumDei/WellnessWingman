package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured JSON schema for exercise analysis results from the LLM.
 */
@Serializable
data class ExerciseAnalysisResult(
    @SerialName("schemaVersion")
    val schemaVersion: String = "1.0",

    @SerialName("activityType")
    val activityType: String? = null,

    @SerialName("metrics")
    val metrics: ExerciseMetrics = ExerciseMetrics(),

    @SerialName("insights")
    val insights: ExerciseInsights? = null,

    @SerialName("warnings")
    val warnings: List<String> = emptyList()
)

@Serializable
data class ExerciseMetrics(
    @SerialName("distance")
    val distance: Double? = null,

    @SerialName("distanceUnit")
    val distanceUnit: String? = null,

    @SerialName("durationMinutes")
    val durationMinutes: Double? = null,

    @SerialName("averagePace")
    val averagePace: String? = null,

    @SerialName("averageSpeed")
    val averageSpeed: Double? = null,

    @SerialName("speedUnit")
    val speedUnit: String? = null,

    @SerialName("calories")
    val calories: Double? = null,

    @SerialName("averageHeartRate")
    val averageHeartRate: Double? = null,

    @SerialName("maxHeartRate")
    val maxHeartRate: Double? = null,

    @SerialName("steps")
    val steps: Double? = null,

    @SerialName("elevationGain")
    val elevationGain: Double? = null,

    @SerialName("elevationUnit")
    val elevationUnit: String? = null
)

@Serializable
data class ExerciseInsights(
    @SerialName("summary")
    val summary: String? = null,

    @SerialName("positives")
    val positives: List<String> = emptyList(),

    @SerialName("improvements")
    val improvements: List<String> = emptyList(),

    @SerialName("recommendations")
    val recommendations: List<String> = emptyList()
)
