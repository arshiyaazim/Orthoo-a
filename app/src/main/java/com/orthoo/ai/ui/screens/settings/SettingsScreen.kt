package com.orthoo.ai.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthoo.ai.data.model.HardwareBackend
import com.orthoo.ai.data.model.SearchProviderId
import com.orthoo.ai.data.model.ThemeMode
import com.orthoo.ai.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val tokenTestState by viewModel.tokenTestState.collectAsStateWithLifecycle()

    var hfTokenInput by remember(settings.hfToken) { mutableStateOf(settings.hfToken) }
    var searxngInput by remember(settings.searxngUrl) { mutableStateOf(settings.searxngUrl) }
    var searchKeyInput by remember(settings.searchApiKey) { mutableStateOf(settings.searchApiKey) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("settings_menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                        }
                    }
                },
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsSectionCard("Appearance & App") {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        title = "Auto-navigate to Chat on model select",
                        checked = settings.autoNavigateToChat,
                        onCheckedChange = { viewModel.setAutoNavigateToChat(it) }
                    )
                    ToggleRow(
                        title = "Display memory & size warnings",
                        checked = settings.displayMemoryUsage,
                        onCheckedChange = { viewModel.setDisplayMemoryUsage(it) }
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.replayOnboarding()
                            onReplayOnboarding()
                        },
                        modifier = Modifier.testTag("replay_onboarding_button")
                    ) {
                        Text("Replay Welcome Onboarding")
                    }
                }
            }

            item {
                SettingsSectionCard("Model Loading & Hardware") {
                    Text("Preferred Acceleration Backend", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HardwareBackend.entries.forEach { backend ->
                            FilterChip(
                                selected = settings.hardwareBackend == backend,
                                onClick = { viewModel.setHardwareBackend(backend) },
                                label = { Text(backend.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Context Window: ${settings.contextSize} tokens", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = settings.contextSize.toFloat(),
                        onValueChange = { viewModel.setContextSize(it.roundToInt()) },
                        valueRange = 1024f..32768f
                    )
                    Text("CPU Threads: ${settings.nThreads}", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = settings.nThreads.toFloat(),
                        onValueChange = { viewModel.setNThreads(it.roundToInt()) },
                        valueRange = 1f..12f
                    )
                    ToggleRow(
                        title = "Use Memory Mapping (mmap)",
                        checked = settings.useMmap,
                        onCheckedChange = { viewModel.setUseMmap(it) }
                    )
                    ToggleRow(
                        title = "Flash Attention",
                        checked = settings.flashAttn,
                        onCheckedChange = { viewModel.setFlashAttn(it) }
                    )
                    ToggleRow(
                        title = "Auto-release idle memory",
                        checked = settings.autoRelease,
                        onCheckedChange = { viewModel.setAutoRelease(it) }
                    )
                }
            }

            item {
                SettingsSectionCard("Hugging Face Hub & Remote Servers") {
                    OutlinedTextField(
                        value = hfTokenInput,
                        onValueChange = {
                            hfTokenInput = it
                            viewModel.setHfToken(it)
                        },
                        label = { Text("Hugging Face Access Token (hf_…)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hf_token_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testHfToken(hfTokenInput) },
                            modifier = Modifier.testTag("test_hf_token_button")
                        ) {
                            Text("Verify Token")
                        }
                        if (!tokenTestState.isNullOrBlank()) {
                            Text(
                                text = tokenTestState!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (servers.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Connected Servers (${servers.size})", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        servers.forEach { srv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(srv.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        srv.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeServer(srv.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove server", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionCard("Internet Search (Opt-In)") {
                    ToggleRow(
                        title = "Enable web_search & read_url talents",
                        checked = settings.searchConsented,
                        onCheckedChange = { viewModel.setSearchConsented(it) }
                    )
                    Text(
                        "When enabled, Pals with search talents can query the selected provider below directly from your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchProviderId.entries.forEach { provider ->
                            FilterChip(
                                selected = settings.searchProvider == provider,
                                onClick = { viewModel.setSearchProvider(provider) },
                                label = { Text(provider.displayName) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        settings.searchProvider.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.searchProvider == SearchProviderId.SEARXNG) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searxngInput,
                            onValueChange = {
                                searxngInput = it
                                viewModel.setSearxngUrl(it)
                            },
                            label = { Text("SearXNG Instance URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (settings.searchProvider.requiresKey) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchKeyInput,
                            onValueChange = {
                                searchKeyInput = it
                                viewModel.setSearchApiKey(it)
                            },
                            label = { Text("${settings.searchProvider.displayName} API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                SettingsSectionCard("Voice & Text-to-Speech") {
                    ToggleRow(
                        title = "Auto-read assistant replies",
                        checked = settings.ttsAutoSpeak,
                        onCheckedChange = { viewModel.setTtsAutoSpeak(it) }
                    )
                    if (voices.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Device TTS Voices", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            voices.take(8).forEach { voice ->
                                FilterChip(
                                    selected = settings.ttsSelectedVoiceName == voice.name,
                                    onClick = { viewModel.setTtsVoiceName(voice.name) },
                                    label = { Text(voice.name.take(18)) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.previewVoice(settings.ttsSelectedVoiceName) }) {
                        Text("Preview Voice")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
