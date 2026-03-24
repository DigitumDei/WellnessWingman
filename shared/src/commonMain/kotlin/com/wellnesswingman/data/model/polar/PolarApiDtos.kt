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
    @SerialName("target") val target: PolarActivityTargetDto? = null
)

@Serializable
internal data class PolarDeviceActivityDto(
    @SerialName("activitySamples") val activitySamples: List<PolarActivitySampleDto> = emptyList()
)

@Serializable
internal data class PolarActivitySampleDto(
    @SerialName("stepSamples") val stepSamples: PolarStepSamplesDto? = null,
    @SerialName("metSamples") val metSamples: PolarMetSamplesDto? = null
)

@Serializable
internal data class PolarStepSamplesDto(
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("interval") val interval: Long? = null,
    @SerialName("steps") val steps: List<Int> = emptyList()
)

@Serializable
internal data class PolarMetSamplesDto(
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("interval") val interval: Long? = null,
    @SerialName("mets") val mets: List<Double> = emptyList()
)

@Serializable
internal data class PolarActivityTargetDto(
    @SerialName("minDailyMetGoal") val minDailyMetGoal: Int? = null
)

// --- Sleep ---

@Serializable
internal data class PolarSleepListResponse(
    @SerialName("nightSleeps") val nightSleeps: List<PolarSleepDto> = emptyList()
)

@Serializable
internal data class PolarSleepDto(
    @SerialName("sleepDate") val sleepDate: String? = null,
    @SerialName("sleepResult") val sleepResult: PolarSleepResultDto? = null,
    @SerialName("sleepEvaluation") val sleepEvaluation: PolarSleepEvaluationDto? = null,
    @SerialName("sleepScore") val sleepScore: PolarSleepScoreDto? = null
)

@Serializable
internal data class PolarSleepResultDto(
    @SerialName("hypnogram") val hypnogram: PolarHypnogramDto? = null
)

@Serializable
internal data class PolarHypnogramDto(
    @SerialName("sleepStart") val sleepStart: String? = null,
    @SerialName("sleepEnd") val sleepEnd: String? = null
)

@Serializable
internal data class PolarSleepEvaluationDto(
    @SerialName("phaseDurations") val phaseDurations: PolarSleepPhaseDurationsDto? = null,
    @SerialName("sleepSpan") val sleepSpan: String? = null,
    @SerialName("asleepDuration") val asleepDuration: String? = null,
    @SerialName("interruptions") val interruptions: PolarSleepInterruptionsDto? = null,
    @SerialName("analysis") val analysis: PolarSleepAnalysisDto? = null
)

@Serializable
internal data class PolarSleepPhaseDurationsDto(
    @SerialName("wake") val wake: String? = null,
    @SerialName("rem") val rem: String? = null,
    @SerialName("light") val light: String? = null,
    @SerialName("deep") val deep: String? = null
)

@Serializable
internal data class PolarSleepInterruptionsDto(
    @SerialName("totalCount") val totalCount: Int? = null,
    @SerialName("longCount") val longCount: Int? = null
)

@Serializable
internal data class PolarSleepAnalysisDto(
    @SerialName("efficiencyPercent") val efficiencyPercent: Double? = null,
    @SerialName("continuityIndex") val continuityIndex: Double? = null
)

@Serializable
internal data class PolarSleepScoreDto(
    @SerialName("sleepScore") val sleepScore: Double? = null,
    @SerialName("continuityScore") val continuityScore: Double? = null,
    @SerialName("efficiencyScore") val efficiencyScore: Double? = null,
    @SerialName("remScore") val remScore: Double? = null,
    @SerialName("n3Score") val n3Score: Double? = null,
    @SerialName("scoreRate") val scoreRate: Int? = null
)

// --- Training Sessions ---

@Serializable
internal data class PolarTrainingListResponse(
    @SerialName("trainingSessions") val trainingSessions: List<PolarTrainingSessionDto> = emptyList()
)

@Serializable
internal data class PolarTrainingSessionDto(
    @SerialName("identifier") val identifier: PolarIdentifierDto? = null,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("durationMillis") val durationMillis: Long? = null,
    @SerialName("sport") val sport: PolarSportDto? = null,
    @SerialName("calories") val calories: Int? = null,
    @SerialName("distanceMeters") val distanceMeters: Double? = null,
    @SerialName("hrAvg") val hrAvg: Int? = null,
    @SerialName("hrMax") val hrMax: Int? = null,
    @SerialName("trainingBenefit") val trainingBenefit: String? = null
)

@Serializable
internal data class PolarIdentifierDto(
    @SerialName("id") val id: String? = null
)

@Serializable
internal data class PolarSportDto(
    @SerialName("id") val id: String? = null
)

// --- Nightly Recharge ---

@Serializable
internal data class PolarNightlyRechargeListResponse(
    @SerialName("nightlyRechargeResults") val nightlyRechargeResults: List<PolarNightlyRechargeDto> = emptyList()
)

@Serializable
internal data class PolarNightlyRechargeDto(
    @SerialName("sleepResultDate") val sleepResultDate: String? = null,
    @SerialName("ansStatus") val ansStatus: Double? = null,
    @SerialName("ansRate") val ansRate: Int? = null,
    @SerialName("recoveryIndicator") val recoveryIndicator: Int? = null,
    @SerialName("recoveryIndicatorSubLevel") val recoveryIndicatorSubLevel: Int? = null,
    @SerialName("meanNightlyRecoveryRmssd") val meanNightlyRecoveryRmssd: Int? = null,
    @SerialName("meanNightlyRecoveryRri") val meanNightlyRecoveryRri: Int? = null,
    @SerialName("meanBaselineRmssd") val meanBaselineRmssd: Int? = null,
    @SerialName("sdBaselineRmssd") val sdBaselineRmssd: Int? = null,
    @SerialName("meanBaselineRri") val meanBaselineRri: Int? = null,
    @SerialName("sdBaselineRri") val sdBaselineRri: Int? = null
)
