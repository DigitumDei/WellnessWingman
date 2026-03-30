package com.wellnesswingman.ui.screens.nutrition

import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.domain.analysis.ExtractedNutrition
import com.wellnesswingman.domain.analysis.NutritionLabelAnalyzing
import com.wellnesswingman.domain.analysis.NutritionLabelExtraction
import com.wellnesswingman.platform.CameraCaptureOperations
import com.wellnesswingman.platform.CaptureResult
import com.wellnesswingman.platform.FileSystemOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionLabelScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `analyzeImage surfaces missing api key guidance`() = runTest(dispatcher.scheduler) {
        val viewModel = NutritionLabelScanViewModel(
            profileId = null,
            cameraService = FakeCameraCaptureOperations(),
            fileSystem = FakeFileSystemOperations(),
            analyzer = FakeNutritionLabelAnalyzer(hasConfiguredApiKey = false),
            repository = FakeNutritionalProfileRepository()
        )
        viewModel.captureFromCamera()
        advanceUntilIdle()

        viewModel.analyzeImage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzing)
        assertEquals(
            "Missing API Key. Go to Settings to add your OpenAI or Gemini API key to extract nutrition facts.",
            state.error
        )
    }

    @Test
    fun `analyzeImage clears stale extracted state before failed retry`() = runTest(dispatcher.scheduler) {
        val analyzer = FakeNutritionLabelAnalyzer(
            hasConfiguredApiKey = true,
            nextResult = Result.failure(IllegalStateException("bad json"))
        )
        val viewModel = NutritionLabelScanViewModel(
            profileId = null,
            cameraService = FakeCameraCaptureOperations(),
            fileSystem = FakeFileSystemOperations(),
            analyzer = analyzer,
            repository = FakeNutritionalProfileRepository()
        )

        viewModel.updateServingSize("1 bar")
        viewModel.updateNutrition { it.copy(totalCalories = 190.0, protein = 21.0) }
        viewModel.captureFromCamera()
        advanceUntilIdle()

        viewModel.analyzeImage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzing)
        assertEquals("", state.servingSize)
        assertNull(state.nutrition.totalCalories)
        assertTrue(state.extractionWarnings.isEmpty())
        assertNull(state.rawJson)
        assertEquals("bad json", state.error)
    }

    @Test
    fun `missing edit profile surfaces error instead of blank create state`() = runTest(dispatcher.scheduler) {
        val viewModel = NutritionLabelScanViewModel(
            profileId = 42L,
            cameraService = FakeCameraCaptureOperations(),
            fileSystem = FakeFileSystemOperations(),
            analyzer = FakeNutritionLabelAnalyzer(),
            repository = FakeNutritionalProfileRepository()
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("This nutritional profile no longer exists.", state.error)
        assertEquals("", state.primaryName)
    }

    @Test
    fun `save trims fields and inserts new profile`() = runTest(dispatcher.scheduler) {
        val repository = FakeNutritionalProfileRepository()
        val viewModel = NutritionLabelScanViewModel(
            profileId = null,
            cameraService = FakeCameraCaptureOperations(),
            fileSystem = FakeFileSystemOperations(),
            analyzer = FakeNutritionLabelAnalyzer(),
            repository = repository
        )
        var savedCalled = false

        viewModel.updatePrimaryName("  Quest Bar  ")
        viewModel.updateAliases("protein bar, quest bar, protein bar")
        viewModel.updateServingSize(" 1 bar ")
        viewModel.updateNutrition { it.copy(totalCalories = 190.0, protein = 21.0) }
        viewModel.save { savedCalled = true }
        advanceUntilIdle()

        val inserted = repository.inserted.single()
        assertTrue(savedCalled)
        assertEquals("Quest Bar", inserted.primaryName)
        assertEquals(listOf("protein bar", "quest bar"), inserted.aliases)
        assertEquals("1 bar", inserted.servingSize)
        assertEquals(190.0, inserted.calories)
        assertEquals(21.0, inserted.protein)
        assertTrue(viewModel.uiState.value.saveCompleted)
    }

    @Test
    fun `applyCapturedPhoto replaces stale extraction state`() = runTest(dispatcher.scheduler) {
        val viewModel = NutritionLabelScanViewModel(
            profileId = null,
            cameraService = FakeCameraCaptureOperations(),
            fileSystem = FakeFileSystemOperations(),
            analyzer = FakeNutritionLabelAnalyzer(),
            repository = FakeNutritionalProfileRepository()
        )

        viewModel.updateServingSize("1 bar")
        viewModel.updateNutrition { it.copy(totalCalories = 190.0) }
        viewModel.applyCapturedPhoto("/tmp/new-label.jpg", byteArrayOf(9, 8, 7))

        val state = viewModel.uiState.value
        assertEquals("/tmp/new-label.jpg", state.sourceImagePath)
        assertContentEquals(byteArrayOf(9, 8, 7), state.photoBytes)
        assertEquals("", state.servingSize)
        assertNull(state.nutrition.totalCalories)
        assertTrue(state.extractionWarnings.isEmpty())
        assertNull(state.rawJson)
    }
}

