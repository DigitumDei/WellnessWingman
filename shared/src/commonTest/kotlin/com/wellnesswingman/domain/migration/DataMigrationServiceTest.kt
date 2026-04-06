package com.wellnesswingman.domain.migration

import com.wellnesswingman.data.model.DailySummary
import com.wellnesswingman.data.model.EntryAnalysis
import com.wellnesswingman.data.model.EntryType
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.model.ProcessingStatus
import com.wellnesswingman.data.model.TrackedEntry
import com.wellnesswingman.data.model.WeeklySummary
import com.wellnesswingman.data.model.WeightRecord
import com.wellnesswingman.data.model.export.ExportData
import com.wellnesswingman.data.model.export.ExportDailySummary
import com.wellnesswingman.data.model.export.ExportEntryAnalysis
import com.wellnesswingman.data.model.export.ExportNutritionalProfile
import com.wellnesswingman.data.model.export.ExportTrackedEntry
import com.wellnesswingman.data.model.export.ExportUserProfile
import com.wellnesswingman.data.model.export.ExportWeeklySummary
import com.wellnesswingman.data.model.export.ExportWeightRecord
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.LlmProvider
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.data.repository.WeeklySummaryRepository
import com.wellnesswingman.data.repository.WeightHistoryRepository
import com.wellnesswingman.platform.FileSystemOperations
import com.wellnesswingman.platform.ZipEntry
import com.wellnesswingman.platform.ZipFileSource
import com.wellnesswingman.platform.ZipOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// region -- Fakes --

private class FakeTrackedEntryRepository : TrackedEntryRepository {
    val entries = mutableListOf<TrackedEntry>()
    val upserted = mutableListOf<TrackedEntry>()

    override suspend fun getAllEntries(): List<TrackedEntry> = entries
    override suspend fun getRecentEntries(limit: Int, entryType: EntryType?): List<TrackedEntry> = emptyList()
    override fun observeAllEntries(): Flow<List<TrackedEntry>> = flowOf(entries)
    override suspend fun getEntryById(id: Long): TrackedEntry? = entries.find { it.entryId == id }
    override suspend fun getEntryByExternalId(externalId: String): TrackedEntry? = entries.find { it.externalId == externalId }
    override suspend fun getEntriesForDay(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
    override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> = emptyList()
    override fun observeEntriesForDay(date: LocalDate): Flow<List<TrackedEntry>> = flowOf(emptyList())
    override suspend fun getEntriesForWeek(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
    override suspend fun getEntriesForMonth(startMillis: Long, endMillis: Long): List<TrackedEntry> = emptyList()
    override suspend fun getEntriesByStatus(status: ProcessingStatus): List<TrackedEntry> = emptyList()
    override suspend fun getPendingEntries(): List<TrackedEntry> = emptyList()
    override suspend fun insertEntry(entry: TrackedEntry): Long = entry.entryId
    override suspend fun updateEntryStatus(id: Long, status: ProcessingStatus) {}
    override suspend fun updateEntryType(id: Long, entryType: EntryType) {}
    override suspend fun updateEntryPayload(id: Long, payload: String, schemaVersion: Int) {}
    override suspend fun updateUserNotes(id: Long, notes: String?) {}
    override suspend fun deleteEntry(id: Long) {}
    override suspend fun upsertEntry(entry: TrackedEntry) { upserted.add(entry) }
}

private class FakeEntryAnalysisRepository : EntryAnalysisRepository {
    val analyses = mutableListOf<EntryAnalysis>()
    val upserted = mutableListOf<EntryAnalysis>()

    override suspend fun getAllAnalyses(): List<EntryAnalysis> = analyses
    override suspend fun getAnalysisById(id: Long): EntryAnalysis? = null
    override suspend fun getAnalysisByExternalId(externalId: String): EntryAnalysis? = null
    override suspend fun getAnalysesForEntry(entryId: Long): List<EntryAnalysis> = emptyList()
    override suspend fun getLatestAnalysisForEntry(entryId: Long): EntryAnalysis? = null
    override suspend fun insertAnalysis(analysis: EntryAnalysis): Long = analysis.analysisId
    override suspend fun updateAnalysis(id: Long, insightsJson: String, schemaVersion: String) {}
    override suspend fun deleteAnalysis(id: Long) {}
    override suspend fun deleteAnalysesForEntry(entryId: Long) {}
    override suspend fun upsertAnalysis(analysis: EntryAnalysis) { upserted.add(analysis) }
}

private class FakeNutritionalProfileRepository : NutritionalProfileRepository {
    val profiles = mutableListOf<NutritionalProfile>()
    val upserted = mutableListOf<NutritionalProfile>()
    val failOnExternalIds = mutableSetOf<String>()

    override fun getAllAsFlow(): Flow<List<NutritionalProfile>> = flowOf(profiles)
    override suspend fun getAll(): List<NutritionalProfile> = profiles
    override suspend fun getById(profileId: Long): NutritionalProfile? = profiles.find { it.profileId == profileId }
    override suspend fun getByExternalId(externalId: String): NutritionalProfile? = profiles.find { it.externalId == externalId }
    override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> = emptyList()
    override suspend fun insert(profile: NutritionalProfile): Long = profile.profileId
    override suspend fun update(profile: NutritionalProfile) {}
    override suspend fun delete(profileId: Long) {}
    override suspend fun upsert(profile: NutritionalProfile) {
        if (profile.externalId in failOnExternalIds) {
            throw IllegalStateException("duplicate profile ${profile.externalId}")
        }
        upserted.removeAll { it.profileId == profile.profileId || it.externalId == profile.externalId }
        upserted.add(profile)
    }
}

private class FakeDailySummaryRepository : DailySummaryRepository {
    val summaries = mutableListOf<DailySummary>()
    val upserted = mutableListOf<DailySummary>()

    override suspend fun getAllSummaries(): List<DailySummary> = summaries
    override suspend fun getSummaryById(id: Long): DailySummary? = null
    override suspend fun getSummaryByExternalId(externalId: String): DailySummary? = null
    override suspend fun getSummaryForDate(date: LocalDate): DailySummary? = null
    override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate): List<DailySummary> = emptyList()
    override suspend fun getRecentSummaries(limit: Long): List<DailySummary> = emptyList()
    override suspend fun insertSummary(summary: DailySummary): Long = summary.summaryId
    override suspend fun updateSummary(id: Long, highlights: String, recommendations: String) {}
    override suspend fun updateSummaryByDate(date: LocalDate, highlights: String, recommendations: String) {}
    override suspend fun updateUserComments(date: LocalDate, comments: String?) {}
    override suspend fun deleteSummary(id: Long) {}
    override suspend fun deleteSummaryByDate(date: LocalDate) {}
    override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
    override suspend fun upsertSummary(summary: DailySummary) { upserted.add(summary) }
}

private class FakeWeeklySummaryRepository : WeeklySummaryRepository {
    val summaries = mutableListOf<WeeklySummary>()
    val inserted = mutableListOf<WeeklySummary>()
    val updated = mutableListOf<WeeklySummary>()

