package com.orthoo.ai.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.ModelOrigin
import com.orthoo.ai.services.agent.AgentRunState
import com.orthoo.ai.services.speech.SpeechListeningState
import com.orthoo.ai.ui.components.Formatters
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPalModelPickerSheet(
    models: List<ModelEntity>,
    pals: List<PalEntity>,
    activeModelId: String?,
    activePalId: String?,
    initialTab: Int = 0,
    onSelectModel: (String) -> Unit,
    onSelectPal: (String?) -> Unit,
    onOpenModelsScreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tabIndex by remember(initialTab) { mutableIntStateOf(initialTab) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Models") },
                    modifier = Modifier.testTag("picker_tab_models")
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Pals") },
                    modifier = Modifier.testTag("picker_tab_pals")
                )
            }

            if (tabIndex == 0) {
                val readyModels = models.filter { it.isDownloaded }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (readyModels.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "No models ready yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Download a GGUF model or connect Ollama / LM Studio / Hugging Face Router.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenModelsScreen()
                                    },
                                    modifier = Modifier.testTag("picker_open_models_button")
                                ) {
                                    Text("Open Models Hub")
                                }
                            }
                        }
                    } else {
                        items(readyModels, key = { it.id }) { model ->
                            val selected = model.id == activeModelId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectModel(model.id)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (model.origin == ModelOrigin.REMOTE.name) Icons.Outlined.CloudQueue else Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        Text(model.name, style = MaterialTheme.typography.titleMedium)
                                        val meta = buildString {
                                            append(model.author)
                                            if (model.sizeBytes > 0L) append(" • ${Formatters.formatBytes(model.sizeBytes)}")
                                            if (model.supportsThinking) append(" • Thinking")
                                        }
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selected) {
                                        Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        item {
                            TextButton(
                                onClick = {
                                    onDismiss()
                                    onOpenModelsScreen()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Manage Models & Remote Servers")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPal(null)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (activePalId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (activePalId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("No Pal (Default)", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Direct conversation without persona instructions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (activePalId == null) {
                                    Icon(Icons.Outlined.Check, contentDescription = "Selected")
                                }
                            }
                        }
                    }
                    items(pals, key = { it.id }) { pal ->
                        val selected = pal.id == activePalId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPal(pal.id)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(pal.colorPrimaryHex))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(pal.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        pal.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerationSettingsSheet(
    initialParams: CompletionParams,
    onSave: (CompletionParams, applyGlobally: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nPredict by remember { mutableFloatStateOf(initialParams.nPredict.toFloat()) }
    var temperature by remember { mutableFloatStateOf(initialParams.temperature) }
    var topP by remember { mutableFloatStateOf(initialParams.topP) }
    var topK by remember { mutableFloatStateOf(initialParams.topK.toFloat()) }
    var minP by remember { mutableFloatStateOf(initialParams.minP) }
    var repeatPenalty by remember { mutableFloatStateOf(initialParams.penaltyRepeat) }
    var includeThinkingInCtx by remember { mutableStateOf(initialParams.includeThinkingInContext) }
    var applyGlobally by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Generation Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))

            SliderSettingRow("Max Tokens (n_predict)", nPredict.roundToInt().toString(), nPredict, 128f..4096f) {
                nPredict = it
            }
            SliderSettingRow("Temperature", String.format(Locale.US, "%.2f", temperature), temperature, 0f..2f) {
                temperature = it
            }
            SliderSettingRow("Top-P", String.format(Locale.US, "%.2f", topP), topP, 0.05f..1f) {
                topP = it
            }
            SliderSettingRow("Top-K", topK.roundToInt().toString(), topK, 1f..100f) {
                topK = it
            }
            SliderSettingRow("Min-P", String.format(Locale.US, "%.2f", minP), minP, 0f..0.5f) {
                minP = it
            }
            SliderSettingRow("Repeat Penalty", String.format(Locale.US, "%.2f", repeatPenalty), repeatPenalty, 1.0f..1.5f) {
                repeatPenalty = it
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include thinking in context", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Keep prior <think> reasoning traces in multi-turn prompts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = includeThinkingInCtx, onCheckedChange = { includeThinkingInCtx = it })
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Also save as global defaults", style = MaterialTheme.typography.titleSmall)
                Switch(checked = applyGlobally, onCheckedChange = { applyGlobally = it })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        val def = CompletionParams()
                        nPredict = def.nPredict.toFloat()
                        temperature = def.temperature
                        topP = def.topP
                        topK = def.topK.toFloat()
                        minP = def.minP
                        repeatPenalty = def.penaltyRepeat
                        includeThinkingInCtx = def.includeThinkingInContext
                    }
                ) { Text("Reset") }

                Button(
                    onClick = {
                        onSave(
                            initialParams.copy(
                                nPredict = nPredict.roundToInt(),
                                temperature = temperature,
                                topP = topP,
                                topK = topK.roundToInt(),
                                minP = minP,
                                penaltyRepeat = repeatPenalty,
                                includeThinkingInContext = includeThinkingInCtx
                            ),
                            applyGlobally
                        )
                        onDismiss()
                    },
                    modifier = Modifier.testTag("save_generation_settings_button")
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncreaseContextSheet(
    currentContextSize: Int,
    onSelectContextSize: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = listOf(2048, 4096, 8192, 16384, 32768)
    var selected by remember { mutableIntStateOf(currentContextSize) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Context Window (n_ctx)", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose a larger context window to fit longer conversations and documents in memory.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { size ->
                    FilterChip(
                        selected = selected == size,
                        onClick = { selected = size },
                        label = { Text("${size / 1024}K") }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSelectContextSize(selected)
                        onDismiss()
                    }
                ) { Text("Update Context") }
            }
        }
    }
}

