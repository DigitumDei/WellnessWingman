package com.wellnesswingman.domain.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * State holder for navigation context.
 */
data class NavigationState(
    val currentLevel: HistoricalViewLevel = HistoricalViewLevel.TODAY,
    val currentDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val breadcrumbs: List<NavigationBreadcrumb> = emptyList()
) {
    val hasBreadcrumbs: Boolean get() = breadcrumbs.isNotEmpty()
}

/**
 * Tracks the currently displayed historical view and maintains the breadcrumb stack
 * used to restore the previous view when navigating backwards.
 */
class HistoricalNavigationContext {

    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    val currentLevel: HistoricalViewLevel get() = _state.value.currentLevel
    val currentDate: LocalDate get() = _state.value.currentDate
    val hasBreadcrumbs: Boolean get() = _state.value.hasBreadcrumbs

    /**
     * Creates a breadcrumb for the current state so the caller can push it before changing levels.
     */
    fun createCurrentBreadcrumb(label: String? = null): NavigationBreadcrumb {
        return NavigationBreadcrumb(
            level = currentLevel,
            date = currentDate,
            label = label ?: NavigationBreadcrumb(currentLevel, currentDate).label
        )
    }

    /**
     * Returns the most recent breadcrumb without modifying the stack.
     */
    fun peekBreadcrumb(): NavigationBreadcrumb? {
        return _state.value.breadcrumbs.lastOrNull()
    }

    /**
     * Pushes a breadcrumb representing the view the user is drilling down from.
     */
    fun pushBreadcrumb(breadcrumb: NavigationBreadcrumb) {
        _state.update { state ->
            state.copy(breadcrumbs = state.breadcrumbs + breadcrumb)
        }
    }

    /**
     * Pops the most recent breadcrumb when the user navigates back up the hierarchy.
     */
    fun popBreadcrumb(): NavigationBreadcrumb? {
        val breadcrumb = _state.value.breadcrumbs.lastOrNull() ?: return null
        _state.update { state ->
            state.copy(breadcrumbs = state.breadcrumbs.dropLast(1))
        }
        return breadcrumb
    }

    /**
     * Updates the current navigation state without touching the breadcrumb stack.
     */
    fun setCurrent(level: HistoricalViewLevel, date: LocalDate) {
        _state.update { state ->
            state.copy(currentLevel = level, currentDate = date)
        }
    }

    /**
     * Clears the breadcrumb stack and sets the current view to the supplied values.
     */
    fun reset(level: HistoricalViewLevel, date: LocalDate) {
        _state.update {
            NavigationState(
                currentLevel = level,
                currentDate = date,
                breadcrumbs = emptyList()
            )
        }
    }

    /**
     * Resets to today's view.
     */
    fun resetToToday() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        reset(HistoricalViewLevel.TODAY, today)
    }
}
