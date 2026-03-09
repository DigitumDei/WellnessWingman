package com.wellnesswingman.platform

data class ZipEntry(val name: String, val data: ByteArray)
data class ZipFileSource(val name: String, val path: String)

expect class ZipUtil() : ZipOperations {
    override suspend fun createZip(zipPath: String, entries: List<ZipEntry>)
    override suspend fun createZipWithFiles(zipPath: String, inMemoryEntries: List<ZipEntry>, fileEntries: List<ZipFileSource>)
    override suspend fun extractZip(zipPath: String, destDir: String)
    override suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray?
}
