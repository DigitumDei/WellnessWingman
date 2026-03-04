package com.wellnesswingman.platform

data class ZipEntry(val name: String, val data: ByteArray)
data class ZipFileSource(val name: String, val path: String)

expect class ZipUtil() {
    suspend fun createZip(zipPath: String, entries: List<ZipEntry>)
    suspend fun createZipWithFiles(zipPath: String, inMemoryEntries: List<ZipEntry>, fileEntries: List<ZipFileSource>)
    suspend fun extractZip(zipPath: String, destDir: String)
    suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray?
}
