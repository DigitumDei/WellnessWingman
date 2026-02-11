package com.wellnesswingman.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry as JavaZipEntry
import java.util.zip.ZipFile
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

    actual suspend fun extractZip(zipPath: String, destDir: String) = withContext(Dispatchers.IO) {
        val destDirectory = File(destDir)
        destDirectory.mkdirs()

        ZipFile(zipPath).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val destFile = File(destDirectory, entry.name)
                // Protect against zip slip
                if (!destFile.canonicalPath.startsWith(destDirectory.canonicalPath)) {
                    throw SecurityException("Zip entry outside target dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    actual suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            ZipFile(zipPath).use { zip ->
                val entry = zip.getEntry(fileName) ?: return@withContext null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        }
}
