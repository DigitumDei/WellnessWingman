package com.wellnesswingman.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.ui.screens.weighthistory.WeightHistoryScreen

class UserProfileSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<SettingsViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(uiState.saveSuccess) {
            if (uiState.saveSuccess) {
                snackbarHostState.showSnackbar("Profile saved successfully")
                viewModel.clearSaveSuccess()
            }
        }

        LaunchedEffect(uiState.error) {
            uiState.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("User Profile") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Your profile helps personalize health recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Height
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.height,
                        onValueChange = { viewModel.updateHeight(it) },
                        label = { Text("Height") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    UnitToggleButton(
                        options = listOf("cm", "in"),
                        selected = uiState.heightUnit,
                        onSelect = { viewModel.updateHeightUnit(it) }
                    )
                }

                // Sex
                SexDropdown(
                    value = uiState.sex,
                    onValueChange = { viewModel.updateSex(it) }
                )

                // Weight
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.currentWeight,
                        onValueChange = { viewModel.updateCurrentWeight(it) },
                        label = { Text("Current Weight") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    UnitToggleButton(
                        options = listOf("kg", "lbs"),
                        selected = uiState.weightUnit,
                        onSelect = { viewModel.updateWeightUnit(it) }
                    )
                }

                // Date of Birth
                val dobIsValid = uiState.dateOfBirth.isBlank() || try {
                    kotlinx.datetime.LocalDate.parse(uiState.dateOfBirth)
                    true
                } catch (_: Exception) { false }
                OutlinedTextField(
                    value = uiState.dateOfBirth,
                    onValueChange = { viewModel.updateDateOfBirth(it) },
                    label = { Text("Date of Birth") },
                    placeholder = { Text("YYYY-MM-DD") },
                    isError = !dobIsValid,
                    supportingText = if (!dobIsValid) {{ Text("Use YYYY-MM-DD format") }} else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Activity Level
                ActivityLevelDropdown(
                    value = uiState.activityLevel,
                    onValueChange = { viewModel.updateActivityLevel(it) }
                )

                // View Weight History button
                OutlinedButton(
                    onClick = { navigator.push(WeightHistoryScreen()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Weight History")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Save Button
                Button(
                    onClick = { viewModel.saveProfileSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Profile")
                }
            }
        }
    }
}
