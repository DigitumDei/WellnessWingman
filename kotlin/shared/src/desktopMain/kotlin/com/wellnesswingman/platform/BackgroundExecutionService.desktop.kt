package com.wellnesswingman.platform

import io.github.aakira.napier.Napier

/**
 * Desktop implementation of BackgroundExecutionService.
 * No-op implementation since desktop has no background execution restrictions.
 */
class DesktopBackgroundExecutionService : BackgroundExecutionService {

    override fun startBackgroundTask(taskName: String) {
        // No-op on desktop - no background restrictions
        Napier.d("Desktop: startBackgroundTask($taskName) - no-op")
    }

    override fun stopBackgroundTask(taskName: String) {
        // No-op on desktop - no background restrictions
        Napier.d("Desktop: stopBackgroundTask($taskName) - no-op")
    }
}
