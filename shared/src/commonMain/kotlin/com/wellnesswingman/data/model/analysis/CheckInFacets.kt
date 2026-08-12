package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What an LLM pulled out of a check-in's free text.
 *
 * Check-ins exist to record how a day *felt*, and that stays true: nothing here scores the day
 * or the user. What extraction adds is the concrete detail buried in the sentence — food that was
 * eaten but never photographed, and the specific things that helped or hurt — so the rest of the
 * app can act on it instead of storing a paragraph it cannot read.
 *
 * The user's own words remain canonical in `DailyCheckIn.responseText`. Everything here is
 * derived, and can be deleted and regenerated without loss.
 */
@Serializable
data class CheckInFacets(
    @SerialName("schemaVersion")
    val schemaVersion: String = "1.0",

    /**
     * Food or drink the user mentioned that was not otherwise logged.
     */
    @SerialName("mentionedFood")
    val mentionedFood: List<MentionedFood> = emptyList(),

    /**
     * Things that helped or hurt, each attributed to the body or to the world.
     */
    @SerialName("factors")
    val factors: List<CheckInFactor> = emptyList(),

    /**
     * Overall confidence in the extraction (0.0 to 1.0).
     */
    @SerialName("confidence")
    @Serializable(with = LenientConfidenceSerializer::class)
    val confidence: Double = 0.0,

    /**
     * Anything the model wants to flag about its own output.
     */
    @SerialName("warnings")
    val warnings: List<String> = emptyList()
) {
    /**
     * Food that should count toward the day's nutrition.
     *
     * Excludes anything the model matched to an already-tracked entry. Day totals merge tracked
     * and mentioned food, so without this filter a meal both photographed and talked about in the
     * evening check-in would be counted twice.
     */
    val countableFood: List<MentionedFood>
        get() = mentionedFood.filter { !it.possiblyAlreadyLogged }

    val goodFactors: List<CheckInFactor>
        get() = factors.filter { it.valence == FactorValence.GOOD }

    val badFactors: List<CheckInFactor>
        get() = factors.filter { it.valence == FactorValence.BAD }

    val isEmpty: Boolean
        get() = mentionedFood.isEmpty() && factors.isEmpty()
}

/**
 * Food or drink named in a check-in but never photographed.
 *
 * Reuses [NutritionEstimate] rather than defining a parallel shape, so `DailyTotalsCalculator`
 * can add these to the day without a converter and the numbers mean exactly what they mean
 * everywhere else in the app.
 */
@Serializable
data class MentionedFood(
    @SerialName("name")
    val name: String = "",

    /**
     * Portion in the user's own terms ("a packet", "two beers", "a slice").
     */
    @SerialName("portionSize")
    val portionSize: String? = null,

    @SerialName("nutrition")
    val nutrition: NutritionEstimate? = null,

    /**
     * Confidence in this item and its estimate (0.0 to 1.0). Text estimates are inherently
     * vaguer than a photo, and the UI says so rather than presenting them as equally solid.
     */
    @SerialName("confidence")
    @Serializable(with = LenientConfidenceSerializer::class)
    val confidence: Double = 0.0,

    /**
     * True when this looks like something already captured as a tracked entry.
     *
     * The extraction prompt is given the day's entries precisely so it can make this call. Such
     * items are kept and shown — they are useful context — but excluded from the day's totals.
     */
    @SerialName("possiblyAlreadyLogged")
    val possiblyAlreadyLogged: Boolean = false,

    /**
     * Which tracked entry it seems to duplicate, so the judgement can be checked rather than
     * taken on trust.
     */
    @SerialName("alreadyLoggedReason")
    val alreadyLoggedReason: String? = null
)

/**
 * Whether a factor helped or hurt.
 *
 * Two values on purpose. A wider scale would be a rating of the day, which is the thing
 * check-ins deliberately avoid; "this specific thing was good" is an observation, not a score.
 */
@Serializable
enum class FactorValence {
    @SerialName("Good")
    GOOD,

    @SerialName("Bad")
    BAD;

    companion object {
        fun fromString(value: String?): FactorValence? = when (value?.trim()?.lowercase()) {
            "good", "positive" -> GOOD
            "bad", "negative" -> BAD
            else -> null
        }
    }

    fun toStorageString(): String = when (this) {
        GOOD -> "Good"
        BAD -> "Bad"
    }
}

/**
 * Where a factor came from.
 *
 * This is the split that makes the data worth having. "Woke at 1am because the cat brought in a
 * rat" is external — noise that should not colour any trend about the user's health. "Couldn't
 * sleep because my stomach was sore" is internal, and worth correlating against what they ate.
 * Storing them apart is what makes that distinction available later.
 */
@Serializable
enum class FactorOrigin {
    /** Arising from the user's own body or mind. */
    @SerialName("Internal")
    INTERNAL,

    /** Arising from circumstance: other people, noise, weather, pets, work. */
    @SerialName("External")
    EXTERNAL;

    companion object {
        fun fromString(value: String?): FactorOrigin? = when (value?.trim()?.lowercase()) {
            "internal" -> INTERNAL
            "external" -> EXTERNAL
            else -> null
        }
    }

    fun toStorageString(): String = when (this) {
        INTERNAL -> "Internal"
        EXTERNAL -> "External"
    }
}

/**
 * One thing that helped or hurt, and where it came from.
 */
@Serializable
data class CheckInFactor(
    /**
     * The factor in plain words ("woke at 1am when the cat brought in a rat").
     */
    @SerialName("description")
    val description: String = "",

    @SerialName("valence")
    val valence: FactorValence = FactorValence.BAD,

    @SerialName("origin")
    val origin: FactorOrigin = FactorOrigin.EXTERNAL,

    /**
     * The user's own words that produced this factor.
     *
     * Kept so a reader can always see what was actually said versus what was inferred from it.
     */
    @SerialName("quote")
    val quote: String? = null,

    /**
     * Loose area this touches: "sleep", "energy", "mood", "digestion", "pain", "stress",
     * "social", "environment", "other". Free text rather than an enum because the useful
     * categories are not yet known; tighten it once real answers show what recurs.
     */
    @SerialName("domain")
    val domain: String? = null,

    @SerialName("confidence")
    @Serializable(with = LenientConfidenceSerializer::class)
    val confidence: Double = 0.0
)

/**
 * How far extraction has got for a given check-in.
 */
@Serializable
enum class CheckInAnalysisStatus {
    /** Queued or in flight. Extraction runs after saving, so this is the normal first state. */
    @SerialName("Pending")
    PENDING,

    @SerialName("Completed")
    COMPLETED,

    @SerialName("Failed")
    FAILED;

    companion object {
        fun fromString(value: String?): CheckInAnalysisStatus = when (value?.trim()?.lowercase()) {
            "completed" -> COMPLETED
            "failed" -> FAILED
            else -> PENDING
        }
    }

    fun toStorageString(): String = when (this) {
        PENDING -> "Pending"
        COMPLETED -> "Completed"
        FAILED -> "Failed"
    }
}
