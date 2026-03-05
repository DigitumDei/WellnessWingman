package com.wellnesswingman.platform

actual class ZipUtil actual constructor() : ZipOperations {

    actual override suspend fun createZip(zipPath: String, entries: List<ZipEntry>) {
        // iOS ZIP support is not yet implemented
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }

    actual override suspend fun createZipWithFiles(
        zipPath: String,
        inMemoryEntries: List<ZipEntry>,
        fileEntries: List<ZipFileSource>
    ) {
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }

    actual override suspend fun extractZip(zipPath: String, destDir: String) {
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }

    actual override suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray? {
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }
}
