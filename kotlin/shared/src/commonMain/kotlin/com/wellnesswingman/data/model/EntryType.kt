package com.wellnesswingman.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type of health tracking entry.
 */
@Serializable
enum class EntryType {
    @SerialName("Unknown")
    UNKNOWN,

    @SerialName("Meal")
    MEAL,

    @SerialName("Exercise")
    EXERCISE,

    @SerialName("Sleep")
    SLEEP,

    @SerialName("Other")
    OTHER,

    @SerialName("DailySummary")
    DAILY_SUMMARY;

    companion object {
        fun fromString(value: String?): EntryType {
            if (value.isNullOrBlank()) return UNKNOWN

            return when (value.trim().lowercase()) {
                "meal" -> MEAL
                "exercise" -> EXERCISE
                "sleep" -> SLEEP
                "other" -> OTHER
                "dailysummary" -> DAILY_SUMMARY
                "unknown" -> UNKNOWN
                else -> UNKNOWN
            }
        }
    }

    fun toStorageString(): String {
        return when (this) {
            UNKNOWN -> "Unknown"
            MEAL -> "Meal"
            EXERCISE -> "Exercise"
            SLEEP -> "Sleep"
            OTHER -> "Other"
            DAILY_SUMMARY -> "DailySummary"
        }
    }
}
