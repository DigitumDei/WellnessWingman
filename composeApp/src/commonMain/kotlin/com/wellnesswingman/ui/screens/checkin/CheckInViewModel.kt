package com.wellnesswingman.ui.screens.checkin

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.CheckInAnalysis
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.data.model.analysis.CheckInAnalysisStatus
import com.wellnesswingman.data.model.analysis.CheckInFacets
import com.wellnesswingman.data.repository.DailyCheckInRepository
import com.wellnesswingman.domain.checkin.CheckInAnalysisService
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
    val saveError: String? = null,
    /** True while the opening chat turn is in flight, before the thread opens. */
    val isStartingConversation: Boolean = false,
    val conversationError: String? = null,
    /** Set once a chat thread exists for this check-in, so re-opening returns to it. */
    val conversationExternalId: String? = null,
    /** True when answering for a past day, which the screen says out loud. */
    val isBackfill: Boolean = false,
    /**
     * What the app read out of the answer, once extraction has been attempted.
     *
     * Shown read-only: it is the app's reading of what the user wrote, not a second thing for
     * them to maintain. Editing it would imply the extraction is the record, when the words are.
     */
    val analysis: CheckInAnalysis? = null
) {
    val facets: CheckInFacets? get() = analysis?.completedFacets

    val isAnalysisPending: Boolean get() = analysis?.isPending == true

    val hasAnalysisFailed: Boolean get() = analysis?.hasFailed == true

    /** True once extraction finished and found nothing worth listing. */
    val analysisFoundNothing: Boolean
        get() = analysis?.status == CheckInAnalysisStatus.COMPLETED && facets?.isEmpty != false

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
    /**
     * The day being checked in about. Null means today.
     *
     * Explicit rather than assumed, because a check-in answered for yesterday must be stored
     * against yesterday — otherwise it lands on the wrong day and feeds the wrong summary.
     */
    private val checkInDate: LocalDate? = null,
    private val dailyCheckInRepository: DailyCheckInRepository,
    private val audioRecordingService: AudioRecordingService,
    private val llmClientFactory: LlmClientFactory,
    private val fileSystem: FileSystem,
    private val conversationStarter: CheckInConversationStarter,
    /**
     * Optional so existing tests can construct this view model without stubbing extraction.
     * Absent means the answer is still saved; only the derived facets are skipped.
     */
    private val checkInAnalysisService: CheckInAnalysisService? = null
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
        observeAnalysis()
    }

    /**
     * Keeps the extraction summary current while the screen is open.
     *
     * Extraction is started by saving and runs on the service's own scope, so the result arrives
     * after this screen has already rendered. Without this the summary would stay on "reading"
     * until the screen was reopened.
     */
    private fun observeAnalysis() {
        val service = checkInAnalysisService ?: return

        screenModelScope.launch {
            service.analysisCompletions.collect { date ->
                if (date == _uiState.value.date) refreshAnalysis()
            }
        }
    }

    private suspend fun refreshAnalysis() {
        val service = checkInAnalysisService ?: return
        _uiState.value = _uiState.value.copy(
            analysis = service.analysisFor(_uiState.value.date, slot)
        )
    }

    private fun load() {
        screenModelScope.launch {
            val date = checkInDate ?: today()
            try {
                val existing = dailyCheckInRepository.getCheckIn(date, slot)
                commentsManager.loadComments(existing?.responseText)
                _uiState.value = _uiState.value.copy(
                    date = date,
                    isLoading = false,
                    hasSavedAnswer = existing != null,
                    // Re-opening a check-in returns to its thread rather than starting another.
                    conversationExternalId = existing?.conversationExternalId,
                    isBackfill = date != today(),
                    analysis = checkInAnalysisService?.analysisFor(date, slot)
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
            persist(text)?.let(onSaved)
        }
    }

    /**
     * Writes the answer, returning it on success and null on failure.
     *
     * Separate from [save] so starting a conversation can persist first and only proceed if
     * that worked: the conversation link is stored with an UPDATE against an existing row, so
     * chatting about an unsaved answer would both lose the answer and leave the thread
     * unlinked.
     */
    private suspend fun persist(text: String): DailyCheckIn? {
        _uiState.value = _uiState.value.copy(isSaving = true, saveError = null)

        val checkIn = DailyCheckIn(
            checkInDate = _uiState.value.date,
            slot = slot,
            capturedAt = Clock.System.now(),
            responseText = text,
            inputSource = inputSourceFor(text),
            // Saving is an upsert, so this must be carried forward or re-answering would
            // orphan the thread already started about this check-in.
            conversationExternalId = _uiState.value.conversationExternalId
        )

        return try {
            dailyCheckInRepository.saveCheckIn(checkIn)
            commentsManager.markSaved()
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                hasSavedAnswer = true
            )

            // Extraction runs on the service's own scope, not this screen model's. Saving is
            // normally followed by the screen closing, which would cancel anything launched here
            // before the network call had a chance to finish.
            checkInAnalysisService?.analyzeInBackground(checkIn)

            // Picks up the pending row the service just wrote, so a screen that stays open says
            // "reading your answer" instead of continuing to show the previous extraction.
            refreshAnalysis()

            checkIn
        } catch (e: Exception) {
            Napier.e("Failed to save ${slot.toStorageString()} check-in", e)
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                saveError = e.message ?: "Could not save your check-in"
            )
            null
        }
    }

    /**
     * Runs extraction again after a failure.
     *
     * Extraction otherwise only runs on save, so without this a dropped connection would leave
     * the summary permanently empty for an answer that is already stored.
     */
    fun retryAnalysis() {
        val service = checkInAnalysisService ?: return

        // Started on the service's own scope, not this one. A retry launched here would be
        // cancelled the moment the user left the screen, mid-request, leaving the row pending
        // with no completion event — the same trap the initial extraction avoids. The result
        // arrives through analysisCompletions.
        service.retryInBackground(_uiState.value.date, slot)

        screenModelScope.launch { refreshAnalysis() }
    }

    /**
     * A conversation id derived from the day and slot, so re-opening a check-in returns to the
     * same thread rather than starting a new one each time.
     */
    fun conversationExternalId(): String =
        "checkin-${_uiState.value.date}-${slot.toStorageString().lowercase()}"

    /**
     * Opens a health chat about this check-in.
     *
     * Sends an opening turn through the existing [HealthChatService], which creates the
     * conversation on demand from the deterministic id — no new chat plumbing, and no changes to
     * the chat feature itself. The assistant's reply is therefore already present when the
     * thread opens, rather than the user landing in an empty thread.
     *
     * If a thread already exists for this check-in we go straight there instead of sending
     * another opening turn.
     *
     * @param onReady invoked with the conversation id once the thread is ready to show.
     */
    fun talkAboutThis(onReady: (String) -> Unit) {
        val state = _uiState.value
        if (state.isStartingConversation) return

        val existing = state.conversationExternalId
        if (existing != null) {
            onReady(existing)
            return
        }

        val answer = commentsState.value.text.trim()
        if (answer.isEmpty()) return

        screenModelScope.launch {
            // Persist first. The conversation link is written with an UPDATE against an
            // existing row, so chatting about an unsaved answer would silently lose the answer
            // and leave the thread unlinked — reopening would then start a second opening turn
            // into the same conversation.
            if (persist(answer) == null) return@launch

            _uiState.value = _uiState.value.copy(
                isStartingConversation = true,
                conversationError = null
            )

            val externalId = conversationExternalId()
            try {
                val result = conversationStarter.start(
                    conversationExternalId = externalId,
                    openingMessage = openingMessage(answer),
                    title = conversationTitle()
                )

                when (result) {
                    CheckInConversationResult.Started -> {
                        dailyCheckInRepository.attachConversation(
                            date = _uiState.value.date,
                            slot = slot,
                            conversationExternalId = externalId
                        )
                        _uiState.value = _uiState.value.copy(
                            isStartingConversation = false,
                            conversationExternalId = externalId
                        )
                        onReady(externalId)
                    }
                    CheckInConversationResult.ApiKeyMissing -> _uiState.value = _uiState.value.copy(
                        isStartingConversation = false,
                        conversationError = "Add an API key in settings to chat about your check-in."
                    )
                    is CheckInConversationResult.Failed -> _uiState.value = _uiState.value.copy(
                        isStartingConversation = false,
                        conversationError = result.message
                    )
                }
            } catch (e: Exception) {
                Napier.e("Failed to start a conversation about the ${slot.toStorageString()} check-in", e)
                _uiState.value = _uiState.value.copy(
                    isStartingConversation = false,
                    conversationError = e.message ?: "Could not start the conversation"
                )
            }
        }
    }

    /**
     * Frames the check-in for the assistant. The user's words are passed through unaltered;
     * only the surrounding context is added, so the assistant knows this is self-report rather
     * than a measurement.
     */
    private fun openingMessage(answer: String): String {
        val what = when (slot) {
            CheckInSlot.MORNING -> "how I slept and how I feel"
            CheckInSlot.EVENING -> "how the day felt, and anything I didn't log"
        }
        // "already saved" is load-bearing. Without it the assistant reads this as a request to
        // record the check-in and apologises for not being able to, which is both wrong and
        // unsettling when the answer is in fact already stored.
        return "My ${slot.toStorageString().lowercase()} check-in for ${_uiState.value.date} " +
            "is already saved in the app. I want to talk it through, not log it again.\n\n" +
            "What I said about $what:\n\n$answer"
    }

    private fun conversationTitle(): String =
        "${slot.toStorageString()} check-in — ${_uiState.value.date}"

    private fun inputSourceFor(text: String): CheckInInputSource {
        val before = lastTranscribedText
        val grewDuringRecording = before != null && text.length > before.length

        return if (grewDuringRecording) CheckInInputSource.VOICE else CheckInInputSource.TYPED
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
