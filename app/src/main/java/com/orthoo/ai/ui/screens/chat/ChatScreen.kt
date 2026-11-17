package com.orthoo.ai.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.AgentUiStatus
import com.orthoo.ai.services.speech.SpeechListeningState
import com.orthoo.ai.ui.components.BrandMark
import com.orthoo.ai.ui.components.DownloadBanner
import com.orthoo.ai.ui.components.HtmlPreviewSheet
import com.orthoo.ai.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onNavigateModels: () -> Unit,
    onPickImages: () -> Unit,
    onRequestMicPermission: (onGranted: () -> Unit) -> Unit,
    onCancelDownload: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val pals by viewModel.pals.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val activePal by viewModel.activePal.collectAsStateWithLifecycle()
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val attachedImages by viewModel.attachedImageUris.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.speakingMessageId.collectAsStateWithLifecycle()
    val listeningState by viewModel.listeningState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val voiceModeOpen by viewModel.voiceModeOpen.collectAsStateWithLifecycle()
    val htmlPreview by viewModel.htmlPreview.collectAsStateWithLifecycle()
    val uiError by viewModel.uiError.collectAsStateWithLifecycle()
    val speechError by viewModel.speechError.collectAsStateWithLifecycle()

    var showPickerSheet by remember { mutableStateOf(false) }
    var pickerInitialTab by remember { mutableIntStateOf(0) }
    var showGenSettings by remember { mutableStateOf(false) }
    var showContextSheet by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val isGenerating = agentState.status == AgentUiStatus.PREFILL ||
        agentState.status == AgentUiStatus.STREAMING_TEXT ||
        agentState.status == AgentUiStatus.GENERATING_TOOL_CALL ||
        agentState.status == AgentUiStatus.EXECUTING_TOOL

    LaunchedEffect(uiError, speechError) {
        val msg = uiError ?: speechError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiError()
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
                            modifier = Modifier.testTag("chat_menu_button")
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                        }
                    }
                },
                title = {
                    Surface(
                        modifier = Modifier
                            .clickable {
                                pickerInitialTab = 0
                                showPickerSheet = true
                            }
                            .testTag("chat_model_pal_pill"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activePal != null) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(activePal!!.colorPrimaryHex))
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Column(modifier = Modifier.widthIn(max = 180.dp)) {
                                Text(
                                    text = activeModel?.name ?: "Select Model",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (activePal != null) {
                                    Text(
                                        text = activePal!!.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Switch model or pal",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.createNewChat() },
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(Icons.Outlined.Create, contentDescription = "Start new chat")
                    }
                    Box {
                        IconButton(
                            onClick = { topMenuExpanded = true },
                            modifier = Modifier.testTag("chat_more_button")
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Chat settings")
                        }
                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Choose Pal") },
                                onClick = {
                                    topMenuExpanded = false
                                    pickerInitialTab = 1
                                    showPickerSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Generation Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                                onClick = {
                                    topMenuExpanded = false
                                    showGenSettings = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Context Window (${settings.contextSize / 1024}K)") },
                                onClick = {
                                    topMenuExpanded = false
                                    showContextSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage Models") },
                                onClick = {
                                    topMenuExpanded = false
                                    onNavigateModels()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            DownloadBanner(downloads = downloads, onCancel = onCancelDownload)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    ChatEmptyState(
                        activeModel = activeModel,
                        activePal = activePal,
                        suggestedPrompts = activePal?.let { viewModel.decodePalSuggestedPrompts(it) }
                            ?: listOf(
                                "Explain a complex topic simply",
                                "Calculate 15% tip on $84.50",
                                "Help me draft a clean Kotlin data class"
                            ),
                        onPromptClick = { prompt ->
                            if (activeModel != null) {
                                viewModel.sendMessage(overrideText = prompt)
                            } else {
                                viewModel.updateComposerText(prompt)
                            }
                        },
                        onSelectModelClick = {
                            pickerInitialTab = 0
                            showPickerSheet = true
                        },
                        onOpenModelsHub = onNavigateModels
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isLastAssistant = msg.role == "assistant" && msg.id == messages.lastOrNull()?.id
                            ChatMessageBubble(
                                message = msg,
                                imageUris = viewModel.getImageUris(msg),
                                toolCalls = viewModel.getToolCalls(msg),
                                toolOutcomes = viewModel.getToolOutcomes(msg),
                                isSpeakingThisMessage = isSpeaking && speakingMessageId == msg.id,
                                isStreamingThisMessage = isGenerating && isLastAssistant,
                                onEditUserMessage = { viewModel.beginEditMessage(msg) },
                                onRetryAssistant = { viewModel.retryAssistantMessage(msg) },
                                onToggleSpeak = { viewModel.toggleSpeakMessage(msg) },
                                onOpenHtmlPreview = { title, html -> viewModel.openHtmlPreview(title, html) }
                            )
                        }
                    }
                }
            }

            // Composer Area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (editingMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Editing prompt (resends & replaces reply)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { viewModel.cancelEditMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel edit", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (attachedImages.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            attachedImages.forEach { uri ->
                                Box {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Selected attachment",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .clickable { viewModel.removeAttachedImage(uri) },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Remove image",
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (listeningState == SpeechListeningState.LISTENING) {
                        Text(
                            text = if (partialTranscript.isNotBlank()) "Listening: $partialTranscript" else "Listening…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = onPickImages,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("attach_image_button")
                        ) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = "Attach image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = composerText,
                            onValueChange = { viewModel.updateComposerText(it) },
                            placeholder = {
                                Text(
                                    if (activeModel == null) "Select a model or server to start…"
                                    else "Type your message here"
                                )
                            },
                            maxLines = 5,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (!isGenerating && (composerText.isNotBlank() || attachedImages.isNotEmpty())) {
                                        viewModel.sendMessage()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field")
                        )

                        Spacer(Modifier.width(6.dp))

                        if (isGenerating) {
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { viewModel.stopGeneration() }
                                    .testTag("stop_button"),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Stop,
                                        contentDescription = "Stop generation",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else if (composerText.isNotBlank() || attachedImages.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { viewModel.sendMessage() }
                                    .testTag("send_button"),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send message",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        } else {
                            Row {
                                IconButton(
                                    onClick = {
                                        if (listeningState == SpeechListeningState.LISTENING) {
                                            viewModel.stopDictation()
                                        } else {
                                            onRequestMicPermission { viewModel.startDictation(false) }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("dictation_mic_button")
                                ) {
                                    Icon(
                                        imageVector = if (listeningState == SpeechListeningState.LISTENING) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                        contentDescription = "Voice dictation",
                                        tint = if (listeningState == SpeechListeningState.LISTENING) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onRequestMicPermission {
                                            viewModel.setVoiceModeOpen(true)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("voice_mode_button")
                                ) {
                                    Icon(
                                        Icons.Outlined.GraphicEq,
                                        contentDescription = "Open hands-free Voice Mode",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Capability Chips (Thinking, Effort, Active Pal)
                    if (activeModel?.supportsThinking == true || activePal != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeModel?.supportsThinking == true) {
                                val thinkingOn = activeSession?.thinkingEnabled ?: settings.globalCompletionParams.enableThinking
                                FilterChip(
                                    selected = thinkingOn,
                                    onClick = { viewModel.toggleThinking() },
                                    label = { Text("Think", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("thinking_toggle_chip")
                                )
                                if (activeModel?.supportsEffort == true && thinkingOn) {
                                    val effortLabel = (activeSession?.reasoningEffort ?: "medium").replaceFirstChar { it.uppercase() }
                                    AssistChip(
                                        onClick = { viewModel.cycleReasoningEffort() },
                                        label = { Text("Effort: $effortLabel", style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.testTag("effort_cycle_chip")
                                    )
                                }
                            }
                            if (activePal != null) {
                                AssistChip(
                                    onClick = {
                                        pickerInitialTab = 1
                                        showPickerSheet = true
                                    },
                                    label = { Text("Pal: ${activePal!!.name}", style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPickerSheet) {
        ChatPalModelPickerSheet(
            models = models,
            pals = pals,
            activeModelId = activeModel?.id,
            activePalId = activePal?.id,
            initialTab = pickerInitialTab,
            onSelectModel = { viewModel.selectModel(it) },
            onSelectPal = { viewModel.selectPal(it) },
            onOpenModelsScreen = onNavigateModels,
            onDismiss = { showPickerSheet = false }
        )
    }

    if (showGenSettings) {
        GenerationSettingsSheet(
            initialParams = viewModel.currentCompletionParams(),
            onSave = { params, global -> viewModel.saveCompletionSettings(params, global) },
            onDismiss = { showGenSettings = false }
        )
    }

    if (showContextSheet) {
        IncreaseContextSheet(
            currentContextSize = settings.contextSize,
            onSelectContextSize = { viewModel.updateContextSize(it) },
            onDismiss = { showContextSheet = false }
        )
    }

    if (voiceModeOpen) {
        VoiceModeDialog(
            listeningState = listeningState,
            partialTranscript = partialTranscript,
            isSpeaking = isSpeaking,
            agentState = agentState,
            onStartListening = { viewModel.startDictation(autoSendInVoiceMode = true) },
            onStopListening = { viewModel.stopDictation() },
            onStopSpeaking = { viewModel.stopSpeaking() },
            onStopGeneration = { viewModel.stopGeneration() },
            onClose = { viewModel.setVoiceModeOpen(false) }
        )
    }

    htmlPreview?.let { (title, html) ->
        HtmlPreviewSheet(
            title = title,
            html = html,
            onDismiss = { viewModel.closeHtmlPreview() }
        )
    }
}

@Composable
private fun ChatEmptyState(
    activeModel: ModelEntity?,
    activePal: PalEntity?,
    suggestedPrompts: List<String>,
    onPromptClick: (String) -> Unit,
    onSelectModelClick: () -> Unit,
    onOpenModelsHub: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = activePal?.name ?: "orthoo-ai",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = activePal?.greetingText?.takeIf { it.isNotBlank() }
                    ?: if (activeModel == null) {
                        "Connect a local server (Ollama, llama-server, LM Studio) or download a model to start chatting."
                    } else {
                        "Chatting with ${activeModel.name}. Your conversations stay private on-device."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            if (activeModel == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onOpenModelsHub,
                        modifier = Modifier.testTag("empty_select_models_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Model or Server")
                    }
                    OutlinedButton(onClick = onSelectModelClick) {
                        Text("Pick Downloaded")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestedPrompts.take(4).forEach { prompt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPromptClick(prompt) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
