package com.wellnesswingman.ui.screens.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.ui.components.ErrorMessage
import com.wellnesswingman.ui.components.LoadingIndicator
import kotlinx.datetime.*

class YearViewScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<YearViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        val currentYear by viewModel.currentYear.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentYear.toString()) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.previousYear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Year")
                        }
                        IconButton(onClick = { viewModel.nextYear() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Year")
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (val state = uiState) {
                is YearUiState.Loading -> LoadingIndicator(Modifier.padding(paddingValues))
                is YearUiState.Success -> YearGrid(
                    year = state.year,
                    entriesByMonth = state.entriesByMonth,
                    onMonthClick = { month ->
                        val date = LocalDate(state.year, month, 1)
                        navigator.push(MonthViewScreen())
                        // Note: We'd need to pass the date to MonthViewScreen or update the ViewModel
                    },
                    modifier = Modifier.padding(paddingValues)
                )
                is YearUiState.Error -> ErrorMessage(
                    message = state.message,
                    onRetry = { viewModel.loadYear(currentYear) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun YearGrid(
    year: Int,
    entriesByMonth: Map<Month, Int>,
    onMonthClick: (Month) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(12) { index ->
            val month = Month.values()[index]
            val entryCount = entriesByMonth[month] ?: 0

            MonthCard(
                month = month,
                entryCount = entryCount,
                onClick = { onMonthClick(month) }
            )
        }
    }
}

@Composable
fun MonthCard(
    month: Month,
    entryCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = month.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$entryCount",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (entryCount == 1) "entry" else "entries",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
