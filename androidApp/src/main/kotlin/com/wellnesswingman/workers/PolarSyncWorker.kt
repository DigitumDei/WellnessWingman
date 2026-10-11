package com.wellnesswingman.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wellnesswingman.domain.polar.PolarSyncOrchestrator
import com.wellnesswingman.domain.polar.PolarSyncOutcome
import com.wellnesswingman.domain.polar.PolarSyncTrigger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PolarSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val polarSyncOrchestrator: PolarSyncOrchestrator by inject()

    override suspend fun doWork(): Result {
        return try {
            Napier.i("Starting background Polar sync job")
            val result = polarSyncOrchestrator.syncIfStale(PolarSyncTrigger.ANDROID_BACKGROUND)
            Napier.i("Finished background Polar sync job with outcome=${result.outcome}")
            when (result.outcome) {
                PolarSyncOutcome.PARTIAL_FAILURE -> Result.retry()
                PolarSyncOutcome.SUCCESS, PolarSyncOutcome.SKIPPED -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Background Polar sync job failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "PolarSyncJob"
    }
}
