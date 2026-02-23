package com.wellnesswingman.domain.navigation

import kotlinx.datetime.LocalDate

/**
 * Service for coordinating navigation between calendar views.
 * Handles swipe gesture navigation: Week ↔ Month ↔ Year
 */
class CalendarNavigationService(
    private val navigationContext: HistoricalNavigationContext
) {
    /**
     * Navigate to week view.
     * @param weekStart The starting date of the week
     * @param pushBreadcrumb Whether to push current state to breadcrumb stack
     */
    fun navigateToWeek(weekStart: LocalDate, pushBreadcrumb: Boolean = true): NavigationTarget {
        if (pushBreadcrumb) {
            navigationContext.pushBreadcrumb(navigationContext.createCurrentBreadcrumb())
        }
        navigationContext.setCurrent(HistoricalViewLevel.WEEK, weekStart)
        return NavigationTarget.Week(weekStart)
    }

    /**
     * Navigate to month view.
     * @param monthDate A date within the target month
     * @param pushBreadcrumb Whether to push current state to breadcrumb stack
     */
    fun navigateToMonth(monthDate: LocalDate, pushBreadcrumb: Boolean = true): NavigationTarget {
        if (pushBreadcrumb) {
            navigationContext.pushBreadcrumb(navigationContext.createCurrentBreadcrumb())
        }
        val firstOfMonth = LocalDate(monthDate.year, monthDate.monthNumber, 1)
        navigationContext.setCurrent(HistoricalViewLevel.MONTH, firstOfMonth)
        return NavigationTarget.Month(firstOfMonth)
    }

    /**
     * Navigate to year view.
     * @param year The target year
     * @param pushBreadcrumb Whether to push current state to breadcrumb stack
     */
    fun navigateToYear(year: Int, pushBreadcrumb: Boolean = true): NavigationTarget {
        if (pushBreadcrumb) {
            navigationContext.pushBreadcrumb(navigationContext.createCurrentBreadcrumb())
        }
        val firstOfYear = LocalDate(year, 1, 1)
        navigationContext.setCurrent(HistoricalViewLevel.YEAR, firstOfYear)
        return NavigationTarget.Year(year)
    }

    /**
     * Navigate to day detail view.
     * @param date The target date
     */
    fun navigateToDay(date: LocalDate): NavigationTarget {
        navigationContext.pushBreadcrumb(navigationContext.createCurrentBreadcrumb())
        navigationContext.setCurrent(HistoricalViewLevel.DAY, date)
        return NavigationTarget.Day(date)
    }

    /**
     * Navigate back using breadcrumb stack.
     * @return The navigation target to return to, or null if at root
     */
    fun navigateBack(): NavigationTarget? {
        val breadcrumb = navigationContext.popBreadcrumb() ?: return null
        navigationContext.setCurrent(breadcrumb.level, breadcrumb.date)

        return when (breadcrumb.level) {
            HistoricalViewLevel.TODAY -> NavigationTarget.Today
            HistoricalViewLevel.WEEK -> NavigationTarget.Week(breadcrumb.date)
            HistoricalViewLevel.MONTH -> NavigationTarget.Month(breadcrumb.date)
            HistoricalViewLevel.YEAR -> NavigationTarget.Year(breadcrumb.date.year)
            HistoricalViewLevel.DAY -> NavigationTarget.Day(breadcrumb.date)
        }
    }

    /**
     * Navigate to today and clear breadcrumbs.
     */
    fun navigateToToday(): NavigationTarget {
        navigationContext.resetToToday()
        return NavigationTarget.Today
    }

    /**
     * Handle swipe left - go to higher level view.
     * Week → Month → Year
     */
    fun swipeLeft(): NavigationTarget? {
        return when (navigationContext.currentLevel) {
            HistoricalViewLevel.WEEK -> {
                val currentDate = navigationContext.currentDate
                navigateToMonth(currentDate, pushBreadcrumb = true)
            }
            HistoricalViewLevel.MONTH -> {
                val currentDate = navigationContext.currentDate
                navigateToYear(currentDate.year, pushBreadcrumb = true)
            }
            else -> null // Already at year or other level
        }
    }

    /**
     * Handle swipe right - go to lower level view or back.
     * Year → Month → Week, or use breadcrumbs
     */
    fun swipeRight(): NavigationTarget? {
        // First check breadcrumbs
        if (navigationContext.hasBreadcrumbs) {
            return navigateBack()
        }

        // Otherwise drill down based on current level
        return when (navigationContext.currentLevel) {
            HistoricalViewLevel.YEAR -> {
                val currentDate = navigationContext.currentDate
                navigateToMonth(currentDate, pushBreadcrumb = false)
            }
            HistoricalViewLevel.MONTH -> {
                val currentDate = navigationContext.currentDate
                navigateToWeek(currentDate, pushBreadcrumb = false)
            }
            else -> null // At week level or other
        }
    }

    /**
     * Check if navigation can go back.
     */
    fun canGoBack(): Boolean = navigationContext.hasBreadcrumbs

    /**
     * Get the current navigation level.
     */
    fun getCurrentLevel(): HistoricalViewLevel = navigationContext.currentLevel
}

/**
 * Represents a navigation target destination.
 */
sealed class NavigationTarget {
    object Today : NavigationTarget()
    data class Week(val weekStart: LocalDate) : NavigationTarget()
    data class Month(val monthDate: LocalDate) : NavigationTarget()
    data class Year(val year: Int) : NavigationTarget()
    data class Day(val date: LocalDate) : NavigationTarget()
}
