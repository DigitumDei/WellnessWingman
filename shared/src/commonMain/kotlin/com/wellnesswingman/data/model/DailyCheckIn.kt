package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which of the two daily check-ins an answer belongs to.
 */
@Serializable
enum class CheckInSlot {
    @SerialName("Morning")
    MORNING,

    @SerialName("Evening")
    EVENING;

    companion object {
        fun fromString(value: String?): CheckInSlot? {
            if (value.isNullOrBlank()) return null

            return when (value.trim().lowercase()) {
                "morning" -> MORNING
                "evening" -> EVENING
                else -> null
            }
        }
    }

    fun toStorageString(): String {
        return when (this) {
            MORNING -> "Morning"
            EVENING -> "Evening"
        }
    }
}

/**
 * How the user supplied a check-in answer. Recorded so transcription quality can later be
 * compared against typed text.
 */
@Serializable
enum class CheckInInputSource {
    @SerialName("Typed")
    TYPED,

    @SerialName("Voice")
    VOICE;

    companion object {
        fun fromString(value: String?): CheckInInputSource {
            if (value.isNullOrBlank()) return TYPED

            return when (value.trim().lowercase()) {
                "voice" -> VOICE
                else -> TYPED
            }
        }
    }

    fun toStorageString(): String {
        return when (this) {
            TYPED -> "Typed"
            VOICE -> "Voice"
        }
    }
}

/**
 * A subjective day-level check-in.
 *
 * Check-ins capture how the user felt, which no photo or wearable can supply. They are stored
 * outside the [TrackedEntry] stream on purpose: a Sleep entry causes DailySummaryService to
 * suppress Polar's measured sleep data, so recording "slept badly" as an entry would evict the
 * actual measurements it was meant to sit alongside.
 */
data class DailyCheckIn(
    val checkInId: Long = 0,
    val externalId: String? = null,

    /** The day this check-in describes. */
    val checkInDate: LocalDate,

    val slot: CheckInSlot,

    /**
     * When the answer was actually given. May fall outside [checkInDate] — an evening check-in
     * answered after midnight still belongs to the day it is about.
     */
    val capturedAt: Instant,

    val responseText: String,

    val inputSource: CheckInInputSource = CheckInInputSource.TYPED,

    /** Set once the user opens a health chat about this check-in. */
    val conversationExternalId: String? = null
)
