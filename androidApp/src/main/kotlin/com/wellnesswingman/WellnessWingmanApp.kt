package com.wellnesswingman

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wellnesswingman.data.model.PolarOAuthConfig
import com.wellnesswingman.data.repository.AppSettingsRepository
import com.wellnesswingman.data.repository.PolarOAuthRepository
import com.wellnesswingman.di.getSharedModules
import com.wellnesswingman.di.platformModule
import com.wellnesswingman.domain.analysis.StaleEntryRecoveryService
import com.wellnesswingman.domain.oauth.PendingOAuthResultStore
import com.wellnesswingman.platform.LogBuffer
import com.wellnesswingman.ui.di.viewModelModule
import com.wellnesswingman.workers.ImageRetentionWorker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class WellnessWingmanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Napier logging with log buffer
        // LogBuffer handles both storing logs and outputting to Logcat
        val logBuffer = LogBuffer.getInstance()
        logBuffer.initPersistentLogging(filesDir)
        Napier.base(logBuffer)

        // Initialize Koin dependency injection
        val koinApp = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@WellnessWingmanApp)
            modules(
                getSharedModules() +
                platformModule +
                viewModelModule +
                module {
                    single {
                        PolarOAuthConfig(
                            clientId = BuildConfig.POLAR_CLIENT_ID,
                            brokerBaseUrl = BuildConfig.POLAR_BROKER_BASE_URL
                        )
                    }
                }
            )
        }

        // Reset any entries stuck in Processing from a previous crash/force-close
        CoroutineScope(Dispatchers.IO).launch {
            koinApp.koin.get<StaleEntryRecoveryService>().recoverStaleEntries()
        }

        // Recover pending OAuth session that survived process death
        val appSettings = koinApp.koin.get<AppSettingsRepository>()
        val pendingSessionId = appSettings.getPendingOAuthSessionId()
        val pendingState = appSettings.getPendingOAuthState()
        if (pendingSessionId != null && pendingState != null) {
            Napier.i("Recovering pending OAuth session from Settings: $pendingSessionId")
            val polarOAuthRepo = koinApp.koin.get<PolarOAuthRepository>()
            val pendingStore = koinApp.koin.get<PendingOAuthResultStore>()
            CoroutineScope(Dispatchers.IO).launch {
                val result = polarOAuthRepo.redeemSession(pendingSessionId, pendingState)
                result.fold(
                    onSuccess = { userId ->
                        appSettings.clearPendingOAuthSession()
                        Napier.i("OAuth session recovered successfully (userId=$userId)")
                        pendingStore.deliver(pendingSessionId, pendingState)
                    },
                    onFailure = { e ->
                        // Don't clear pending session on transient failures —
                        // it can be retried on next launch while the broker TTL is valid
                        Napier.e("OAuth session recovery failed", e)
                        pendingStore.deliverError(e.message ?: "Session recovery failed")
                    }
                )
            }
        }

        setupBackgroundJobs()

        Napier.i("WellnessWingman app initialized")
    }

    private fun setupBackgroundJobs() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val retentionWorkRequest = PeriodicWorkRequestBuilder<ImageRetentionWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ImageRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            retentionWorkRequest
        )
    }
}
