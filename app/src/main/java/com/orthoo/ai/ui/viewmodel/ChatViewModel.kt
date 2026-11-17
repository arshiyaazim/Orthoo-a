package com.orthoo.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthoo.ai.data.db.ChatMessageEntity
import com.orthoo.ai.data.db.ChatSessionEntity
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.AgentUiStatus
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.DownloadProgress
import com.orthoo.ai.data.model.ReasoningEffort
import com.orthoo.ai.data.model.ReasoningIntent
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.di.AppContainer
import com.orthoo.ai.services.agent.AgentRunState
import com.orthoo.ai.services.speech.SpeechListeningState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class SessionGroup(
    val label: String,
    val sessions: List<ChatSessionEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val models: StateFlow<List<ModelEntity>> = container.modelRepository.models
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pals: StateFlow<List<PalEntity>> = container.palRepository.pals
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<ChatSessionEntity>> = container.chatRepository.sessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val agentState: StateFlow<AgentRunState> = container.agentRunner.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgentRunState())

    val downloads: StateFlow<Map<String, DownloadProgress>> = container.downloadManager.downloads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val isSpeaking: StateFlow<Boolean> = container.speechManager.isSpeaking
    val speakingMessageId: StateFlow<String?> = container.speechManager.speakingMessageId
    val listeningState: StateFlow<SpeechListeningState> = container.speechManager.listeningState
    val partialTranscript: StateFlow<String> = container.speechManager.partialTranscript
    val speechError: StateFlow<String?> = container.speechManager.speechError

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = combine(
        _selectedSessionId,
        settings.map { it.activeSessionId }.distinctUntilChanged(),
        sessions
    ) { localId, savedId, allSessions ->
        val candidate = localId ?: savedId
        if (candidate != null && allSessions.any { it.id == candidate }) {
            candidate
        } else {
            allSessions.firstOrNull()?.id
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeSession: StateFlow<ChatSessionEntity?> = combine(
        selectedSessionId,
        sessions
    ) { id, all -> all.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val messages: StateFlow<List<ChatMessageEntity>> = selectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.chatRepository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeModel: StateFlow<ModelEntity?> = combine(
        activeSession,
        settings,
        models
    ) { session, prefs, allModels ->
        val modelId = session?.modelId ?: prefs.activeModelId
        allModels.find { it.id == modelId }
            ?: allModels.firstOrNull { it.isDownloaded }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _draftPalId = MutableStateFlow<String?>(null)

    val activePal: StateFlow<PalEntity?> = combine(
        activeSession,
        _draftPalId,
        pals
    ) { session, draftPalId, allPals ->
        val palId = session?.activePalId ?: draftPalId
        allPals.find { it.id == palId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sidebarQuery = MutableStateFlow("")
    val sidebarQuery: StateFlow<String> = _sidebarQuery.asStateFlow()

    val groupedSessions: StateFlow<List<SessionGroup>> = combine(
        sessions,
        _sidebarQuery
    ) { list, query ->
        val filtered = if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter { it.title.lowercase().contains(q) }
        }
        groupSessionsByRecency(filtered)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _attachedImageUris = MutableStateFlow<List<String>>(emptyList())
    val attachedImageUris: StateFlow<List<String>> = _attachedImageUris.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessageEntity?>(null)
    val editingMessage: StateFlow<ChatMessageEntity?> = _editingMessage.asStateFlow()

    private val _selectedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSessionIds: StateFlow<Set<String>> = _selectedSessionIds.asStateFlow()

    private val _voiceModeOpen = MutableStateFlow(false)
    val voiceModeOpen: StateFlow<Boolean> = _voiceModeOpen.asStateFlow()

    private val _htmlPreview = MutableStateFlow<Pair<String, String>?>(null)
    val htmlPreview: StateFlow<Pair<String, String>?> = _htmlPreview.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    private var generationJob: Job? = null

    init {
        container.speechManager.initTts()
    }

    fun updateComposerText(text: String) {
        _composerText.value = text
    }

    fun addAttachedImages(uris: List<String>) {
        if (uris.isEmpty()) return
        _attachedImageUris.value = (_attachedImageUris.value + uris).distinct().take(4)
    }

    fun removeAttachedImage(uri: String) {
        _attachedImageUris.value = _attachedImageUris.value - uri
    }

    fun updateSidebarQuery(query: String) {
        _sidebarQuery.value = query
    }

    fun clearUiError() {
        _uiError.value = null
        container.speechManager.clearError()
    }

    fun openHtmlPreview(title: String, html: String) {
        _htmlPreview.value = title to html
    }

    fun closeHtmlPreview() {
        _htmlPreview.value = null
    }

    fun setVoiceModeOpen(open: Boolean) {
        _voiceModeOpen.value = open
        if (!open) {
            container.speechManager.stopListening()
        }
    }

    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
        _editingMessage.value = null
        viewModelScope.launch {
            container.preferences.setActiveSessionId(sessionId)
        }
    }

    fun createNewChat(palId: String? = null, modelId: String? = null) {
        viewModelScope.launch {
            val targetModelId = modelId ?: activeModel.value?.id ?: settings.value.activeModelId
            val targetPalId = palId ?: _draftPalId.value
            val pal = targetPalId?.let { id -> pals.value.find { it.id == id } }
            val session = container.chatRepository.createSession(
                title = "New Chat",
                palId = pal?.id,
                modelId = pal?.defaultModelId ?: targetModelId
            )
            _selectedSessionId.value = session.id
            _draftPalId.value = null
            _editingMessage.value = null
            container.preferences.setActiveSessionId(session.id)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            container.chatRepository.updateSessionTitle(sessionId, title)
        }
    }

    fun togglePinSession(sessionId: String) {
        viewModelScope.launch {
            container.chatRepository.togglePinSession(sessionId)
        }
    }

    fun duplicateSession(sessionId: String) {
        viewModelScope.launch {
            val copy = container.chatRepository.duplicateSession(sessionId) ?: return@launch
            _selectedSessionId.value = copy.id
            container.preferences.setActiveSessionId(copy.id)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            container.chatRepository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
                container.preferences.setActiveSessionId(null)
            }
            _selectedSessionIds.value = _selectedSessionIds.value - sessionId
        }
    }

    fun toggleSessionSelection(sessionId: String) {
        val current = _selectedSessionIds.value
        _selectedSessionIds.value = if (sessionId in current) current - sessionId else current + sessionId
    }

    fun clearSessionSelection() {
        _selectedSessionIds.value = emptySet()
    }

    fun deleteSelectedSessions() {
        val ids = _selectedSessionIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            container.chatRepository.deleteSessions(ids)
            if (_selectedSessionId.value in ids) {
                _selectedSessionId.value = null
                container.preferences.setActiveSessionId(null)
            }
            _selectedSessionIds.value = emptySet()
        }
    }

    fun selectModel(modelId: String?) {
        viewModelScope.launch {
            container.preferences.setActiveModelId(modelId)
            selectedSessionId.value?.let { sid ->
                container.chatRepository.setSessionModel(sid, modelId)
            }
        }
    }

    fun selectPal(palId: String?) {
        viewModelScope.launch {
            _draftPalId.value = palId
            selectedSessionId.value?.let { sid ->
                container.chatRepository.setSessionPal(sid, palId)
            }
            val pal = palId?.let { id -> pals.value.find { it.id == id } }
            if (pal?.defaultModelId != null) {
                selectModel(pal.defaultModelId)
            }
        }
    }

    fun beginEditMessage(message: ChatMessageEntity) {
        if (message.role != "user") return
        _editingMessage.value = message
        _composerText.value = message.text
        _attachedImageUris.value = container.chatRepository.decodeImageUris(message)
    }

    fun cancelEditMessage() {
        _editingMessage.value = null
        _composerText.value = ""
        _attachedImageUris.value = emptyList()
    }

    fun sendMessage(overrideText: String? = null) {
        val rawText = (overrideText ?: _composerText.value).trim()
        val images = if (overrideText == null) _attachedImageUris.value else emptyList()
        if (rawText.isEmpty() && images.isEmpty()) return

        val model = activeModel.value
        if (model == null) {
            _uiError.value = "Select or add a model first before sending a message."
            return
        }

        if (overrideText == null) {
            _composerText.value = ""
            _attachedImageUris.value = emptyList()
        }

        val editing = _editingMessage.value
        _editingMessage.value = null

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val existingSession = activeSession.value
            val session = existingSession ?: container.chatRepository.createSession(
                title = rawText.replace("\n", " ").take(42).ifEmpty { "New Chat" },
                palId = _draftPalId.value,
                modelId = model.id
            ).also {
                _selectedSessionId.value = it.id
                _draftPalId.value = null
                container.preferences.setActiveSessionId(it.id)
            }

            if (editing != null) {
                container.chatRepository.deleteMessagesFrom(session.id, editing.createdAt)
            }

            val userMessage = ChatMessageEntity(
                id = editing?.id ?: UUID.randomUUID().toString(),
                sessionId = session.id,
                role = "user",
                text = rawText,
                imageUrisJson = container.chatRepository.encodeImageUris(images),
                createdAt = editing?.createdAt ?: System.currentTimeMillis()
            )
            container.chatRepository.upsertMessage(userMessage)

            executeAgentTurn(session.id, model)
        }
    }

    fun retryAssistantMessage(assistantMessage: ChatMessageEntity) {
        val model = activeModel.value ?: return
        val sessionId = activeSession.value?.id ?: return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            container.chatRepository.deleteMessagesFrom(sessionId, assistantMessage.createdAt)
            executeAgentTurn(sessionId, model)
        }
    }

    private suspend fun executeAgentTurn(sessionId: String, model: ModelEntity) {
        val session = container.chatRepository.getSession(sessionId)
        val pal = session?.activePalId?.let { container.palRepository.getById(it) }
        val prefs = settings.value
        val sessionParams = container.chatRepository.parseCompletionParams(session)
        val palParams = pal?.let { container.palRepository.decodeCompletionParams(it) }
        val effectiveParams = sessionParams ?: palParams ?: prefs.globalCompletionParams

        val thinkingEnabled = session?.thinkingEnabled ?: effectiveParams.enableThinking
        val reasoningIntent = if (model.supportsThinking) {
            ReasoningIntent(
                enabled = thinkingEnabled,
                effort = session?.reasoningEffort
            )
        } else null

        container.agentRunner.runTurn(
            sessionId = sessionId,
            model = model,
            pal = pal,
            completionParams = effectiveParams.copy(enableThinking = thinkingEnabled),
            reasoning = reasoningIntent,
            settings = prefs
        )

        if (container.agentRunner.state.value.status == AgentUiStatus.DONE &&
            (prefs.ttsAutoSpeak || _voiceModeOpen.value)
        ) {
            val latest = container.chatRepository.getMessages(sessionId).lastOrNull { it.role == "assistant" && !it.isError }
            if (latest != null && latest.text.isNotBlank()) {
                container.speechManager.speak(latest.text, latest.id, prefs.ttsSelectedVoiceName)
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    fun toggleThinking() {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            container.chatRepository.setSessionThinking(
                sessionId = session.id,
                enabled = !session.thinkingEnabled,
                effort = session.reasoningEffort
            )
        }
    }

    fun cycleReasoningEffort() {
        val session = activeSession.value ?: return
        val efforts = listOf(
            ReasoningEffort.LOW.wireValue,
            ReasoningEffort.MEDIUM.wireValue,
            ReasoningEffort.HIGH.wireValue
        )
        val currentIdx = efforts.indexOf(session.reasoningEffort)
        val next = efforts[(currentIdx + 1) % efforts.size]
        viewModelScope.launch {
            container.chatRepository.setSessionThinking(
                sessionId = session.id,
                enabled = true,
                effort = next
            )
        }
    }

    fun saveCompletionSettings(params: CompletionParams, applyGlobally: Boolean) {
        viewModelScope.launch {
            activeSession.value?.let { session ->
                container.chatRepository.updateSessionCompletionParams(session.id, params)
            }
            if (applyGlobally) {
                container.preferences.setGlobalCompletionParams(params)
            }
        }
    }

    fun updateContextSize(newSize: Int) {
        viewModelScope.launch {
            container.preferences.setContextSize(newSize)
        }
    }

    fun startDictation(autoSendInVoiceMode: Boolean = false) {
        container.speechManager.stopSpeaking()
        container.speechManager.startListening { transcript ->
            if (autoSendInVoiceMode || _voiceModeOpen.value) {
                sendMessage(overrideText = transcript)
            } else {
                val existing = _composerText.value
                _composerText.value = if (existing.isBlank()) transcript else "$existing $transcript"
            }
        }
    }

    fun stopDictation() {
        container.speechManager.stopListening()
    }

    fun toggleSpeakMessage(message: ChatMessageEntity) {
        if (speakingMessageId.value == message.id && isSpeaking.value) {
            container.speechManager.stopSpeaking()
        } else {
            container.speechManager.speak(message.text, message.id, settings.value.ttsSelectedVoiceName)
        }
    }

    fun stopSpeaking() {
        container.speechManager.stopSpeaking()
    }

    fun getImageUris(message: ChatMessageEntity): List<String> =
        container.chatRepository.decodeImageUris(message)

    fun getToolCalls(message: ChatMessageEntity) =
        container.chatRepository.decodeToolCalls(message)

    fun getToolOutcomes(message: ChatMessageEntity) =
        container.chatRepository.decodeToolOutcomes(message)

    fun decodePalSuggestedPrompts(pal: PalEntity): List<String> =
        container.palRepository.decodeSuggestedPrompts(pal)

    fun currentCompletionParams(): CompletionParams {
        val session = activeSession.value
        return container.chatRepository.parseCompletionParams(session)
            ?: activePal.value?.let { container.palRepository.decodeCompletionParams(it) }
            ?: settings.value.globalCompletionParams
    }

    private fun groupSessionsByRecency(list: List<ChatSessionEntity>): List<SessionGroup> {
        if (list.isEmpty()) return emptyList()
        val pinned = list.filter { it.isPinned }
        val unpinned = list.filterNot { it.isPinned }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val startOfYesterday = startOfToday - 86_400_000L
        val startOfWeek = startOfToday - 6 * 86_400_000L

        val today = mutableListOf<ChatSessionEntity>()
        val yesterday = mutableListOf<ChatSessionEntity>()
        val thisWeek = mutableListOf<ChatSessionEntity>()
        val older = mutableListOf<ChatSessionEntity>()

        for (s in unpinned) {
            when {
                s.updatedAt >= startOfToday -> today.add(s)
                s.updatedAt >= startOfYesterday -> yesterday.add(s)
                s.updatedAt >= startOfWeek -> thisWeek.add(s)
                else -> older.add(s)
            }
        }

        return buildList {
            if (pinned.isNotEmpty()) add(SessionGroup("Pinned", pinned))
            if (today.isNotEmpty()) add(SessionGroup("Today", today))
            if (yesterday.isNotEmpty()) add(SessionGroup("Yesterday", yesterday))
            if (thisWeek.isNotEmpty()) add(SessionGroup("This week", thisWeek))
            if (older.isNotEmpty()) add(SessionGroup("Older", older))
        }
    }
}