    override suspend fun getAllSummaries(): List<WeeklySummary> = summaries
    override suspend fun getSummaryById(id: Long): WeeklySummary? = null
    override suspend fun getSummaryForWeek(weekStart: LocalDate): WeeklySummary? =
        summaries.find { it.weekStartDate == weekStart }
    override suspend fun getSummariesForDateRange(startDate: LocalDate, endDate: LocalDate): List<WeeklySummary> = emptyList()
    override suspend fun getRecentSummaries(limit: Long): List<WeeklySummary> = emptyList()
    override suspend fun insertSummary(summary: WeeklySummary): Long {
        inserted.add(summary)
        return summary.summaryId
    }
    override suspend fun updateSummary(summary: WeeklySummary) {}
    override suspend fun updateSummaryByWeek(weekStart: LocalDate, summary: WeeklySummary) {
        updated.add(summary)
    }
    override suspend fun updateUserComments(weekStart: LocalDate, comments: String?) {}
    override suspend fun deleteSummary(id: Long) {}
    override suspend fun deleteSummaryByWeek(weekStart: LocalDate) {}
    override suspend fun deleteOldSummaries(beforeDate: LocalDate) {}
}

private class FakeAppSettingsRepository : AppSettingsRepository {
    val settings = mutableMapOf<String, Any?>()

    override fun getApiKey(provider: LlmProvider): String? = null
    override fun setApiKey(provider: LlmProvider, apiKey: String) {}
    override fun removeApiKey(provider: LlmProvider) {}
    override fun getSelectedProvider(): LlmProvider = LlmProvider.OPENAI
    override fun setSelectedProvider(provider: LlmProvider) {}
    override fun getModel(provider: LlmProvider): String? = null
    override fun setModel(provider: LlmProvider, model: String) {}
    override fun clear() {}
    override fun getHeight(): Double? = settings["height"] as? Double
    override fun setHeight(height: Double) { settings["height"] = height }
    override fun getHeightUnit(): String = (settings["heightUnit"] as? String) ?: "cm"
    override fun setHeightUnit(unit: String) { settings["heightUnit"] = unit }
    override fun getSex(): String? = settings["sex"] as? String
    override fun setSex(sex: String) { settings["sex"] = sex }
    override fun getCurrentWeight(): Double? = settings["currentWeight"] as? Double
    override fun setCurrentWeight(weight: Double) { settings["currentWeight"] = weight }
    override fun getWeightUnit(): String = (settings["weightUnit"] as? String) ?: "kg"
    override fun setWeightUnit(unit: String) { settings["weightUnit"] = unit }
    override fun getDateOfBirth(): String? = settings["dateOfBirth"] as? String
    override fun setDateOfBirth(dob: String) { settings["dateOfBirth"] = dob }
    override fun getActivityLevel(): String? = settings["activityLevel"] as? String
    override fun setActivityLevel(level: String) { settings["activityLevel"] = level }
    override fun clearHeight() { settings.remove("height") }
    override fun clearCurrentWeight() { settings.remove("currentWeight") }
    override fun clearProfileData() { settings.clear() }
    override fun getImageRetentionThresholdDays(): Int = (settings["imageRetentionDays"] as? Int) ?: 30
    override fun setImageRetentionThresholdDays(days: Int) { settings["imageRetentionDays"] = days }

