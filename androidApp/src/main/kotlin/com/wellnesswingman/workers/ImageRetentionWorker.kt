package com.wellnesswingman.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wellnesswingman.domain.media.ImageRetentionService
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ImageRetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val imageRetentionService: ImageRetentionService by inject()

    override suspend fun doWork(): Result {
        return try {
            Napier.i("Starting background image retention job")
            imageRetentionService.downsizeOldImages()
            Napier.i("Finished background image retention job successfully")
            Result.success()
        } catch (e: Exception) {
            Napier.e("Image retention job failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ImageRetentionJob"
    }
}
