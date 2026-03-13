package com.wellnesswingman.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wellnesswingman.data.repository.LlmProvider

@Composable
fun UnitToggleButton(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option) },
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SexDropdown(
    value: String,
    onValueChange: (String) -> Unit
) {
    SettingsDropdown(
        label = "Sex",
        value = value,
        options = listOf("Male", "Female", "Other", "Prefer not to say"),
        onValueChange = onValueChange
    )
}

@Composable
fun ActivityLevelDropdown(
    value: String,
    onValueChange: (String) -> Unit
) {
    SettingsDropdown(
        label = "Activity Level",
        value = value,
        options = listOf(
            "Sedentary",
            "Lightly Active",
            "Moderately Active",
            "Very Active",
            "Extremely Active"
        ),
        onValueChange = onValueChange
    )
}

@Composable
fun ProviderSelector(
    selectedProvider: LlmProvider,
    onProviderSelected: (LlmProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        LlmProvider.entries.forEach { provider ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = selectedProvider == provider,
                    onClick = { onProviderSelected(provider) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (provider) {
                            LlmProvider.OPENAI -> "GPT-4o-mini with vision and Whisper"
                            LlmProvider.GEMINI -> "Gemini 1.5 Flash with vision"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
