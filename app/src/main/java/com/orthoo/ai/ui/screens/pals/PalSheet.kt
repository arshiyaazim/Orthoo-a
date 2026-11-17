package com.orthoo.ai.ui.screens.pals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.PAL_COLOR_PAIRS
import com.orthoo.ai.data.model.PalParameterSchema
import com.orthoo.ai.data.model.PalType
import com.orthoo.ai.ui.viewmodel.PalsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PalSheet(
    viewModel: PalsViewModel,
    existingPal: PalEntity?,
    models: List<ModelEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existingPal?.name.orEmpty()) }
    var description by remember { mutableStateOf(existingPal?.description.orEmpty()) }
    var palType by remember {
        mutableStateOf(
            existingPal?.palType?.let { runCatching { PalType.valueOf(it) }.getOrNull() } ?: PalType.ASSISTANT
        )
    }
    var systemPrompt by remember {
        mutableStateOf(
            existingPal?.systemPrompt ?: "You are a helpful, accurate AI assistant."
        )
    }
    var greetingText by remember { mutableStateOf(existingPal?.greetingText.orEmpty()) }
    var suggestedPromptsRaw by remember {
        mutableStateOf(
            existingPal?.let { viewModel.decodeSuggestedPrompts(it).joinToString("\n") }.orEmpty()
        )
    }
    var defaultModelId by remember { mutableStateOf(existingPal?.defaultModelId) }
    var primaryHex by remember { mutableLongStateOf(existingPal?.colorPrimaryHex ?: PAL_COLOR_PAIRS.first().primaryHex) }
    var secondaryHex by remember { mutableLongStateOf(existingPal?.colorSecondaryHex ?: PAL_COLOR_PAIRS.first().secondaryHex) }

    val initialTalents = remember(existingPal) {
        existingPal?.let { viewModel.decodeTalents(it).toSet() } ?: setOf("calculate", "datetime")
    }
    var selectedTalents by remember { mutableStateOf(initialTalents) }

    val defaultSchema = remember(existingPal) {
        existingPal?.let { viewModel.decodeParameterSchema(it) }.orEmpty().ifEmpty {
            listOf(
                PalParameterSchema("world", "World / Setting", "e.g., Cyberpunk metropolis", false),
                PalParameterSchema("location", "Location", "e.g., Neon Rooftop", false),
                PalParameterSchema("aiRole", "Character Role", "e.g., Veteran courier", false)
            )
        }
    }
    val parameterValues = remember(existingPal) {
        mutableStateMapOf<String, String>().apply {
            existingPal?.let { putAll(viewModel.decodeParameters(it)) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (existingPal == null) "Create Pal" else "Edit Pal",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PalType.entries.forEachIndexed { idx, type ->
                    SegmentedButton(
                        selected = palType == type,
                        onClick = {
                            palType = type
                            if (type == PalType.ROLEPLAY && !systemPrompt.contains("{{aiRole}}")) {
                                systemPrompt = "Stay in character as {{aiRole}} in {{world}} at {{location}}."
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(idx, PalType.entries.size)
                    ) {
                        Text(type.label)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pal Name *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pal_name_input")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Short Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt (supports {{key}} variables)") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pal_system_prompt_input")
            )

            if (palType == PalType.ROLEPLAY) {
                Spacer(Modifier.height(12.dp))
                Text("Roleplay Variables", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                defaultSchema.forEach { schema ->
                    OutlinedTextField(
                        value = parameterValues[schema.key].orEmpty(),
                        onValueChange = { parameterValues[schema.key] = it },
                        label = { Text("${schema.label} ({{${schema.key}}})") },
                        placeholder = { Text(schema.placeholder) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Color Theme", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PAL_COLOR_PAIRS.forEach { pair ->
                    val selected = pair.primaryHex == primaryHex
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(pair.primaryHex))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable {
                                primaryHex = pair.primaryHex
                                secondaryHex = pair.secondaryHex
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = "Selected color", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Talents (Tools)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            viewModel.availableTalents.forEach { talent ->
                val enabled = talent.metadata.name in selectedTalents
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(talent.metadata.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                talent.metadata.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                selectedTalents = if (checked) {
                                    selectedTalents + talent.metadata.name
                                } else {
                                    selectedTalents - talent.metadata.name
                                }
                            }
                        )
                    }
                }
            }

            val readyModels = models.filter { it.isDownloaded }
            if (readyModels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Default Model (optional)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = defaultModelId == null,
                        onClick = { defaultModelId = null },
                        label = { Text("Any active") }
                    )
                    readyModels.forEach { m ->
                        FilterChip(
                            selected = defaultModelId == m.id,
                            onClick = { defaultModelId = m.id },
                            label = { Text(m.name) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = greetingText,
                onValueChange = { greetingText = it },
                label = { Text("Greeting Message (shown in empty chat)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = suggestedPromptsRaw,
                onValueChange = { suggestedPromptsRaw = it },
                label = { Text("Suggested Prompts (one per line)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.savePal(
                            id = existingPal?.id,
                            name = name.trim().ifEmpty { "Custom Pal" },
                            description = description,
                            palType = palType,
                            systemPrompt = systemPrompt,
                            defaultModelId = defaultModelId,
                            colorPrimaryHex = primaryHex,
                            colorSecondaryHex = secondaryHex,
                            greetingText = greetingText,
                            suggestedPrompts = suggestedPromptsRaw.lines().map { it.trim() }.filter { it.isNotEmpty() },
                            talents = selectedTalents.toList(),
                            parameters = parameterValues.toMap(),
                            parameterSchema = if (palType == PalType.ROLEPLAY) defaultSchema else emptyList(),
                            completionParams = existingPal?.let { viewModel.decodeCompletionParams(it) }
                        )
                        onDismiss()
                    },
                    enabled = name.isNotBlank() || existingPal != null,
                    modifier = Modifier.testTag("save_pal_button")
                ) {
                    Text("Save Pal")
                }
            }
        }
    }
}
