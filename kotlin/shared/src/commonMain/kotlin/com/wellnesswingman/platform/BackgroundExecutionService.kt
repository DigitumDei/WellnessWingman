package com.wellnesswingman.platform

/**
 * Platform-specific service for managing background task execution.
 * Ensures long-running operations (like LLM analysis) complete even when
 * the app goes to the background or screen locks.
 */
interface BackgroundExecutionService {
    /**
     * Start a background task with the given name.
     * On Android: Starts foreground service with notification.
     * On iOS: Begins background task (implemented in separate issue).
     * On Desktop: No-op (no background restrictions).
     *
     * @param taskName A unique identifier for the task (e.g., "analyze-123")
     */
    fun startBackgroundTask(taskName: String)

    /**
     * Stop a background task with the given name.
     * On Android: Stops foreground service and dismisses notification.
     * On iOS: Ends background task (implemented in separate issue).
     * On Desktop: No-op (no background restrictions).
     *
     * @param taskName The unique identifier for the task to stop
     */
    fun stopBackgroundTask(taskName: String)
}
