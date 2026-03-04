package com.wellnesswingman.platform

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry as JavaZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual class ZipUtil actual constructor() {

    actual suspend fun createZip(zipPath: String, entries: List<ZipEntry>) = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        zipFile.parentFile?.mkdirs()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            for (entry in entries) {
                zos.putNextEntry(JavaZipEntry(entry.name))
                zos.write(entry.data)
                zos.closeEntry()
            }
        }
    }

    actual suspend fun createZipWithFiles(
        zipPath: String,
        inMemoryEntries: List<ZipEntry>,
        fileEntries: List<ZipFileSource>
    ) = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        zipFile.parentFile?.mkdirs()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            for (entry in inMemoryEntries) {
                zos.putNextEntry(JavaZipEntry(entry.name))
                zos.write(entry.data)
                zos.closeEntry()
            }
            for (source in fileEntries) {
                val file = File(source.path)
                if (!file.exists()) continue
                zos.putNextEntry(JavaZipEntry(source.name))
                BufferedInputStream(FileInputStream(file)).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    actual suspend fun extractZip(zipPath: String, destDir: String) = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            throw java.io.FileNotFoundException("ZIP file not found: $zipPath")
        }
        if (zipFile.length() == 0L) {
            throw java.util.zip.ZipException("ZIP file is empty: $zipPath")
        }

        val destDirectory = File(destDir)
        destDirectory.mkdirs()
        val canonicalDestDir = destDirectory.canonicalPath

        // We use ZipInputStream instead of ZipFile to be more resilient to truncated or 
        // slightly corrupted archives (e.g., missing the END header/EOCD record).
        // ZipFile is more strict and requires a valid Central Directory at the end of the file.
        try {
            FileInputStream(zipFile).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val destFile = File(destDirectory, entry.name)

                            // Protect against zip slip
                            val canonicalDestFile = destFile.canonicalPath
                            if (!canonicalDestFile.startsWith(canonicalDestDir + File.separator)) {
                                throw SecurityException("Zip entry outside target dir: ${entry.name}")
                            }

                            if (entry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                FileOutputStream(destFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e("Error extracting ZIP: $zipPath (size: ${zipFile.length()})", e)
            throw e
        }
    }

    actual suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val zipFile = File(zipPath)
            if (!zipFile.exists() || zipFile.length() == 0L) return@withContext null

            // Try ZipFile first (faster for random access)
            try {
                ZipFile(zipFile).use { zip ->
                    val entry = zip.getEntry(fileName)
                    if (entry != null) {
                        return@withContext zip.getInputStream(entry).use { it.readBytes() }
                    }
                }
            } catch (e: Exception) {
                Napier.w("ZipFile failed for $fileName, falling back to ZipInputStream", e)
            }

            // Fallback to ZipInputStream (more resilient to corruption)
            try {
                FileInputStream(zipFile).use { fis ->
                    ZipInputStream(BufferedInputStream(fis)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == fileName) {
                                return@withContext zis.readBytes()
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("Failed to read $fileName from ZIP", e)
            }
            null
        }
}
