package com.wellnesswingman.ui.screens.googleexport

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.domain.common.DateRange
import com.wellnesswingman.domain.googleexport.GoogleAuthResult
import com.wellnesswingman.domain.googleexport.GoogleAuthService
import com.wellnesswingman.domain.googleexport.GoogleDocsExportService
import com.wellnesswingman.domain.googleexport.GoogleExportError
import com.wellnesswingman.domain.report.HealthReportBuilder
import com.wellnesswingman.domain.report.HealthReportData
import com.wellnesswingman.domain.report.HealthReportGatherer
import com.wellnesswingman.domain.report.HealthReportPreset
import com.wellnesswingman.domain.report.HealthReportPreview
import com.wellnesswingman.domain.report.HealthReportRequest
import com.wellnesswingman.domain.report.HealthReportSection
import com.wellnesswingman.domain.report.toPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class GoogleDocsExportViewModel(
    private val gatherer: HealthReportGatherer,
    private val builder: HealthReportBuilder,
    private val authService: GoogleAuthService,
    private val exportService: GoogleDocsExportService
) : ScreenModel {
    private val zone = TimeZone.currentSystemDefault()
    private val today = Clock.System.todayIn(zone)
    private val _uiState = MutableStateFlow(GoogleDocsExportUiState(
        startDate = today.toString(), endDate = today.toString(), timeZoneId = zone.id,
        preset = HealthReportPreset.FOOD_DIARY, selectedSections = HealthReportPreset.FOOD_DIARY.defaultSections(),
        isAuthorizationAvailable = authService.isAvailable
    ))
    val uiState: StateFlow<GoogleDocsExportUiState> = _uiState.asStateFlow()

    /** The in-flight preview gather, if any. */
    private var gatherJob: Job? = null

    /**
     * Bumped on every parameter edit. A gather records the revision it started
     * under and publishes only if it still matches, so a result gathered for
     * old parameters can never become the exportable snapshot.
     */
    private var parametersRevision = 0

    fun updateStartDate(value: String) = changeParameters { it.copy(startDate = value) }
    fun updateEndDate(value: String) = changeParameters { it.copy(endDate = value) }
    fun selectPreset(preset: HealthReportPreset) = changeParameters {
        it.copy(preset = preset, selectedSections = preset.defaultSections())
    }
    fun toggleSection(section: HealthReportSection) = changeParameters {
        val next = it.selectedSections.toMutableSet().apply { if (!add(section)) remove(section) }
        it.copy(preset = HealthReportPreset.CUSTOM, selectedSections = next)
    }

    /**
     * Editing any report parameter invalidates everything derived from the old
     * ones: an in-flight gather is cancelled and its result discarded, and the
     * previous export's link is cleared so it can't be mistaken for output of
     * the new parameters.
     */
    private fun changeParameters(edit: (GoogleDocsExportUiState) -> GoogleDocsExportUiState) {
        parametersRevision++
        gatherJob?.cancel()
        gatherJob = null
        _uiState.update {
            edit(it).copy(
                error = null,
                preview = null,
                snapshot = null,
                resultUrl = null,
                isGathering = false
            )
        }
    }
    fun dismissConfirmation() = _uiState.update { it.copy(showConfirmation = false) }
    fun clearResult() = _uiState.update { it.copy(resultUrl = null, error = null) }

    fun gatherPreview() {
        val request = requestOrError() ?: return
        val revision = parametersRevision
        gatherJob?.cancel()
        _uiState.update { it.copy(isGathering = true, error = null, resultUrl = null) }
        gatherJob = screenModelScope.launch {
            // Not runCatching: it would swallow the CancellationException from
            // a parameter edit and report it as a gather failure.
            val outcome = try {
                Result.success(gatherer.gather(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            // The parameters moved on while this gather ran; its data
            // describes a selection the user no longer wants to share.
            if (revision != parametersRevision) return@launch

            outcome
                .onSuccess { data ->
                    if (data.isEmpty) _uiState.update { it.copy(isGathering = false, snapshot = data, preview = data.toPreview(), error = "No selected data was found. Google authorization was not started.") }
                    else _uiState.update { it.copy(isGathering = false, snapshot = data, preview = data.toPreview()) }
                }
                .onFailure { _uiState.update { state -> state.copy(isGathering = false, error = "Unable to prepare the local preview.") } }
        }
    }

    fun requestConfirmation() = _uiState.update {
        if (it.snapshot == null || it.snapshot.isEmpty) it.copy(error = "Prepare a non-empty local preview first.") else it.copy(showConfirmation = true)
    }

    fun createGoogleDocument() {
        _uiState.update { it.copy(showConfirmation = false) }
        authorizeAndExport()
    }

    fun resumeAfterConsent() = authorizeAndExport()

    private fun authorizeAndExport() = screenModelScope.launch {
        val snapshot = _uiState.value.snapshot ?: return@launch
        _uiState.update { it.copy(isExporting = true, awaitingConsent = false, error = null) }
        when (val auth = authService.requestAccess()) {
            is GoogleAuthResult.Granted -> try {
                // build() is pure, but guard it like every other step so an
                // unexpected failure becomes a friendly error, not a crash.
                val document = runCatching { builder.build(snapshot, Clock.System.now()) }
                    .getOrElse {
                        _uiState.update { it.copy(isExporting = false, error = "Unable to build the report document.") }
                        return@launch
                    }
                exportService.createAndPopulate(auth.accessToken, "WellnessWingman health report", document)
                    .onSuccess { result -> _uiState.update { it.copy(isExporting = false, resultUrl = result.url) } }
                    .onFailure { failure ->
                        val message = when {
                            failure is GoogleExportError.ApiFailure &&
                                failure.partialDocumentId != null && !failure.cleanupDeleted ->
                                "Google Docs export failed and the incomplete document could not be removed. Check Google Drive before retrying."
                            failure is GoogleExportError -> failure.toUserMessage()
                            else -> "Google Docs export failed. You can retry safely."
                        }
                        _uiState.update { it.copy(isExporting = false, error = message) }
                    }
            } finally { authService.clearAccessToken() }
            GoogleAuthResult.ConsentRequired -> _uiState.update { it.copy(isExporting = false, awaitingConsent = true) }
            GoogleAuthResult.Cancelled -> _uiState.update { it.copy(isExporting = false, error = "Google authorization was cancelled.") }
            GoogleAuthResult.Denied -> _uiState.update { it.copy(isExporting = false, error = "Google authorization was denied.") }
            GoogleAuthResult.Failed -> _uiState.update { it.copy(isExporting = false, error = "Google authorization failed. You can retry.") }
            GoogleAuthResult.Unavailable -> _uiState.update { it.copy(isExporting = false, error = "Google Docs export is available on Android only.") }
        }
    }

    private fun requestOrError(): HealthReportRequest? {
        val selected = _uiState.value.selectedSections
        if (selected.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one report section.") }
            return null
        }
        return try {
            HealthReportRequest(DateRange.of(LocalDate.parse(_uiState.value.startDate), LocalDate.parse(_uiState.value.endDate)), zone, _uiState.value.preset, selected)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Use ISO dates (YYYY-MM-DD), with an end date on or after the start date.") }
            null
        }
    }
}

data class GoogleDocsExportUiState(
    val startDate: String = "", val endDate: String = "", val timeZoneId: String = "",
    val preset: HealthReportPreset = HealthReportPreset.FOOD_DIARY,
    val selectedSections: Set<HealthReportSection> = emptySet(),
    val snapshot: HealthReportData? = null, val preview: HealthReportPreview? = null,
    val isGathering: Boolean = false, val isExporting: Boolean = false, val awaitingConsent: Boolean = false,
    val showConfirmation: Boolean = false, val resultUrl: String? = null, val error: String? = null,
    val isAuthorizationAvailable: Boolean = false
)
