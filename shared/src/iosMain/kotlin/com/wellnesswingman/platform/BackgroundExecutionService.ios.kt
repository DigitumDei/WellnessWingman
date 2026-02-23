package com.wellnesswingman.platform

import io.github.aakira.napier.Napier

/**
 * iOS implementation of BackgroundExecutionService.
 * TODO: Implement using iOS Background Task API (Issue #34).
 * Currently a stub that logs but does nothing.
 */
class IosBackgroundExecutionService : BackgroundExecutionService {

    override fun startBackgroundTask(taskName: String) {
        // TODO: Implement iOS background task API
        // See: https://developer.apple.com/documentation/backgroundtasks
        Napier.w("iOS background task not yet implemented for: $taskName")
    }

    override fun stopBackgroundTask(taskName: String) {
        // TODO: Implement iOS background task API
        Napier.w("iOS background task stop not yet implemented for: $taskName")
    }
}