    // Polar Integration stubs
    override fun getPolarAccessToken(): String? = null
    override fun setPolarAccessToken(token: String) {}
    override fun getPolarRefreshToken(): String? = null
    override fun setPolarRefreshToken(token: String) {}
    override fun getPolarTokenExpiresAt(): Long = 0L
    override fun setPolarTokenExpiresAt(expiresAt: Long) {}
    override fun getPolarUserId(): String? = null
    override fun setPolarUserId(userId: String) {}
    override fun getPendingOAuthState(): String? = null
    override fun setPendingOAuthState(state: String) {}
    override fun getPendingOAuthSessionId(): String? = null
    override fun setPendingOAuthSessionId(sessionId: String) {}
    override fun clearPendingOAuthSession() {}
    override fun clearPolarTokens() {}
    override fun isPolarConnected(): Boolean = false
}

private class FakeWeightHistoryRepository : WeightHistoryRepository {
    val records = mutableListOf<WeightRecord>()
    val upserted = mutableListOf<WeightRecord>()

    override suspend fun addWeightRecord(record: WeightRecord): Long = record.weightRecordId
    override suspend fun getWeightHistory(startDate: Instant, endDate: Instant): List<WeightRecord> = emptyList()
    override suspend fun getLatestWeightRecord(): WeightRecord? = records.lastOrNull()
    override suspend fun getAllWeightRecords(): List<WeightRecord> = records
    override suspend fun deleteWeightRecord(recordId: Long) {}
    override suspend fun nullifyRelatedEntryId(entryId: Long) {}
    override suspend fun upsertWeightRecord(record: WeightRecord) { upserted.add(record) }
}

private class FakeFileSystem : FileSystemOperations {
    val files = mutableMapOf<String, ByteArray>()
    val directories = mutableSetOf<String>()
    val copiedFiles = mutableListOf<Pair<String, String>>()

    init {
        directories.add("/app/data")
        directories.add("/app/cache")
        directories.add("/app/exports")
    }

    override fun getAppDataDirectory(): String = "/app/data"
    override fun getPhotosDirectory(): String = "/app/data/photos"
    override suspend fun readBytes(path: String): ByteArray = files[path] ?: ByteArray(0)
    override suspend fun writeBytes(path: String, bytes: ByteArray) { files[path] = bytes }
    override suspend fun delete(path: String): Boolean { files.remove(path); directories.remove(path); return true }
    override fun exists(path: String): Boolean = files.containsKey(path) || directories.contains(path)
    override fun isDirectory(path: String): Boolean = directories.contains(path)
    override fun listFiles(path: String): List<String> = files.keys.filter {
        val parent = it.substringBeforeLast('/')
        parent == path.trimEnd('/')
    }
    override fun createDirectory(path: String): Boolean { directories.add(path); return true }
    override fun getCacheDirectory(): String = "/app/cache"
    override fun getExportsDirectory(): String = "/app/exports"
    override fun listFilesRecursively(path: String): List<String> {
        val prefix = path.trimEnd('/') + "/"
        return files.keys.filter { it.startsWith(prefix) }
    }
    override suspend fun copyFile(sourcePath: String, destPath: String) {
        copiedFiles.add(sourcePath to destPath)
        files[destPath] = files[sourcePath] ?: ByteArray(0)
    }
}

private class FakeZipUtil : ZipOperations {
    var createdZipPath: String? = null
    var createdInMemoryEntries: List<ZipEntry>? = null
    var createdFileEntries: List<ZipFileSource>? = null
    var onExtract: ((String, String) -> Unit)? = null

    override suspend fun createZip(zipPath: String, entries: List<ZipEntry>) {
        createdZipPath = zipPath
    }

    override suspend fun createZipWithFiles(
        zipPath: String,
        inMemoryEntries: List<ZipEntry>,
        fileEntries: List<ZipFileSource>
    ) {
        createdZipPath = zipPath
        createdInMemoryEntries = inMemoryEntries
        createdFileEntries = fileEntries
    }

