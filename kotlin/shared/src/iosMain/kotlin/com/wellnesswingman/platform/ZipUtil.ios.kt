package com.wellnesswingman.platform

actual class ZipUtil actual constructor() {

    actual suspend fun createZip(zipPath: String, entries: List<ZipEntry>) {
        // iOS ZIP support is not yet implemented
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }

    actual suspend fun extractZip(zipPath: String, destDir: String) {
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }

    actual suspend fun readFileFromZip(zipPath: String, fileName: String): ByteArray? {
        throw UnsupportedOperationException("ZIP operations are not yet supported on iOS")
    }
}
