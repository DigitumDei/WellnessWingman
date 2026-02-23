package com.wellnesswingman.domain.navigation

/**
 * Represents the different hierarchical levels in the historical view navigation.
 */
enum class HistoricalViewLevel {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
    DAY;

    /**
     * Get the route name for navigation.
     */
    val route: String
        get() = when (this) {
            TODAY -> "today"
            WEEK -> "week"
            MONTH -> "month"
            YEAR -> "year"
            DAY -> "day"
        }
}