    override suspend fun extractZip(zipPath: String, destDir: String) {
        onExtract?.invoke(zipPath, destDir)
    }

    override suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray? = null
}

// endregion

class DataMigrationServiceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
    }

    private fun createService(
        trackedEntryRepo: FakeTrackedEntryRepository = FakeTrackedEntryRepository(),
        entryAnalysisRepo: FakeEntryAnalysisRepository = FakeEntryAnalysisRepository(),
        nutritionalProfileRepo: FakeNutritionalProfileRepository = FakeNutritionalProfileRepository(),
        dailySummaryRepo: FakeDailySummaryRepository = FakeDailySummaryRepository(),
        weeklySummaryRepo: FakeWeeklySummaryRepository = FakeWeeklySummaryRepository(),
        appSettingsRepo: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        weightHistoryRepo: FakeWeightHistoryRepository = FakeWeightHistoryRepository(),
        fileSystem: FakeFileSystem = FakeFileSystem(),
        zipUtil: FakeZipUtil = FakeZipUtil()
    ): DefaultDataMigrationService = DefaultDataMigrationService(
        trackedEntryRepository = trackedEntryRepo,
        entryAnalysisRepository = entryAnalysisRepo,
        nutritionalProfileRepository = nutritionalProfileRepo,
        dailySummaryRepository = dailySummaryRepo,
        weeklySummaryRepository = weeklySummaryRepo,
        appSettingsRepository = appSettingsRepo,
        weightHistoryRepository = weightHistoryRepo,
        fileSystem = fileSystem,
        zipUtil = zipUtil
    )

    // region -- Export tests --

    @Test
    fun `exportData returns zip path`() = runTest {
        val zipUtil = FakeZipUtil()
        val service = createService(zipUtil = zipUtil)

        val result = service.exportData()

        assertTrue(result.startsWith("/app/exports/wellnesswingman_export_"))
        assertTrue(result.endsWith(".zip"))
        assertEquals(result, zipUtil.createdZipPath)
    }

    @Test
    fun `exportData includes entries and analyses in JSON`() = runTest {
        val trackedEntryRepo = FakeTrackedEntryRepository()
        val entryAnalysisRepo = FakeEntryAnalysisRepository()
        val now = Instant.parse("2026-02-11T09:00:00Z")

        trackedEntryRepo.entries.add(
            TrackedEntry(entryId = 1, entryType = EntryType.MEAL, capturedAt = now, dataPayload = "{}")
        )
        entryAnalysisRepo.analyses.add(
            EntryAnalysis(analysisId = 1, entryId = 1, capturedAt = now, insightsJson = """{"cal":500}""")
        )

        val zipUtil = FakeZipUtil()
        val service = createService(
            trackedEntryRepo = trackedEntryRepo,
            entryAnalysisRepo = entryAnalysisRepo,
            zipUtil = zipUtil
        )

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(1, exportData.entries.size)
        assertEquals(1, exportData.analyses.size)
        assertEquals(1L, exportData.entries[0].entryId)
    }

    @Test
    fun `exportData includes nutritional profiles in JSON`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val now = Instant.parse("2026-02-11T09:00:00Z")
        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 7,
                externalId = "profile-7",
                primaryName = "Protein Shake",
                aliases = listOf("Shake"),
                calories = 160.0,
                sourceImagePath = "/app/data/photos/labels/shake.jpg",
                createdAt = now,
                updatedAt = now
            )
        )

        val zipUtil = FakeZipUtil()
        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            zipUtil = zipUtil
        )

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(1, exportData.nutritionalProfiles.size)
        assertEquals("Protein Shake", exportData.nutritionalProfiles[0].primaryName)
        assertEquals("photos/labels/shake.jpg", exportData.nutritionalProfiles[0].sourceImagePath)
    }

    @Test
    fun `exportData includes user profile`() = runTest {
        val appSettingsRepo = FakeAppSettingsRepository()
        appSettingsRepo.setHeight(180.0)
        appSettingsRepo.setSex("Male")
        appSettingsRepo.setCurrentWeight(75.0)
        appSettingsRepo.setWeightUnit("kg")

        val zipUtil = FakeZipUtil()
        val service = createService(appSettingsRepo = appSettingsRepo, zipUtil = zipUtil)

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(180.0, exportData.userProfile?.height)
        assertEquals("Male", exportData.userProfile?.sex)
        assertEquals(75.0, exportData.userProfile?.currentWeight)
    }

    @Test
    fun `exportData includes weight records`() = runTest {
        val weightRepo = FakeWeightHistoryRepository()
        val now = Instant.parse("2026-02-11T08:00:00Z")
        weightRepo.records.add(
            WeightRecord(weightRecordId = 1, recordedAt = now, weightValue = 75.0, weightUnit = "kg", source = "Manual")
        )

        val zipUtil = FakeZipUtil()
        val service = createService(weightHistoryRepo = weightRepo, zipUtil = zipUtil)

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(1, exportData.weightRecords.size)
        assertEquals(75.0, exportData.weightRecords[0].weightValue)
    }

    @Test
    fun `exportData includes weekly summaries`() = runTest {
        val weeklySummaryRepo = FakeWeeklySummaryRepository()
        weeklySummaryRepo.summaries.add(
            WeeklySummary(
                summaryId = 1,
                weekStartDate = LocalDate.parse("2026-02-10"),
                highlights = "Good week",
                mealCount = 21,
                totalEntries = 35
            )
        )

        val zipUtil = FakeZipUtil()
        val service = createService(weeklySummaryRepo = weeklySummaryRepo, zipUtil = zipUtil)

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(1, exportData.weeklySummaries.size)
        assertEquals(21, exportData.weeklySummaries[0].mealCount)
    }

    @Test
    fun `exportData relativizes blob paths`() = runTest {
        val trackedEntryRepo = FakeTrackedEntryRepository()
        val now = Instant.parse("2026-02-11T09:00:00Z")
        trackedEntryRepo.entries.add(
            TrackedEntry(
                entryId = 1,
                entryType = EntryType.MEAL,
                capturedAt = now,
                blobPath = "/app/data/photos/meal.jpg",
                dataPayload = "{}"
            )
        )

        val zipUtil = FakeZipUtil()
        val service = createService(trackedEntryRepo = trackedEntryRepo, zipUtil = zipUtil)

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals("photos/meal.jpg", exportData.entries[0].blobPath)
    }

    @Test
    fun `exportData purges old zip files`() = runTest {
        val fileSystem = FakeFileSystem()
        fileSystem.files["/app/exports/old_export.zip"] = ByteArray(0)

        val service = createService(fileSystem = fileSystem)

        service.exportData()

        assertTrue(!fileSystem.files.containsKey("/app/exports/old_export.zip"))
    }

    @Test
    fun `exportData includes image files in zip`() = runTest {
        val trackedEntryRepo = FakeTrackedEntryRepository()
        val fileSystem = FakeFileSystem()
        val now = Instant.parse("2026-02-11T09:00:00Z")

        trackedEntryRepo.entries.add(
            TrackedEntry(
                entryId = 1,
                entryType = EntryType.MEAL,
                capturedAt = now,
                blobPath = "/app/data/photos/meal.jpg",
                dataPayload = "{}"
            )
        )
        fileSystem.files["/app/data/photos/meal.jpg"] = byteArrayOf(1, 2, 3)

        val zipUtil = FakeZipUtil()
        val service = createService(
            trackedEntryRepo = trackedEntryRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        service.exportData()

        assertEquals(1, zipUtil.createdFileEntries!!.size)
        assertEquals("photos/meal.jpg", zipUtil.createdFileEntries!![0].name)
    }

    @Test
    fun `exportData includes nutritional profile image files in zip`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val fileSystem = FakeFileSystem()
        val now = Instant.parse("2026-02-11T09:00:00Z")

        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 1,
                externalId = "profile-1",
                primaryName = "Granola Bar",
                sourceImagePath = "/app/data/photos/labels/granola.jpg",
                createdAt = now,
                updatedAt = now
            )
        )
        fileSystem.files["/app/data/photos/labels/granola.jpg"] = byteArrayOf(4, 5, 6)

        val zipUtil = FakeZipUtil()
        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        service.exportData()

        assertEquals(1, zipUtil.createdFileEntries!!.size)
        assertEquals("photos/labels/granola.jpg", zipUtil.createdFileEntries!![0].name)
    }

    @Test
    fun `exportData rewrites external nutritional profile image paths to stable archive paths`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val fileSystem = FakeFileSystem()
        val now = Instant.parse("2026-02-11T09:00:00Z")

        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 11,
                externalId = "usda:granola/bar",
                primaryName = "Granola Bar",
                sourceImagePath = "/Users/test/Desktop/granola.jpg",
                createdAt = now,
                updatedAt = now
            )
        )
        fileSystem.files["/Users/test/Desktop/granola.jpg"] = byteArrayOf(4, 5, 6)

        val zipUtil = FakeZipUtil()
        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(
            "nutritional-profiles/usda_granola_bar/granola.jpg",
            exportData.nutritionalProfiles[0].sourceImagePath
        )
        assertEquals(
            "nutritional-profiles/usda_granola_bar/granola.jpg",
            zipUtil.createdFileEntries!!.single().name
        )
    }

    @Test
    fun `exportData keeps distinct archive paths for external nutritional profile basename collisions`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val fileSystem = FakeFileSystem()
        val now = Instant.parse("2026-02-11T09:00:00Z")

        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 1,
                externalId = "profile-a",
                primaryName = "Item A",
                sourceImagePath = "/Users/a/label.jpg",
                createdAt = now,
                updatedAt = now
            )
        )
        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 2,
                externalId = "profile-b",
                primaryName = "Item B",
                sourceImagePath = "/Users/b/label.jpg",
                createdAt = now,
                updatedAt = now
            )
        )
        fileSystem.files["/Users/a/label.jpg"] = byteArrayOf(1)
        fileSystem.files["/Users/b/label.jpg"] = byteArrayOf(2)

        val zipUtil = FakeZipUtil()
        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        service.exportData()

        assertEquals(
            setOf(
                "nutritional-profiles/profile-a/label.jpg",
                "nutritional-profiles/profile-b/label.jpg"
            ),
            zipUtil.createdFileEntries!!.map { it.name }.toSet()
        )
    }

    @Test
    fun `exportData skips missing nutritional profile image files but preserves archive path in JSON`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val now = Instant.parse("2026-02-11T09:00:00Z")
        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 3,
                externalId = "missing-profile",
                primaryName = "Missing Image",
                sourceImagePath = "/Users/test/Desktop/missing.jpg",
                createdAt = now,
                updatedAt = now
            )
        )

        val zipUtil = FakeZipUtil()
        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            zipUtil = zipUtil
        )

        service.exportData()

        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals(
            "nutritional-profiles/missing-profile/missing.jpg",
            exportData.nutritionalProfiles[0].sourceImagePath
        )
        assertTrue(zipUtil.createdFileEntries!!.isEmpty())
    }

    @Test
    fun `exportData ignores blank nutritional profile image paths`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val now = Instant.parse("2026-02-11T09:00:00Z")
        nutritionalProfileRepo.profiles.add(
            NutritionalProfile(
                profileId = 4,
                externalId = "blank-image",
                primaryName = "Blank Image",
                sourceImagePath = "",
                createdAt = now,
                updatedAt = now
            )
        )

        val zipUtil = FakeZipUtil()
        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            zipUtil = zipUtil
        )

        service.exportData()

        assertTrue(zipUtil.createdFileEntries!!.isEmpty())
        val jsonEntry = zipUtil.createdInMemoryEntries!!.find { it.name == "data.json" }!!
        val exportData = json.decodeFromString(ExportData.serializer(), jsonEntry.data.decodeToString())
        assertEquals("", exportData.nutritionalProfiles[0].sourceImagePath)
    }

    // endregion

    // region -- Import tests --

    @Test
    fun `importData returns error when data json is missing`() = runTest {
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
        }

        val service = createService(fileSystem = fileSystem, zipUtil = zipUtil)

        val result = service.importData("/path/to/import.zip")

        assertTrue(!result.isSuccess)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("data.json not found"))
    }

    @Test
    fun `importData returns error on invalid JSON`() = runTest {
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = "not valid json!!!".encodeToByteArray()
        }

        val service = createService(fileSystem = fileSystem, zipUtil = zipUtil)

        val result = service.importData("/path/to/import.zip")

        assertTrue(!result.isSuccess)
        assertTrue(result.errors[0].contains("Failed to parse data.json"))
    }

    @Test
    fun `importData imports entries, nutritional profiles, analyses, summaries, and weight records`() = runTest {
        val trackedEntryRepo = FakeTrackedEntryRepository()
        val entryAnalysisRepo = FakeEntryAnalysisRepository()
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val dailySummaryRepo = FakeDailySummaryRepository()
        val weeklySummaryRepo = FakeWeeklySummaryRepository()
        val weightHistoryRepo = FakeWeightHistoryRepository()
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            entries = listOf(
                ExportTrackedEntry(
                    entryId = 1,
                    entryType = EntryType.MEAL,
                    capturedAt = "2026-02-11T09:00:00Z",
                    dataPayload = "{}"
                )
            ),
            nutritionalProfiles = listOf(
                ExportNutritionalProfile(
                    profileId = 1,
                    externalId = "profile-1",
                    primaryName = "Yogurt",
                    aliases = listOf("Greek Yogurt"),
                    calories = 120.0,
                    sourceImagePath = "photos/labels/yogurt.jpg",
                    createdAt = "2026-02-11T09:00:00Z",
                    updatedAt = "2026-02-11T09:00:00Z"
                )
            ),
            analyses = listOf(
                ExportEntryAnalysis(
                    analysisId = 1,
                    entryId = 1,
                    capturedAt = "2026-02-11T09:00:00Z"
                )
            ),
            summaries = listOf(
                ExportDailySummary(
                    summaryId = 1,
                    summaryDate = "2026-02-11"
                )
            ),
            weeklySummaries = listOf(
                ExportWeeklySummary(
                    summaryId = 1,
                    weekStartDate = "2026-02-10"
                )
            ),
            weightRecords = listOf(
                ExportWeightRecord(
                    weightRecordId = 1,
                    recordedAt = "2026-02-11T08:00:00Z",
                    weightValue = 75.0,
                    weightUnit = "kg",
                    source = "Manual"
                )
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)

        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
            fileSystem.files["$destDir/photos/labels/yogurt.jpg"] = byteArrayOf(9, 8, 7)
        }

        val service = createService(
            trackedEntryRepo = trackedEntryRepo,
            entryAnalysisRepo = entryAnalysisRepo,
            nutritionalProfileRepo = nutritionalProfileRepo,
            dailySummaryRepo = dailySummaryRepo,
            weeklySummaryRepo = weeklySummaryRepo,
            weightHistoryRepo = weightHistoryRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertEquals(1, result.entriesImported)
        assertEquals(1, result.nutritionalProfilesImported)
        assertEquals(1, result.analysesImported)
        assertEquals(1, result.summariesImported)
        assertEquals(1, result.weeklySummariesImported)
        assertEquals(1, result.weightRecordsImported)
        assertEquals(1, trackedEntryRepo.upserted.size)
        assertEquals(1, nutritionalProfileRepo.upserted.size)
        assertEquals(1, entryAnalysisRepo.upserted.size)
        assertEquals(1, dailySummaryRepo.upserted.size)
        assertEquals(1, weeklySummaryRepo.inserted.size)
        assertEquals(1, weightHistoryRepo.upserted.size)
        assertEquals("/app/data/photos/labels/yogurt.jpg", nutritionalProfileRepo.upserted[0].sourceImagePath)
        assertTrue(fileSystem.copiedFiles.any { (_, dest) -> dest == "/app/data/photos/labels/yogurt.jpg" })
    }

    @Test
    fun `importData rewrites exported external nutritional profile images into app data`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            nutritionalProfiles = listOf(
                ExportNutritionalProfile(
                    profileId = 1,
                    externalId = "profile-a",
                    primaryName = "External Profile",
                    sourceImagePath = "nutritional-profiles/profile-a/label.jpg",
                    createdAt = "2026-02-11T09:00:00Z",
                    updatedAt = "2026-02-11T09:00:00Z"
                )
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
            fileSystem.files["$destDir/nutritional-profiles/profile-a/label.jpg"] = byteArrayOf(7, 8, 9)
        }

        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertEquals("/app/data/nutritional-profiles/profile-a/label.jpg", nutritionalProfileRepo.upserted.single().sourceImagePath)
        assertTrue(
            fileSystem.copiedFiles.any { (_, dest) -> dest == "/app/data/nutritional-profiles/profile-a/label.jpg" }
        )
    }

    @Test
    fun `importData imports user profile settings`() = runTest {
        val appSettingsRepo = FakeAppSettingsRepository()
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            userProfile = ExportUserProfile(
                height = 175.0,
                heightUnit = "cm",
                sex = "Female",
                currentWeight = 65.0,
                weightUnit = "kg",
                dateOfBirth = "1990-01-15",
                activityLevel = "Active"
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)

        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
        }

        val service = createService(appSettingsRepo = appSettingsRepo, fileSystem = fileSystem, zipUtil = zipUtil)

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertEquals(175.0, appSettingsRepo.getHeight())
        assertEquals("Female", appSettingsRepo.getSex())
        assertEquals(65.0, appSettingsRepo.getCurrentWeight())
        assertEquals("kg", appSettingsRepo.getWeightUnit())
        assertEquals("1990-01-15", appSettingsRepo.getDateOfBirth())
        assertEquals("Active", appSettingsRepo.getActivityLevel())
    }

    @Test
    fun `importData handles pre-extracted directory`() = runTest {
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(version = 1, exportedAt = "2026-02-11T09:00:00Z")
        val jsonString = json.encodeToString(ExportData.serializer(), exportData)

        val extractedDir = "/app/cache/already_extracted"
        fileSystem.directories.add(extractedDir)
        fileSystem.files["$extractedDir/data.json"] = jsonString.encodeToByteArray()

        val service = createService(fileSystem = fileSystem, zipUtil = zipUtil)

        val result = service.importData(extractedDir)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `importData updates existing weekly summary instead of inserting`() = runTest {
        val weeklySummaryRepo = FakeWeeklySummaryRepository()
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        weeklySummaryRepo.summaries.add(
            WeeklySummary(
                summaryId = 99,
                weekStartDate = LocalDate.parse("2026-02-10"),
                highlights = "Old highlights"
            )
        )

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            weeklySummaries = listOf(
                ExportWeeklySummary(
                    summaryId = 1,
                    weekStartDate = "2026-02-10",
                    highlights = "New highlights"
                )
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
        }

        val service = createService(weeklySummaryRepo = weeklySummaryRepo, fileSystem = fileSystem, zipUtil = zipUtil)

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertEquals(1, result.weeklySummariesImported)
        assertEquals(1, weeklySummaryRepo.updated.size)
        assertEquals(0, weeklySummaryRepo.inserted.size)
        assertEquals(99, weeklySummaryRepo.updated[0].summaryId)
    }

    @Test
    fun `importData copies image files to app data directory`() = runTest {
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(version = 1, exportedAt = "2026-02-11T09:00:00Z")
        val jsonString = json.encodeToString(ExportData.serializer(), exportData)

        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
            fileSystem.files["$destDir/photos/meal.jpg"] = byteArrayOf(1, 2, 3)
        }

        val service = createService(fileSystem = fileSystem, zipUtil = zipUtil)

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertTrue(fileSystem.copiedFiles.any { (_, dest) -> dest.contains("photos") && dest.contains("meal.jpg") })
    }

    @Test
    fun `importData resolves relative blob paths to absolute`() = runTest {
        val trackedEntryRepo = FakeTrackedEntryRepository()
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            entries = listOf(
                ExportTrackedEntry(
                    entryId = 1,
                    entryType = EntryType.MEAL,
                    capturedAt = "2026-02-11T09:00:00Z",
                    blobPath = "photos/meal.jpg",
                    dataPayload = "{}"
                )
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
        }

        val service = createService(
            trackedEntryRepo = trackedEntryRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertEquals("/app/data/photos/meal.jpg", trackedEntryRepo.upserted[0].blobPath)
    }

    @Test
    fun `importData preserves backward compatibility when nutritional profiles are absent`() = runTest {
        val trackedEntryRepo = FakeTrackedEntryRepository()
        val nutritionalProfileRepo = FakeNutritionalProfileRepository()
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            entries = listOf(
                ExportTrackedEntry(
                    entryId = 1,
                    entryType = EntryType.MEAL,
                    capturedAt = "2026-02-11T09:00:00Z",
                    dataPayload = "{}"
                )
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
        }

        val service = createService(
            trackedEntryRepo = trackedEntryRepo,
            nutritionalProfileRepo = nutritionalProfileRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        val result = service.importData("/path/to/import.zip")

        assertTrue(result.isSuccess)
        assertEquals(1, result.entriesImported)
        assertEquals(1, trackedEntryRepo.upserted.size)
        assertEquals(0, result.nutritionalProfilesImported)
        assertTrue(nutritionalProfileRepo.upserted.isEmpty())
    }

    @Test
    fun `importData continues after nutritional profile errors`() = runTest {
        val nutritionalProfileRepo = FakeNutritionalProfileRepository().apply {
            failOnExternalIds.add("bad-profile")
        }
        val fileSystem = FakeFileSystem()
        val zipUtil = FakeZipUtil()

        val exportData = ExportData(
            version = 1,
            exportedAt = "2026-02-11T09:00:00Z",
            nutritionalProfiles = listOf(
                ExportNutritionalProfile(
                    profileId = 1,
                    externalId = "bad-profile",
                    primaryName = "Bad",
                    createdAt = "2026-02-11T09:00:00Z",
                    updatedAt = "2026-02-11T09:00:00Z"
                ),
                ExportNutritionalProfile(
                    profileId = 2,
                    externalId = "good-profile",
                    primaryName = "Good",
                    createdAt = "2026-02-11T09:00:00Z",
                    updatedAt = "2026-02-11T09:00:00Z"
                )
            )
        )

        val jsonString = json.encodeToString(ExportData.serializer(), exportData)
        zipUtil.onExtract = { _, destDir ->
            fileSystem.directories.add(destDir)
            fileSystem.files["$destDir/data.json"] = jsonString.encodeToByteArray()
        }

        val service = createService(
            nutritionalProfileRepo = nutritionalProfileRepo,
            fileSystem = fileSystem,
            zipUtil = zipUtil
        )

        val result = service.importData("/path/to/import.zip")

        assertTrue(!result.isSuccess)
        assertEquals(1, result.nutritionalProfilesImported)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().contains("bad-profile"))
        assertEquals(listOf("good-profile"), nutritionalProfileRepo.upserted.map { it.externalId })
    }

    // endregion

    // region -- ImportResult --

    @Test
    fun `ImportResult isSuccess returns true when no errors`() {
        val result = ImportResult(entriesImported = 5)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ImportResult isSuccess returns false when errors present`() {
        val result = ImportResult(errors = listOf("something failed"))
        assertTrue(!result.isSuccess)
    }

    // endregion
}
