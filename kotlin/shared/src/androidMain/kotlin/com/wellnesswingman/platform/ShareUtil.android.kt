package com.wellnesswingman.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

actual class ShareUtil(private val context: Context) {

    actual fun shareFile(filePath: String, mimeType: String, title: String) {
        try {
            val file = File(filePath)
            val uri = Uri.fromFile(file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("ShareUtil", "Failed to share file", e)
        }
    }
}