@Composable
fun VoiceModeDialog(
    listeningState: SpeechListeningState,
    partialTranscript: String,
    isSpeaking: Boolean,
    agentState: AgentRunState,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onStopSpeaking: () -> Unit,
    onStopGeneration: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Voice Mode", style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_voice_mode_button")) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close Voice Mode")
                    }
                }
                Spacer(Modifier.height(24.dp))

                Surface(
                    modifier = Modifier
                        .size(116.dp)
                        .clickable {
                            when {
                                isSpeaking -> onStopSpeaking()
                                listeningState == SpeechListeningState.LISTENING -> onStopListening()
                                else -> onStartListening()
                            }
                        }
                        .testTag("voice_mode_orb_button"),
                    shape = CircleShape,
                    color = when {
                        listeningState == SpeechListeningState.LISTENING -> MaterialTheme.colorScheme.secondary
                        isSpeaking -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isSpeaking -> Icons.Outlined.GraphicEq
                                listeningState == SpeechListeningState.LISTENING -> Icons.Outlined.Stop
                                else -> Icons.Outlined.Mic
                            },
                            contentDescription = "Toggle voice interaction",
                            modifier = Modifier.size(44.dp),
                            tint = if (listeningState == SpeechListeningState.LISTENING || isSpeaking) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                val statusText = when {
                    listeningState == SpeechListeningState.LISTENING -> "Listening…"
                    isSpeaking -> "Speaking response…"
                    agentState.status.name.contains("STREAM") || agentState.status.name.contains("PREFILL") -> "Thinking…"
                    else -> "Tap the microphone to speak"
                }
                Text(statusText, style = MaterialTheme.typography.titleMedium)

                if (partialTranscript.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = partialTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isSpeaking) {
                        OutlinedButton(onClick = onStopSpeaking) { Text("Mute Speech") }
                    }
                    if (agentState.status.name == "STREAMING_TEXT" || agentState.status.name == "PREFILL") {
                        OutlinedButton(onClick = onStopGeneration) { Text("Stop AI") }
                    }
                    Button(onClick = onClose) { Text("Done") }
                }
            }
        }
    }
}
