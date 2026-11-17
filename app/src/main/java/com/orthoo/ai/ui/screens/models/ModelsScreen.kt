package com.orthoo.ai.ui.screens.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.model.DownloadProgress
import com.orthoo.ai.data.model.DownloadStatus
import com.orthoo.ai.data.model.ModelOrigin
import com.orthoo.ai.ui.components.DownloadBanner
import com.orthoo.ai.ui.components.Formatters
import com.orthoo.ai.ui.viewmodel.ModelsViewModel

private enum class ModelFilter(val label: String) {
    ALL("All"),
    READY("Ready"),
    REMOTE("Remote"),
    CATALOG("Presets")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    showMenuButton: Boolean,
    initialHfRepoId: String? = null,
    onConsumedInitialHfRepo: () -> Unit = {},
    onOpenDrawer: () -> Unit,
    onImportLocalGguf: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val models by viewModel.models.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var activeFilter by remember { mutableStateOf(ModelFilter.ALL) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var showHfSheet by remember { mutableStateOf(false) }
    var showRemoteSheet by remember { mutableStateOf(false) }
    var settingsTargetModel by remember { mutableStateOf<ModelEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialHfRepoId) {
        if (!initialHfRepoId.isNullOrBlank()) {
            viewModel.updateHfQuery(initialHfRepoId)
            viewModel.searchHuggingFace(initialHfRepoId)
            showHfSheet = true
            onConsumedInitialHfRepo()
        }
    }

    LaunchedEffect(downloadError, statusBanner) {
        val msg = downloadError ?: statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrors()
        }
    }

    val filteredModels = remember(models, activeFilter) {
        when (activeFilter) {
            ModelFilter.ALL -> models
            ModelFilter.READY -> models.filter { it.isDownloaded }
            ModelFilter.REMOTE -> models.filter { it.origin == ModelOrigin.REMOTE.name }
            ModelFilter.CATALOG -> models.filter { it.origin == ModelOrigin.PRESET.name }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("models_menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                        }
                    }
                },
                title = { Text("Models", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(
                        onClick = { viewModel.resetPresetModels() },
                        modifier = Modifier.testTag("reset_presets_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Reset default presets")
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { fabMenuOpen = true },
                    modifier = Modifier.testTag("models_add_fab")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add model or remote server")
                }
                DropdownMenu(
                    expanded = fabMenuOpen,
                    onDismissRequest = { fabMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Search Hugging Face GGUF") },
                        leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                        onClick = {
                            fabMenuOpen = false
                            viewModel.searchHuggingFace()
                            showHfSheet = true
                        },
                        modifier = Modifier.testTag("fab_search_hf")
                    )
                    DropdownMenuItem(
                        text = { Text("Add Remote Server / Model") },
                        leadingIcon = { Icon(Icons.Outlined.CloudQueue, contentDescription = null) },
                        onClick = {
                            fabMenuOpen = false
                            showRemoteSheet = true
                        },
                        modifier = Modifier.testTag("fab_add_remote")
                    )
                    DropdownMenuItem(
                        text = { Text("Import Local .gguf File") },
                        leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                        onClick = {
                            fabMenuOpen = false
                            onImportLocalGguf()
                        },
                        modifier = Modifier.testTag("fab_import_local")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DownloadBanner(
                downloads = downloads,
                onCancel = { viewModel.cancelDownload(it) }
            )

            // Quick Action Bar for Remote & HF Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.searchHuggingFace()
                        showHfSheet = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_hf_search_button")
                ) {
                    Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("HF Hub", maxLines = 1)
                }
                Button(
                    onClick = { showRemoteSheet = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_remote_server_button")
                ) {
                    Icon(Icons.Outlined.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Remote Server", maxLines = 1)
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("model_filter_${filter.name.lowercase()}")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredModels, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isActive = settings.activeModelId == model.id,
                        downloadProgress = downloads[model.id],
                        onSelect = {
                            viewModel.selectActiveModel(model.id)
                            if (settings.autoNavigateToChat) {
                                onNavigateToChat()
                            }
                        },
                        onDownload = { viewModel.startDownload(model) },
                        onCancelDownload = { viewModel.cancelDownload(model.id) },
                        onDelete = { viewModel.deleteModel(model.id) },
                        onEditSettings = { settingsTargetModel = model }
                    )
                }
            }
        }
    }

    if (showHfSheet) {
        HFSearchSheet(
            viewModel = viewModel,
            onDismiss = { showHfSheet = false }
        )
    }

    if (showRemoteSheet) {
        RemoteModelSheet(
            viewModel = viewModel,
            servers = servers,
            onDismiss = { showRemoteSheet = false }
        )
    }

    settingsTargetModel?.let { target ->
        ModelSettingsSheet(
            model = target,
            initialStopWords = viewModel.decodeStopWords(target),
            onSave = { name, template, stops, reasoningOverride, effort ->
                viewModel.updateModelSettings(target.id, name, template, stops, reasoningOverride, effort)
                settingsTargetModel = null
            },
            onDismiss = { settingsTargetModel = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: ModelEntity,
    isActive: Boolean,
    downloadProgress: DownloadProgress?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
    onEditSettings: () -> Unit
) {
    val isDownloading = downloadProgress != null || model.downloadStatus == DownloadStatus.DOWNLOADING.name

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("model_card_${model.filename}"),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isActive) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Active model",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    val subtitle = buildString {
                        append(model.author)
                        if (model.sizeBytes > 0L) append(" • ${Formatters.formatBytes(model.sizeBytes)}")
                        if (model.quantization.isNotBlank()) append(" • ${model.quantization}")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onEditSettings,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Model settings", modifier = Modifier.size(18.dp))
                }
            }

            if (model.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                AssistChip(onClick = {}, label = { Text(model.origin, style = MaterialTheme.typography.labelSmall) })
                if (model.supportsThinking) {
                    AssistChip(onClick = {}, label = { Text("Thinking", style = MaterialTheme.typography.labelSmall) })
                }
                if (model.supportsVision) {
                    AssistChip(onClick = {}, label = { Text("Vision", style = MaterialTheme.typography.labelSmall) })
                }
                if (model.supportsToolUse) {
                    AssistChip(onClick = {}, label = { Text("Tools", style = MaterialTheme.typography.labelSmall) })
                }
            }

            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                val fraction = downloadProgress?.fraction ?: (model.downloadProgress / 100f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(fraction * 100).toInt()}% ${Formatters.formatSpeed(downloadProgress?.speedBytesPerSec ?: 0L)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onCancelDownload) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (model.isDownloaded) {
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.testTag("use_model_button_${model.filename}")
                        ) {
                            Text(if (isActive) "Active in Chat" else "Use in Chat")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete model",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.testTag("download_model_button_${model.filename}")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download GGUF")
                        }
                        if (model.origin != ModelOrigin.PRESET.name) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove model")
                            }
                        }
                    }
                }
            }
        }
    }
}
