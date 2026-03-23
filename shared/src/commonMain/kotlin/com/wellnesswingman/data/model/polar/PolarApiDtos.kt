package com.wellnesswingman.data.model.polar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Activity ---

@Serializable
internal data class PolarActivityListResponse(
    @SerialName("activityDays") val activityDays: List<PolarActivityDayDto> = emptyList()
)

@Serializable
internal data class PolarActivityDayDto(
    @SerialName("date") val date: String? = null,
    @SerialName("activitiesPerDevice") val activitiesPerDevice: List<PolarDeviceActivityDto> = emptyList(),
    @SerialName("activitySamples") val activitySamples: PolarActivitySamplesDto? = null
)

@Serializable
internal data class PolarDeviceActivityDto(
    @SerialName("activeSteps") val activeSteps: Int? = null,
    @SerialName("activeCalories") val activeCalories: Int? = null
)

@Serializable
internal data class PolarActivitySamplesDto(
    @SerialName("stepSamples") val stepSamples: List<PolarStepSampleDto> = emptyList()
)

@Serializable
internal data class PolarStepSampleDto(
    @SerialName("time") val time: String? = null,
    @SerialName("steps") val steps: Int? = null
)

// --- Sleep ---

@Serializable
internal data class PolarSleepListResponse(
    @SerialName("sleeps") val sleeps: List<PolarSleepDto> = emptyList()
)

@Serializable
internal data class PolarSleepDto(
    @SerialName("sleepResultDate") val sleepResultDate: String? = null,
    @SerialName("duration") val duration: String? = null,
    @SerialName("deepSleep") val deepSleep: String? = null,
    @SerialName("remSleep") val remSleep: String? = null,
    @SerialName("lowLightSleep") val lowLightSleep: String? = null,
    @SerialName("awakeTime") val awakeTime: String? = null
)

// --- Training Sessions ---

@Serializable
internal data class PolarTrainingListResponse(
    @SerialName("trainingSessions") val trainingSessions: List<PolarTrainingSessionDto> = emptyList()
)

@Serializable
internal data class PolarTrainingSessionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("duration") val duration: String? = null,
    @SerialName("sport") val sport: String? = null,
    @SerialName("calories") val calories: Int? = null,
    @SerialName("distance") val distance: Double? = null,
    @SerialName("heartRate") val heartRate: PolarHeartRateDto? = null,
    @SerialName("trainingLoad") val trainingLoad: Double? = null
)

@Serializable
internal data class PolarHeartRateDto(
    @SerialName("average") val average: Int? = null,
    @SerialName("maximum") val maximum: Int? = null
)

// --- Nightly Recharge ---

@Serializable
internal data class PolarNightlyRechargeListResponse(
    @SerialName("nightlyRechargeResults") val nightlyRechargeResults: List<PolarNightlyRechargeDto> = emptyList()
)

@Serializable
internal data class PolarNightlyRechargeDto(
    @SerialName("sleepResultDate") val sleepResultDate: String? = null,
    @SerialName("ansStatus") val ansStatus: String? = null,
    @SerialName("ansRate") val ansRate: Double? = null,
    @SerialName("recoveryIndicator") val recoveryIndicator: String? = null,
    @SerialName("recoveryIndicatorSubLevel") val recoveryIndicatorSubLevel: String? = null
)
