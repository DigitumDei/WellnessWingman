package com.wellnesswingman.domain.migration

import com.wellnesswingman.data.model.export.ExportData
import com.wellnesswingman.data.model.export.toDomain
import com.wellnesswingman.data.model.export.toExport
import com.wellnesswingman.data.repository.DailySummaryRepository
import com.wellnesswingman.data.repository.EntryAnalysisRepository
import com.wellnesswingman.data.repository.TrackedEntryRepository
import com.wellnesswingman.platform.FileSystem
import com.wellnesswingman.platform.ZipEntry
import com.wellnesswingman.platform.ZipUtil
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

interface DataMigrationService {
    suspend fun exportData(): String // returns ZIP file path
    suspend fun importData(zipFilePath: String): ImportResult
}

data class ImportResult(
    val entriesImported: Int = 0,
    val analysesImported: Int = 0,
    val summariesImported: Int = 0,
    val errors: List<String> = emptyList()
) {
    val isSuccess get() = errors.isEmpty()
}

class DefaultDataMigrationService(
    private val trackedEntryRepository: TrackedEntryRepository,
    private val entryAnalysisRepository: EntryAnalysisRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val fileSystem: FileSystem,
    private val zipUtil: ZipUtil
) : DataMigrationService {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun exportData(): String {
        Napier.i("Starting data export")

        // 1. Gather data
        val entries = trackedEntryRepository.getAllEntries()
        val analyses = entryAnalysisRepository.getAllAnalyses()
        val summaries = dailySummaryRepository.getAllSummaries()

        Napier.i("Exporting ${entries.size} entries, ${analyses.size} analyses, ${summaries.size} summaries")

        // 2. Build export model (relativize absolute paths for MAUI compatibility)
        val appDataDir = fileSystem.getAppDataDirectory()
        val exportData = ExportData(
            version = 1,
            exportedAt = Clock.System.now().toString(),
            entries = entries.map { entry ->
                val exported = entry.toExport()
                exported.copy(
                    blobPath = exported.blobPath?.let { relativizePath(it, appDataDir) },
                    dataPayload = relativizePayloadPaths(exported.dataPayload, appDataDir)
                )
            },
            analyses = analyses.map { it.toExport() },
            summaries = summaries.map { it.toExport() },
            summariesAnalyses = emptyList() // Kotlin doesn't have this junction table
        )

        // 3. Serialize to JSON
        val jsonString = json.encodeToString(ExportData.serializer(), exportData)
        val jsonBytes = jsonString.encodeToByteArray()

        // 4. Gather image files
        val zipEntries = mutableListOf<ZipEntry>()
        zipEntries.add(ZipEntry("data.json", jsonBytes))

        val appDataPrefix = appDataDir.replace('\\', '/').trimEnd('/') + "/"
        val exportedPaths = mutableSetOf<String>()

        for (entry in entries) {
            val imagePaths = enumerateImageAbsolutePaths(entry, appDataDir)
            for (absolutePath in imagePaths) {
                if (!fileSystem.exists(absolutePath)) continue

                // Convert absolute path to relative for the ZIP entry
                val normalizedAbsolute = absolutePath.replace('\\', '/')
                val relativePath = if (normalizedAbsolute.startsWith(appDataPrefix)) {
                    normalizedAbsolute.removePrefix(appDataPrefix)
                } else {
                    normalizedAbsolute.substringAfterLast('/')
                }

                if (relativePath in exportedPaths) continue

                try {
                    val bytes = fileSystem.readBytes(absolutePath)
                    zipEntries.add(ZipEntry(relativePath, bytes))
                    exportedPaths.add(relativePath)
                } catch (e: Exception) {
                    Napier.w("Failed to read image: $absolutePath", e)
                }
            }
        }

        // 5. Create ZIP
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = "${now.year}${now.monthNumber.toString().padStart(2, '0')}${now.dayOfMonth.toString().padStart(2, '0')}_${now.hour.toString().padStart(2, '0')}${now.minute.toString().padStart(2, '0')}${now.second.toString().padStart(2, '0')}"
        val fileName = "wellnesswingman_export_$timestamp.zip"
        val zipPath = "${fileSystem.getCacheDirectory()}${fileSeparator}$fileName"

        zipUtil.createZip(zipPath, zipEntries)

        Napier.i("Export completed: $zipPath (${zipEntries.size} files)")
        return zipPath
    }

    override suspend fun importData(zipFilePath: String): ImportResult {
        Napier.i("Starting data import from: $zipFilePath")
        val errors = mutableListOf<String>()

        // 1. Extract ZIP to temp dir
        val tempDir = "${fileSystem.getCacheDirectory()}${fileSeparator}import_temp_${Clock.System.now().toEpochMilliseconds()}"
        fileSystem.createDirectory(tempDir)

        try {
            zipUtil.extractZip(zipFilePath, tempDir)

            // 2. Read and parse data.json
            val jsonPath = "$tempDir${fileSeparator}data.json"
            if (!fileSystem.exists(jsonPath)) {
                return ImportResult(errors = listOf("data.json not found in import file"))
            }

            val jsonBytes = fileSystem.readBytes(jsonPath)
            val jsonString = jsonBytes.decodeToString()

            val exportData = try {
                json.decodeFromString(ExportData.serializer(), jsonString)
            } catch (e: Exception) {
                Napier.e("Failed to parse data.json", e)
                return ImportResult(errors = listOf("Failed to parse data.json: ${e.message}"))
            }

            Napier.i("Parsed: ${exportData.entries.size} entries, ${exportData.analyses.size} analyses, ${exportData.summaries.size} summaries")

            // 3. Copy images to app data directory
            val appDataDir = fileSystem.getAppDataDirectory()
            val allFiles = fileSystem.listFilesRecursively(tempDir)
            val normalizedTempDir = tempDir.replace('\\', '/')
            for (file in allFiles) {
                val normalizedFile = file.replace('\\', '/')
                val relativePath = normalizedFile.removePrefix(normalizedTempDir).removePrefix("/")
                if (relativePath.equals("data.json", ignoreCase = true)) continue

                val destPath = "$appDataDir${fileSeparator}${relativePath.replace("/", fileSeparator)}"
                try {
                    fileSystem.copyFile(file, destPath)
                } catch (e: Exception) {
                    Napier.w("Failed to copy image: $relativePath", e)
                    errors.add("Failed to copy image: $relativePath")
                }
            }

            // 4. Upsert entries (resolve relative paths to absolute)
            var entriesImported = 0
            for (exportEntry in exportData.entries) {
                try {
                    val domainEntry = exportEntry.toDomain()
                    // Convert MAUI-style relative paths to absolute paths
                    val resolvedEntry = domainEntry.copy(
                        blobPath = domainEntry.blobPath?.let { resolveImportPath(it, appDataDir) },
                        dataPayload = resolvePayloadPaths(domainEntry.dataPayload, appDataDir)
                    )
                    trackedEntryRepository.upsertEntry(resolvedEntry)
                    entriesImported++
                } catch (e: Exception) {
                    Napier.w("Failed to import entry ${exportEntry.entryId}", e)
                    errors.add("Failed to import entry ${exportEntry.entryId}: ${e.message}")
                }
            }

            // 5. Upsert analyses
            var analysesImported = 0
            for (exportAnalysis in exportData.analyses) {
                try {
                    val domainAnalysis = exportAnalysis.toDomain()
                    entryAnalysisRepository.upsertAnalysis(domainAnalysis)
                    analysesImported++
                } catch (e: Exception) {
                    Napier.w("Failed to import analysis ${exportAnalysis.analysisId}", e)
                    errors.add("Failed to import analysis ${exportAnalysis.analysisId}: ${e.message}")
                }
            }

            // 6. Upsert summaries
            var summariesImported = 0
            for (exportSummary in exportData.summaries) {
                try {
                    val domainSummary = exportSummary.toDomain()
                    dailySummaryRepository.upsertSummary(domainSummary)
                    summariesImported++
                } catch (e: Exception) {
                    Napier.w("Failed to import summary ${exportSummary.summaryId}", e)
                    errors.add("Failed to import summary ${exportSummary.summaryId}: ${e.message}")
                }
            }

            // SummariesAnalyses junction table: ignored (Kotlin doesn't have this)

            Napier.i("Import completed: $entriesImported entries, $analysesImported analyses, $summariesImported summaries")
            return ImportResult(
                entriesImported = entriesImported,
                analysesImported = analysesImported,
                summariesImported = summariesImported,
                errors = errors
            )
        } finally {
            // Cleanup temp dir
            try {
                cleanupDirectory(tempDir)
            } catch (e: Exception) {
                Napier.w("Failed to cleanup temp dir: $tempDir", e)
            }
        }
    }

    /**
     * Returns absolute file paths for all images referenced by this entry.
     * Handles both absolute paths (Kotlin-native) and relative paths (MAUI imports).
     */
    private fun enumerateImageAbsolutePaths(entry: com.wellnesswingman.data.model.TrackedEntry, appDataDir: String): List<String> {
        val rawPaths = mutableListOf<String>()

        // Add blob path
        entry.blobPath?.takeIf { it.isNotBlank() }?.let { rawPaths.add(it) }

        // Try to extract preview paths from payload JSON
        if (entry.dataPayload.isNotBlank()) {
            try {
                val payloadJson = Json.parseToJsonElement(entry.dataPayload)
                if (payloadJson is kotlinx.serialization.json.JsonObject) {
                    val keys = listOf("PreviewBlobPath", "previewBlobPath", "ScreenshotBlobPath", "screenshotBlobPath")
                    for (key in keys) {
                        payloadJson[key]?.let { elem ->
                            if (elem is kotlinx.serialization.json.JsonPrimitive && elem.isString) {
                                elem.content.takeIf { it.isNotBlank() }?.let { rawPaths.add(it) }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Not valid JSON or not an object, skip
            }
        }

        // Resolve any relative paths to absolute
        return rawPaths.map { resolveImportPath(it, appDataDir) }
    }

    /**
     * Resolves a potentially relative path to an absolute path.
     * MAUI exports store relative paths (e.g. "Entries/Meal/guid.jpg")
     * while the Kotlin app expects absolute paths.
     */
    private fun resolveImportPath(path: String, appDataDir: String): String {
        if (path.isBlank()) return path
        // Already absolute (starts with / on Unix or drive letter on Windows)
        if (path.startsWith("/") || (path.length >= 2 && path[1] == ':')) return path
        return "$appDataDir/$path"
    }

    /**
     * Converts an absolute path to a relative path for export.
     * Strips the appDataDir prefix so MAUI can read Kotlin exports.
     */
    private fun relativizePath(path: String, appDataDir: String): String {
        if (path.isBlank()) return path
        val normalizedPath = path.replace('\\', '/')
        val normalizedPrefix = appDataDir.replace('\\', '/').trimEnd('/') + "/"
        return if (normalizedPath.startsWith(normalizedPrefix)) {
            normalizedPath.removePrefix(normalizedPrefix)
        } else {
            path
        }
    }

    /**
     * Relativizes blob paths inside a DataPayload JSON string for export.
     */
    private fun relativizePayloadPaths(payload: String, appDataDir: String): String {
        if (payload.isBlank()) return payload
        try {
            val element = Json.parseToJsonElement(payload)
            if (element !is kotlinx.serialization.json.JsonObject) return payload

            val pathKeys = listOf("PreviewBlobPath", "previewBlobPath", "ScreenshotBlobPath", "screenshotBlobPath")
            var modified = false
            val mutableMap = element.toMutableMap()

            for (key in pathKeys) {
                val value = mutableMap[key]
                if (value is kotlinx.serialization.json.JsonPrimitive && value.isString && value.content.isNotBlank()) {
                    val relativized = relativizePath(value.content, appDataDir)
                    if (relativized != value.content) {
                        mutableMap[key] = kotlinx.serialization.json.JsonPrimitive(relativized)
                        modified = true
                    }
                }
            }

            return if (modified) {
                Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(mutableMap))
            } else {
                payload
            }
        } catch (_: Exception) {
            return payload
        }
    }

    /**
     * Resolves blob paths inside a DataPayload JSON string.
     * Handles both PascalCase (MAUI) and camelCase (Kotlin) keys.
     */
    private fun resolvePayloadPaths(payload: String, appDataDir: String): String {
        if (payload.isBlank()) return payload
        try {
            val element = Json.parseToJsonElement(payload)
            if (element !is kotlinx.serialization.json.JsonObject) return payload

            val pathKeys = listOf("PreviewBlobPath", "previewBlobPath", "ScreenshotBlobPath", "screenshotBlobPath")
            var modified = false
            val mutableMap = element.toMutableMap()

            for (key in pathKeys) {
                val value = mutableMap[key]
                if (value is kotlinx.serialization.json.JsonPrimitive && value.isString && value.content.isNotBlank()) {
                    val resolved = resolveImportPath(value.content, appDataDir)
                    if (resolved != value.content) {
                        mutableMap[key] = kotlinx.serialization.json.JsonPrimitive(resolved)
                        modified = true
                    }
                }
            }

            return if (modified) {
                Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(mutableMap))
            } else {
                payload
            }
        } catch (_: Exception) {
            return payload
        }
    }

    private fun kotlinx.serialization.json.JsonObject.toMutableMap(): MutableMap<String, kotlinx.serialization.json.JsonElement> {
        return this.toMap().toMutableMap()
    }

    private suspend fun cleanupDirectory(path: String) {
        val files = fileSystem.listFilesRecursively(path)
        for (file in files) {
            fileSystem.delete(file)
        }
        fileSystem.delete(path)
    }

    companion object {
        // Use forward slash as default; platform file operations handle conversion
        private const val fileSeparator = "/"
    }
}