private class FakeCameraCaptureOperations : CameraCaptureOperations {
    override suspend fun capturePhoto(): CaptureResult = CaptureResult.Success(
        photoPath = "/tmp/label.jpg",
        bytes = byteArrayOf(1, 2, 3)
    )

    override suspend fun pickFromGallery(): CaptureResult? = null
}

private class FakeFileSystemOperations : FileSystemOperations {
    override fun getAppDataDirectory(): String = "/tmp"
    override fun getPhotosDirectory(): String = "/tmp"
    override suspend fun readBytes(path: String): ByteArray = byteArrayOf()
    override suspend fun writeBytes(path: String, bytes: ByteArray) = Unit
    override suspend fun delete(path: String): Boolean = true
    override fun exists(path: String): Boolean = false
    override fun isDirectory(path: String): Boolean = false
    override fun listFiles(path: String): List<String> = emptyList()
    override fun createDirectory(path: String): Boolean = true
    override fun getCacheDirectory(): String = "/tmp"
    override fun getExportsDirectory(): String = "/tmp"
    override fun listFilesRecursively(path: String): List<String> = emptyList()
    override suspend fun copyFile(sourcePath: String, destPath: String) = Unit
}

private class FakeNutritionLabelAnalyzer(
    private val hasConfiguredApiKey: Boolean = true,
    var nextResult: Result<NutritionLabelExtraction> = Result.success(
        NutritionLabelExtraction(
            servingSize = "1 serving",
            nutrition = ExtractedNutrition(totalCalories = 100.0),
            confidence = 0.9
        )
    )
) : NutritionLabelAnalyzing {
    override fun hasConfiguredApiKey(): Boolean = hasConfiguredApiKey

    override suspend fun analyzeLabelImage(
        imageBytes: ByteArray,
        sourceImagePath: String?
    ): NutritionLabelExtraction = nextResult.getOrThrow()
}

private class FakeNutritionalProfileRepository(
    private val profiles: MutableList<NutritionalProfile> = mutableListOf()
) : NutritionalProfileRepository {
    val inserted = mutableListOf<NutritionalProfile>()

    override suspend fun getAll(): List<NutritionalProfile> = profiles.toList()

    override suspend fun getById(profileId: Long): NutritionalProfile? =
        profiles.find { it.profileId == profileId }

    override suspend fun getByExternalId(externalId: String): NutritionalProfile? =
        profiles.find { it.externalId == externalId }

    override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> = emptyList()

    override suspend fun insert(profile: NutritionalProfile): Long {
        inserted += profile
        profiles += profile.copy(profileId = (profiles.maxOfOrNull { it.profileId } ?: 0L) + 1L)
        return profiles.last().profileId
    }

    override suspend fun update(profile: NutritionalProfile) {
        val index = profiles.indexOfFirst { it.profileId == profile.profileId }
        if (index >= 0) {
            profiles[index] = profile
        }
    }

    override suspend fun delete(profileId: Long) {
        profiles.removeAll { it.profileId == profileId }
    }

    override suspend fun upsert(profile: NutritionalProfile) {
        update(profile)
    }
}
