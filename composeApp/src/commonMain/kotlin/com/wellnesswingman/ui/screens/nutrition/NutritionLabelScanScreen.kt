package com.wellnesswingman.ui.screens.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.wellnesswingman.domain.analysis.ExtractedNutrition
import com.wellnesswingman.ui.screens.detail.ImageDisplay
import org.koin.core.parameter.parametersOf

data class NutritionLabelScanScreen(
    val profileId: Long? = null,
    val onSaved: (() -> Unit)? = null
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getScreenModel<NutritionLabelScanViewModel> { parametersOf(profileId) }
        val uiState by viewModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (profileId == null) "Scan Nutrition Label" else "Edit Nutrition Profile") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (uiState.isCapturing || uiState.isAnalyzing || uiState.isSaving) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            when {
                                uiState.isCapturing -> "Opening camera or gallery..."
                                uiState.isAnalyzing -> "Extracting nutrition facts..."
                                else -> "Saving profile..."
                            }
                        )
                    }
                }

                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (uiState.photoBytes != null) {
                            ImageDisplay(uiState.photoBytes)
                        } else {
                            Text("Capture or choose a nutrition label photo to begin.")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = viewModel::captureFromCamera, modifier = Modifier.weight(1f)) {
                                Text("Camera")
                            }
                            OutlinedButton(onClick = viewModel::pickFromGallery, modifier = Modifier.weight(1f)) {
                                Text("Gallery")
                            }
                        }

                        Button(
                            onClick = viewModel::analyzeImage,
                            enabled = uiState.photoBytes != null && !uiState.isAnalyzing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Extract Nutrition Facts")
                        }
                    }
                }

                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Profile Details", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = uiState.primaryName,
                            onValueChange = viewModel::updatePrimaryName,
                            label = { Text("Primary name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uiState.aliases,
                            onValueChange = viewModel::updateAliases,
                            label = { Text("Aliases (comma-separated)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uiState.servingSize,
                            onValueChange = viewModel::updateServingSize,
                            label = { Text("Serving size") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                uiState.extractionWarnings.takeIf { it.isNotEmpty() }?.let { warnings ->
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Extraction Notes", style = MaterialTheme.typography.titleMedium)
                            warnings.forEach { warning -> Text("• $warning") }
                        }
                    }
                }

                NutritionEditor(
                    nutrition = uiState.nutrition,
                    onValueChange = { field, value ->
                        viewModel.updateNutrition { current ->
                            when (field) {
                                "calories" -> current.copy(totalCalories = value.toDoubleOrNull())
                                "protein" -> current.copy(protein = value.toDoubleOrNull())
                                "carbohydrates" -> current.copy(carbohydrates = value.toDoubleOrNull())
                                "fat" -> current.copy(fat = value.toDoubleOrNull())
                                "fiber" -> current.copy(fiber = value.toDoubleOrNull())
                                "sugar" -> current.copy(sugar = value.toDoubleOrNull())
                                "sodium" -> current.copy(sodium = value.toDoubleOrNull())
                                "saturatedFat" -> current.copy(saturatedFat = value.toDoubleOrNull())
                                "transFat" -> current.copy(transFat = value.toDoubleOrNull())
                                else -> current.copy(cholesterol = value.toDoubleOrNull())
                            }
                        }
                    }
                )

                Button(
                    onClick = {
                        viewModel.save {
                            onSaved?.invoke()
                            navigator.pop()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    Text(if (profileId == null) "Save Profile" else "Save Changes")
                }
            }
        }
    }
}

@Composable
private fun NutritionEditor(
    nutrition: ExtractedNutrition,
    onValueChange: (String, String) -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nutrition Facts", style = MaterialTheme.typography.titleMedium)
            NumericField("Calories (kcal)", nutrition.totalCalories, "calories", onValueChange)
            NumericField("Protein (g)", nutrition.protein, "protein", onValueChange)
            NumericField("Carbohydrates (g)", nutrition.carbohydrates, "carbohydrates", onValueChange)
            NumericField("Fat (g)", nutrition.fat, "fat", onValueChange)
            NumericField("Fiber (g)", nutrition.fiber, "fiber", onValueChange)
            NumericField("Sugar (g)", nutrition.sugar, "sugar", onValueChange)
            NumericField("Sodium (mg)", nutrition.sodium, "sodium", onValueChange)
            NumericField("Saturated Fat (g)", nutrition.saturatedFat, "saturatedFat", onValueChange)
            NumericField("Trans Fat (g)", nutrition.transFat, "transFat", onValueChange)
            NumericField("Cholesterol (mg)", nutrition.cholesterol, "cholesterol", onValueChange)
        }
    }
}

@Composable
private fun NumericField(
    label: String,
    value: Double?,
    field: String,
    onValueChange: (String, String) -> Unit
) {
    var text by remember(field) { mutableStateOf(value?.toString().orEmpty()) }

    LaunchedEffect(value) {
        val normalizedValue = value?.toString().orEmpty()
        if (text.toDoubleOrNull() != value) {
            text = normalizedValue
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            if (it.isEmpty() || it.matches(Regex("""\d*\.?\d*"""))) {
                text = it
                onValueChange(field, it)
            }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
