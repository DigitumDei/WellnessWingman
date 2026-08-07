package com.wellnesswingman.ui.screens.checkin

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.domain.llm.LlmClientFactory
import com.wellnesswingman.platform.AudioRecordingService
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.ui.common.CommentsState
import com.wellnesswingman.ui.common.UserCommentsManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class CheckInUiState(
    val slot: CheckInSlot = CheckInSlot.MORNING,
    val date: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** True once an answer for this slot exists, whether saved now or earlier today. */
    val hasSavedAnswer: Boolean = false,
    val saveError: String? = null
) {
    /** The prompt shown above the input, and the reason this feature exists. */
    val questions: List<String>
        get() = when (slot) {
            CheckInSlot.MORNING -> listOf("How did you sleep?", "How do you feel?")
            CheckInSlot.EVENING -> listOf(
                "How did the day feel?",
                "Anything other than what you logged?"
            )
        }

    val title: String
        get() = when (slot) {
            CheckInSlot.MORNING -> "Morning check-in"
            CheckInSlot.EVENING -> "Evening check-in"
        }
}

/**
 * Captures a subjective check-in.
 *
 * Text and voice share the existing [UserCommentsManager], so transcription behaves exactly as
 * it does for entry and summary notes: speech lands in the text field where it can be corrected
 * before saving, rather than being committed blind.
 */
class CheckInViewModel(
    private val slot: CheckInSlot,
    private val dailyCheckInRepository: DailyCheckInRepository,
    private val audioRecordingService: AudioRecordingService,
    private val llmClientFactory: LlmClientFactory,
    private val fileSystem: FileSystem
) : ScreenModel {

    private val _uiState = MutableStateFlow(CheckInUiState(slot = slot))
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val commentsManager = UserCommentsManager(
        audioRecordingService = audioRecordingService,
        llmClientFactory = llmClientFactory,
        fileSystem = fileSystem,
        scope = screenModelScope,
        audioFilePrefix = "checkin"
    )

    val commentsState: StateFlow<CommentsState> = commentsManager.commentsState

    /**
     * Tracks whether the current text arrived by voice, so the stored row records how the user
     * actually answered. Typing after a transcription counts as typed.
     */
    private var lastTranscribedText: String? = null

    init {
        load()
    }

    private fun load() {
        screenModelScope.launch {
            val date = today()
            try {
                val existing = dailyCheckInRepository.getCheckIn(date, slot)
                commentsManager.loadComments(existing?.responseText)
                _uiState.value = _uiState.value.copy(
                    date = date,
                    isLoading = false,
                    hasSavedAnswer = existing != null
                )
            } catch (e: Exception) {
                Napier.e("Failed to load ${slot.toStorageString()} check-in", e)
                commentsManager.loadComments(null)
                _uiState.value = _uiState.value.copy(date = date, isLoading = false)
            }
        }
    }

    fun updateText(text: String) {
        commentsManager.updateText(text)
    }

    fun toggleRecording() {
        val textBeforeRecording = commentsState.value.text
        commentsManager.toggleRecording()

        // The manager appends the transcript to the existing text, so anything that grows the
        // field while recording came from speech.
        screenModelScope.launch {
            lastTranscribedText = textBeforeRecording
        }
    }

    suspend fun checkMicPermission(): Boolean = commentsManager.checkMicPermission()

    /**
     * Saves the answer, replacing any earlier answer for this slot today.
     *
     * @return true when the answer was stored, so the screen can offer to open a chat about it.
     */
    fun save(onSaved: (DailyCheckIn) -> Unit = {}) {
        val text = commentsState.value.text.trim()
        if (text.isEmpty()) return

        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)

            val checkIn = DailyCheckIn(
                checkInDate = _uiState.value.date,
                slot = slot,
                capturedAt = Clock.System.now(),
                responseText = text,
                inputSource = inputSourceFor(text)
            )

            try {
                dailyCheckInRepository.saveCheckIn(checkIn)
                commentsManager.markSaved()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    hasSavedAnswer = true
                )
                onSaved(checkIn)
            } catch (e: Exception) {
                Napier.e("Failed to save ${slot.toStorageString()} check-in", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveError = e.message ?: "Could not save your check-in"
                )
            }
        }
    }

    /**
     * A conversation id derived from the day and slot, so re-opening a check-in returns to the
     * same thread rather than starting a new one each time.
     */
    fun conversationExternalId(): String =
        "checkin-${_uiState.value.date}-${slot.toStorageString().lowercase()}"

    private fun inputSourceFor(text: String): CheckInInputSource {
        val before = lastTranscribedText
        val grewDuringRecording = before != null && text.length > before.length

        return if (grewDuringRecording) CheckInInputSource.VOICE else CheckInInputSource.TYPED
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
