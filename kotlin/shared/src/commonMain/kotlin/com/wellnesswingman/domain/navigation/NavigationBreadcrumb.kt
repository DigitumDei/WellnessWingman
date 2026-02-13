package com.wellnesswingman.domain.navigation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/**
 * Represents a point in the navigation history that can be returned to.
 */
data class NavigationBreadcrumb(
    val level: HistoricalViewLevel,
    val date: LocalDate,
    val label: String = buildDefaultLabel(level, date)
) {
    /**
     * Route name for this breadcrumb.
     */
    val route: String = level.route

    /**
     * Builds navigation parameters for this breadcrumb.
     */
    fun buildNavigationParameters(): Map<String, Any> {
        val parameters = mutableMapOf<String, Any>()
        when (level) {
            HistoricalViewLevel.DAY -> {
                parameters["date"] = date.toString()
            }
            HistoricalViewLevel.WEEK -> {
                parameters["weekStart"] = date.toString()
            }
            HistoricalViewLevel.MONTH -> {
                parameters["year"] = date.year
                parameters["month"] = date.monthNumber
            }
            HistoricalViewLevel.YEAR -> {
                parameters["year"] = date.year
            }
            HistoricalViewLevel.TODAY -> {
                // No parameters needed
            }
        }
        return parameters
    }

    companion object {
        private fun buildDefaultLabel(level: HistoricalViewLevel, date: LocalDate): String {
            return when (level) {
                HistoricalViewLevel.TODAY -> "Today"
                HistoricalViewLevel.DAY -> formatDayLabel(date)
                HistoricalViewLevel.WEEK -> "Week of ${formatMonthDay(date)}"
                HistoricalViewLevel.MONTH -> "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.year}"
                HistoricalViewLevel.YEAR -> "${date.year}"
            }
        }

        private fun formatDayLabel(date: LocalDate): String {
            val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
            return "$monthName ${date.dayOfMonth}, ${date.year}"
        }

        private fun formatMonthDay(date: LocalDate): String {
            val monthAbbrev = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            return "$monthAbbrev ${date.dayOfMonth}"
        }
    }
}
