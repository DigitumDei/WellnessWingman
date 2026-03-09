package com.wellnesswingman.platform

interface ZipOperations {
    suspend fun createZip(zipPath: String, entries: List<ZipEntry>)
    suspend fun createZipWithFiles(zipPath: String, inMemoryEntries: List<ZipEntry>, fileEntries: List<ZipFileSource>)
    suspend fun extractZip(zipPath: String, destDir: String)
    suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray?
}
