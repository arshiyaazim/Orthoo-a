package com.orthoo.ai.ui.screens.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.RemoteServerEntity
import com.orthoo.ai.ui.components.Formatters
import com.orthoo.ai.ui.viewmodel.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HFSearchSheet(
    viewModel: ModelsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val query by viewModel.hfQuery.collectAsStateWithLifecycle()
    val sort by viewModel.hfSort.collectAsStateWithLifecycle()
    val results by viewModel.hfResults.collectAsStateWithLifecycle()
    val filesMap by viewModel.hfFiles.collectAsStateWithLifecycle()
    val loading by viewModel.hfLoading.collectAsStateWithLifecycle()
    val error by viewModel.hfError.collectAsStateWithLifecycle()

    var expandedRepoId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 640.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Hugging Face GGUF Hub", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.updateHfQuery(it) },
                    placeholder = { Text("Search Qwen, Llama, Gemma…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hf_search_input")
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.searchHuggingFace() },
                    modifier = Modifier.testTag("hf_search_submit_button")
                ) {
                    Text("Go")
                }
            }

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("downloads" to "Downloads", "likes" to "Likes", "lastModified" to "Recent").forEach { (key, label) ->
                    FilterChip(
                        selected = sort == key,
                        onClick = { viewModel.updateHfSort(key) },
                        label = { Text(label) }
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Searching Hugging Face…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { repo ->
                        val isExpanded = expandedRepoId == repo.id
                        val repoFiles = filesMap[repo.id]

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedRepoId = if (isExpanded) null else repo.id
                                    if (!isExpanded && repoFiles == null) {
                                        viewModel.loadRepoFiles(repo.id)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(repo.id, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${Formatters.formatCount(repo.downloads)} downloads • ${Formatters.formatCount(repo.likes)} likes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (repo.gated) {
                                        Icon(
                                            Icons.Outlined.Lock,
                                            contentDescription = "Gated repository",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    if (repoFiles == null) {
                                        Text("Loading GGUF files…", style = MaterialTheme.typography.bodySmall)
                                    } else if (repoFiles.isEmpty()) {
                                        Text("No GGUF files found in repo.", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            repoFiles.forEach { file ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(file.filename, style = MaterialTheme.typography.bodySmall)
                                                        Text(
                                                            "${file.quantization} • ${Formatters.formatBytes(file.sizeBytes)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.addAndDownloadHfFile(repo, file)
                                                            onDismiss()
                                                        }
                                                    ) {
                                                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Get")
                                                    }
                                                }
                                            }
                                        }
                                    }
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
fun RemoteModelSheet(
    viewModel: ModelsViewModel,
    servers: List<RemoteServerEntity>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val probedModels by viewModel.probedModels.collectAsStateWithLifecycle()
    val detectedType by viewModel.detectedServerType.collectAsStateWithLifecycle()
    val probeLoading by viewModel.probeLoading.collectAsStateWithLifecycle()
    val probeError by viewModel.probeError.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var serverName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://192.168.1.100:11434") }
    var apiKey by remember { mutableStateOf("") }
    var timeoutSec by remember { mutableStateOf("30") }
    var selectedExistingServer by remember { mutableStateOf<RemoteServerEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearProbeState()
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Connect Remote Server", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Connect Ollama, llama-server, LM Studio, vLLM, OpenAI, or Hugging Face Inference Router.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            // Quick Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        selectedExistingServer = null
                        serverName = "Ollama LAN"
                        serverUrl = "http://10.0.2.2:11434"
                    },
                    label = { Text("Ollama") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        selectedExistingServer = null
                        serverName = "LM Studio"
                        serverUrl = "http://10.0.2.2:1234"
                    },
                    label = { Text("LM Studio") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        selectedExistingServer = null
                        serverName = "Hugging Face Router"
                        serverUrl = "https://router.huggingface.co"
                        apiKey = settings.hfToken
                    },
                    label = { Text("HF Router") }
                )
            }

            if (servers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Saved Servers", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                servers.forEach { srv ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                selectedExistingServer = srv
                                serverName = srv.name
                                serverUrl = srv.url
                                apiKey = srv.apiKey.orEmpty()
                                viewModel.probeRemoteServer(srv.url, srv.apiKey, (srv.timeoutMs / 1000).toInt())
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(srv.name, style = MaterialTheme.typography.titleSmall)
                                Text(srv.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("Server Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    selectedExistingServer = null
                    serverUrl = it
                },
                label = { Text("Server URL (e.g. http://192.168.1.50:11434)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_server_url_input")
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key / Token (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = timeoutSec,
                    onValueChange = { timeoutSec = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Timeout (s)") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.probeRemoteServer(
                        url = serverUrl,
                        apiKey = apiKey.takeIf { it.isNotBlank() },
                        timeoutSec = timeoutSec.toIntOrNull() ?: 30
                    )
                },
                enabled = serverUrl.isNotBlank() && !probeLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("probe_server_button")
            ) {
                if (probeLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Testing Connection…")
                } else {
                    Text("Test & Fetch /v1/models")
                }
            }

            if (!probeError.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = probeError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (probedModels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Detected: ${detectedType.displayName} (${probedModels.size} models)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                probedModels.forEach { remote ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                val existing = selectedExistingServer
                                if (existing != null) {
                                    viewModel.addModelFromExistingServer(existing, remote)
                                } else {
                                    viewModel.addRemoteServerAndModel(
                                        serverName = serverName.ifBlank { detectedType.displayName },
                                        serverUrl = serverUrl,
                                        apiKey = apiKey,
                                        timeoutSec = timeoutSec.toIntOrNull() ?: 30,
                                        remoteModel = remote
                                    )
                                }
                                onDismiss()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(remote.id, style = MaterialTheme.typography.titleSmall)
                                val flags = buildList {
                                    remote.ownedBy?.let { add(it) }
                                    if (remote.supportsVision) add("Vision")
                                    remote.contextLength?.let { add("${it} ctx") }
                                }.joinToString(" • ")
                                if (flags.isNotBlank()) {
                                    Text(flags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (settings.hfToken.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                Text("Quick Hugging Face Router", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.addQuickHuggingFaceCloudServer(settings.hfCloudChatModel, "Qwen2.5 72B")
                            onDismiss()
                        }
                    ) { Text("Qwen 72B") }
                    OutlinedButton(
                        onClick = {
                            viewModel.addQuickHuggingFaceCloudServer(settings.hfCloudCodeModel, "Qwen Coder 32B")
                            onDismiss()
                        }
                    ) { Text("Coder 32B") }
                }
            } else {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsSheet(
    model: ModelEntity,
    initialStopWords: String,
    onSave: (name: String, template: String, stopWords: String, reasoningOverride: Boolean?, supportsEffort: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(model.name) }
    var template by remember { mutableStateOf(model.chatTemplate) }
    var stopWords by remember { mutableStateOf(initialStopWords) }
    var thinkingEnabled by remember { mutableStateOf(model.supportsThinking) }
    var effortSupported by remember { mutableStateOf(model.supportsEffort) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("Model Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = stopWords,
                onValueChange = { stopWords = it },
                label = { Text("Stop Words (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = template,
                onValueChange = { template = it },
                label = { Text("Custom Chat Template (optional)") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Thinking / Reasoning Toggle", style = MaterialTheme.typography.titleSmall)
                Switch(checked = thinkingEnabled, onCheckedChange = { thinkingEnabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Supports Reasoning Effort (Low/Med/High)", style = MaterialTheme.typography.titleSmall)
                Switch(checked = effortSupported, onCheckedChange = { effortSupported = it })
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSave(name, template, stopWords, thinkingEnabled, effortSupported) }
                ) { Text("Save") }
            }
        }
    }
}
