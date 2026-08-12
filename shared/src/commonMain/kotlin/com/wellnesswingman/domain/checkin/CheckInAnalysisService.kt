package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.repository.CheckInAnalysisRepository
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/**
 * Reads structure out of a check-in's free text: food that was never photographed, and the
 * specific things that helped or hurt.
 *
 * ### Why this owns a scope
 *
 * Extraction runs after the user saves, and saving is immediately followed by the screen closing
 * — that is the normal path, not an edge case. A coroutine launched in the check-in screen's
 * `screenModelScope` would therefore be cancelled a few hundred milliseconds in, essentially
 * every time. The work has to outlive the screen, so this service holds its own application-level
 * scope and callers merely poke it.
 *
 * [SupervisorJob] specifically: one failed extraction must not cancel the scope and take every
 * later extraction down with it.
 */
class CheckInAnalysisService(
    private val checkInAnalysisRepository: CheckInAnalysisRepository,
    private val dailyCheckInRepository: DailyCheckInRepository,
    private val trackedEntryRepository: TrackedEntryRepository,
    private val llmClientFactory: LlmClientFactory,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    /**
     * Overridable so tests can supply a scope they control, rather than racing a background one.
     */
    scope: CoroutineScope? = null
) {

    private val backgroundScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val _analysisCompletions = MutableSharedFlow<LocalDate>(
        replay = 0,
        extraBufferCapacity = 16
    )

    /**
     * Emits the date of each finished extraction, successful or not.
     *
     * Extraction runs after the screen that triggered it has closed, so a day screen has no other
     * way to learn the result arrived — without this it would sit on "Reading your answer…" until
     * the user happened to navigate away and back. Failures are emitted too: the screen needs to
     * stop saying "reading" either way.
     *
     * Mirrors [com.wellnesswingman.domain.events.StatusChangeNotifier] rather than inventing a
     * second event mechanism.
     */
    val analysisCompletions: SharedFlow<LocalDate> = _analysisCompletions.asSharedFlow()

    /**
     * Marks the check-in as pending and extracts in the background.
     *
     * Returns immediately. The pending row is written first and synchronously enough to be
     * observed, so a screen can show "reading your answer" rather than an empty space that looks
     * like nothing was found.
     *
     * @return the [Job] doing the work, so tests and callers that care can await it.
     */
    fun analyzeInBackground(checkIn: DailyCheckIn): Job = backgroundScope.launch {
        analyze(checkIn)
    }

    /**
     * Extracts facets for a check-in and stores the result.
     *
     * Never throws: a failed extraction is recorded as [CheckInAnalysisStatus.FAILED] with its
     * message, because the caller is usually a background job with nobody to catch it, and a
     * stored failure is what makes an informed retry possible.
     */
    suspend fun analyze(checkIn: DailyCheckIn): CheckInAnalysis {
        markPending(checkIn)

        val analysis = runAnalysis(checkIn)

        // tryEmit rather than emit: a finished extraction must not block on a screen that is not
        // listening, and a dropped notification only costs a stale card until the next refresh.
        _analysisCompletions.tryEmit(checkIn.checkInDate)

        return analysis
    }

    private suspend fun runAnalysis(checkIn: DailyCheckIn): CheckInAnalysis {
        return try {
            val trackedEntryLines = describeTrackedEntries(checkIn.checkInDate)
            val prompt = CheckInFacetPrompt.build(checkIn, trackedEntryLines)

            val llmClient = llmClientFactory.createForCurrentProvider()
            val result = llmClient.generateCompletion(
                prompt = prompt,
                jsonSchema = CheckInFacetPrompt.RESPONSE_SCHEMA
            )

            val facets = parseFacets(result.content)
                ?: error("The response could not be read as check-in facets")

            store(
                checkIn = checkIn,
                status = CheckInAnalysisStatus.COMPLETED,
                providerId = llmClient.providerId,
                model = result.diagnostics.model,
                facets = facets,
                errorMessage = null
            )
        } catch (e: Exception) {
            Napier.e(
                "Failed to extract facets from the ${checkIn.slot.toStorageString()} " +
                    "check-in for ${checkInDateLabel(checkIn)}",
                e
            )
            store(
                checkIn = checkIn,
                status = CheckInAnalysisStatus.FAILED,
                providerId = "",
                model = "",
                facets = null,
                errorMessage = e.message ?: "Extraction failed"
            )
        }
    }

    /**
     * Re-runs extraction for a check-in the user asks about again.
     *
     * Reads the check-in fresh rather than trusting a passed-in copy, so a retry after editing
     * the answer analyses what is actually stored.
     */
    suspend fun retry(date: LocalDate, slot: CheckInSlot): CheckInAnalysis? {
        val checkIn = try {
            dailyCheckInRepository.getCheckIn(date, slot)
        } catch (e: Exception) {
            Napier.w("Failed to reload check-in for retry: ${e.message}")
            null
        } ?: return null

        return analyze(checkIn)
    }

    /**
     * The extraction for a single check-in, whatever state it is in.
     *
     * Returns the whole record rather than just its facets so a caller can tell "still reading"
     * from "read it and found nothing" from "failed" — three states that look identical if only
     * the facets are handed over.
     */
    suspend fun analysisFor(date: LocalDate, slot: CheckInSlot): CheckInAnalysis? = try {
        checkInAnalysisRepository.getAnalysis(date, slot)
    } catch (e: Exception) {
        Napier.w("Failed to load the check-in analysis for $date/$slot: ${e.message}")
        null
    }

    /**
     * Facets for a day, ready to be totalled or shown. Only completed extractions are returned:
     * a pending row has no food in it *yet*, which is not the same as a day with no extra food.
     */
    suspend fun completedFacetsForDate(date: LocalDate): List<CheckInFacets> = try {
        checkInAnalysisRepository.getAnalysesForDate(date).mapNotNull { it.completedFacets }
    } catch (e: Exception) {
        Napier.w("Failed to load check-in facets for $date: ${e.message}")
        emptyList()
    }

    private suspend fun markPending(checkIn: DailyCheckIn) {
        try {
            store(
                checkIn = checkIn,
                status = CheckInAnalysisStatus.PENDING,
                providerId = "",
                model = "",
                facets = null,
                errorMessage = null
            )
        } catch (e: Exception) {
            // Not fatal on its own — the extraction can still run and write its result.
            Napier.w("Failed to mark check-in analysis pending: ${e.message}")
        }
    }

    private suspend fun store(
        checkIn: DailyCheckIn,
        status: CheckInAnalysisStatus,
        providerId: String,
        model: String,
        facets: CheckInFacets?,
        errorMessage: String?
    ): CheckInAnalysis {
        val analysis = CheckInAnalysis(
            checkInDate = checkIn.checkInDate,
            slot = checkIn.slot,
            status = status,
            providerId = providerId,
            model = model,
            analyzedAt = clock.now(),
            facets = facets,
            errorMessage = errorMessage
        )

        checkInAnalysisRepository.saveAnalysis(analysis)
        return analysis
    }

    /**
     * One line per completed entry already logged that day, so the model can spot food it is
     * being told about a second time.
     *
     * Only completed entries are listed: a still-processing photo has no analysis to compare
     * against, so claiming it as a duplicate would be a guess.
     */
    private suspend fun describeTrackedEntries(date: LocalDate): List<String> = try {
        val start = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val end = date.atTime(23, 59, 59).toInstant(timeZone).toEpochMilliseconds()

        trackedEntryRepository.getEntriesForDay(start, end)
            .filter {
                it.entryType != EntryType.DAILY_SUMMARY &&
                    it.processingStatus == ProcessingStatus.COMPLETED
            }
            .map { it.describe() }
    } catch (e: Exception) {
        Napier.w("Failed to load tracked entries for duplicate detection: ${e.message}")
        emptyList()
    }

    private fun TrackedEntry.describe(): String {
        val localTime = capturedAt.toLocalDateTime(timeZone).time
        val hour = localTime.hour.toString().padStart(2, '0')
        val minute = localTime.minute.toString().padStart(2, '0')
        val note = userNotes?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()

        return "$hour:$minute ${entryType.name.lowercase()}$note"
    }

    /**
     * Parses the model's reply, tolerating the prose and code fences models add around JSON.
     */
    private fun parseFacets(content: String): CheckInFacets? {
        val payload = extractJsonObject(content) ?: return null

        return try {
            json.decodeFromString(CheckInFacets.serializer(), payload)
        } catch (e: Exception) {
            Napier.w("Failed to parse check-in facets: ${e.message}")
            null
        }
    }

    /**
     * Pulls the outermost JSON object out of a reply.
     *
     * Brace-matching rather than first-to-last-brace, so nested objects survive, and quote-aware
     * so a brace inside a food name does not end the object early.
     */
    private fun extractJsonObject(content: String): String? {
        val start = content.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until content.length) {
            val char = content[index]

            if (escaped) {
                escaped = false
                continue
            }

            when {
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return content.substring(start, index + 1)
                }
            }
        }

        return null
    }

    private fun checkInDateLabel(checkIn: DailyCheckIn): String = checkIn.checkInDate.toString()
}
