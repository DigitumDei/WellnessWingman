package com.wellnesswingman.ui.screens.googleexport

import com.wellnesswingman.data.googleexport.GoogleDocsError
import com.wellnesswingman.domain.googleexport.GoogleAuthResult
import com.wellnesswingman.domain.googleexport.GoogleAuthService
import com.wellnesswingman.domain.googleexport.GoogleDocsExportResult
import com.wellnesswingman.domain.googleexport.GoogleDocsExportService
import com.wellnesswingman.domain.googleexport.GoogleExportError
import com.wellnesswingman.domain.report.HealthReportBuilder
import com.wellnesswingman.domain.report.HealthReportData
import com.wellnesswingman.domain.report.HealthReportGatherer
import com.wellnesswingman.domain.report.HealthReportPreset
import com.wellnesswingman.domain.report.HealthReportSection
import com.wellnesswingman.domain.report.ReportMealEntry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDocsExportViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        auth: GoogleAuthService,
        export: GoogleDocsExportService = mockk(relaxed = true),
        snapshot: HealthReportData = nonEmptyData()
    ): GoogleDocsExportViewModel {
        val gatherer = mockk<HealthReportGatherer>(relaxed = true)
        coEvery { gatherer.gather(any()) } returns snapshot
        return GoogleDocsExportViewModel(
            gatherer = gatherer,
            builder = HealthReportBuilder(),
            authService = auth,
            exportService = export
        )
    }

    private fun nonEmptyData() = HealthReportData(
        start = LocalDate(2024, 1, 10),
        end = LocalDate(2024, 1, 10),
        timeZoneId = "UTC",
        meals = listOf(
            ReportMealEntry(date = LocalDate(2024, 1, 10), localTime = LocalDateTime(2024, 1, 10, 8, 0))
        )
    )

    private fun grantingAuth(): GoogleAuthService {
        val auth = mockk<GoogleAuthService>(relaxed = true)
        every { auth.isAvailable } returns true
        coEvery { auth.requestAccess() } returns GoogleAuthResult.Granted("token")
        every { auth.clearAccessToken() } just Runs
        return auth
    }

    private fun prepareSnapshot(vm: GoogleDocsExportViewModel) {
        vm.gatherPreview()
        assertTrue(vm.uiState.value.snapshot?.isEmpty == false)
    }

    @Test
    fun `gather with no selected sections shows a distinct selection error`() {
        val vm = viewModel(auth = grantingAuth())
        vm.uiState.value.selectedSections.toList().forEach { vm.toggleSection(it) }
        assertTrue(vm.uiState.value.selectedSections.isEmpty())

        vm.gatherPreview()

        assertEquals("Select at least one report section.", vm.uiState.value.error)
    }

    @Test
    fun `gather with invalid dates keeps the date format error`() {
        val vm = viewModel(auth = grantingAuth())
        vm.updateStartDate("not-a-date")

        vm.gatherPreview()

        assertEquals("Use ISO dates (YYYY-MM-DD), with an end date on or after the start date.", vm.uiState.value.error)
    }

    @Test
    fun `export failure surfaces the categorized Google error message`() {
        val export = mockk<GoogleDocsExportService>(relaxed = true)
        coEvery { export.createAndPopulate(any(), any(), any(), any()) } returns Result.failure(
            GoogleExportError.ApiFailure(GoogleDocsError.RateLimited("limit"))
        )
        val vm = viewModel(auth = grantingAuth(), export = export)
        prepareSnapshot(vm)

        vm.createGoogleDocument()

        assertEquals("Google is rate limiting requests, please wait and retry", vm.uiState.value.error)
    }

    @Test
    fun `export failure with an uncleaned partial document warns about the orphaned doc`() {
        val export = mockk<GoogleDocsExportService>(relaxed = true)
        coEvery { export.createAndPopulate(any(), any(), any(), any()) } returns Result.failure(
            GoogleExportError.ApiFailure(
                GoogleDocsError.RateLimited("limit"),
                partialDocumentId = "doc-1",
                cleanupDeleted = false
            )
        )
        val vm = viewModel(auth = grantingAuth(), export = export)
        prepareSnapshot(vm)

        vm.createGoogleDocument()

        assertEquals(
            "Google Docs export failed and the incomplete document could not be removed. Check Google Drive before retrying.",
            vm.uiState.value.error
        )
    }

    @Test
    fun `failed authorization is surfaced as a retryable error`() {
        val auth = mockk<GoogleAuthService>(relaxed = true)
        every { auth.isAvailable } returns true
        coEvery { auth.requestAccess() } returns GoogleAuthResult.Failed
        every { auth.clearAccessToken() } just Runs
        val vm = viewModel(auth = auth)
        prepareSnapshot(vm)

        vm.createGoogleDocument()

        assertEquals("Google authorization failed. You can retry.", vm.uiState.value.error)
    }

    @Test
    fun `cancelled consent is surfaced without re-authorizing`() {
        val auth = mockk<GoogleAuthService>(relaxed = true)
        every { auth.isAvailable } returns true
        coEvery { auth.requestAccess() } returns GoogleAuthResult.Cancelled
        val vm = viewModel(auth = auth)
        prepareSnapshot(vm)

        vm.resumeAfterConsent()

        assertEquals("Google authorization was cancelled.", vm.uiState.value.error)
    }

    @Test
    fun `a report build failure is surfaced as an error, not thrown`() {
        val export = mockk<GoogleDocsExportService>(relaxed = true)
        val auth = grantingAuth()
        // An unknown zone id makes the builder's TimeZone.of(...) throw.
        val vm = viewModel(auth = auth, export = export, snapshot = nonEmptyData().copy(timeZoneId = "Not/AZone"))
        prepareSnapshot(vm)

        vm.createGoogleDocument()

        val state = vm.uiState.value
        assertEquals("Unable to build the report document.", state.error)
        assertFalse(state.isExporting)
        coVerify(exactly = 0) { export.createAndPopulate(any(), any(), any(), any()) }
        // The token is still released on the early return.
        verify(exactly = 1) { auth.clearAccessToken() }
    }

    private fun viewModelWithGatherer(gatherer: HealthReportGatherer) = GoogleDocsExportViewModel(
        gatherer = gatherer,
        builder = HealthReportBuilder(),
        authService = grantingAuth(),
        exportService = mockk(relaxed = true)
    )

    @Test
    fun `editing parameters during a gather cancels it without a false error`() {
        val gate = CompletableDeferred<HealthReportData>()
        val gatherer = mockk<HealthReportGatherer>()
        coEvery { gatherer.gather(any()) } coAnswers { gate.await() }
        val vm = viewModelWithGatherer(gatherer)

        vm.gatherPreview()
        assertTrue(vm.uiState.value.isGathering)
        vm.toggleSection(HealthReportSection.CHECK_INS)
        gate.complete(nonEmptyData())

        val state = vm.uiState.value
        assertNull(state.snapshot)
        assertNull(state.preview)
        assertFalse(state.isGathering)
        // The cancellation must not surface as "Unable to prepare the local preview."
        assertNull(state.error)
    }

    @Test
    fun `a gather that finishes after parameters change never becomes the snapshot`() {
        val gate = CompletableDeferred<HealthReportData>()
        val gatherer = mockk<HealthReportGatherer>()
        // Ignores cancellation and returns anyway, so only the revision check
        // stands between this stale data and the export button.
        coEvery { gatherer.gather(any()) } coAnswers { withContext(NonCancellable) { gate.await() } }
        val vm = viewModelWithGatherer(gatherer)

        vm.gatherPreview()
        vm.updateEndDate("2024-01-11")
        gate.complete(nonEmptyData())

        val state = vm.uiState.value
        assertNull(state.snapshot)
        assertNull(state.preview)
        assertFalse(state.isGathering)
    }

    @Test
    fun `editing report parameters clears the previous export link`() {
        val export = mockk<GoogleDocsExportService>(relaxed = true)
        coEvery { export.createAndPopulate(any(), any(), any(), any()) } returns Result.success(
            GoogleDocsExportResult(title = "Report", documentId = "doc-1", url = GoogleDocsExportResult.urlOf("doc-1"))
        )
        val vm = viewModel(auth = grantingAuth(), export = export)

        val edits: List<(GoogleDocsExportViewModel) -> Unit> = listOf(
            { it.updateStartDate("2024-01-09") },
            { it.updateEndDate("2024-01-11") },
            { it.selectPreset(HealthReportPreset.COMPLETE_HEALTH_RECORD) },
            { it.toggleSection(HealthReportSection.WEIGHT_HISTORY) }
        )
        edits.forEach { edit ->
            prepareSnapshot(vm)
            vm.createGoogleDocument()
            assertNotNull(vm.uiState.value.resultUrl)

            edit(vm)

            assertNull(vm.uiState.value.resultUrl)
        }
    }
}
