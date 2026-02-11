package com.wellnesswingman.platform

data class ZipEntry(val name: String, val data: ByteArray)

expect class ZipUtil() {
    suspend fun createZip(zipPath: String, entries: List<ZipEntry>)
    suspend fun extractZip(zipPath: String, destDir: String)
    suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray?
}
