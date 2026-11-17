package com.orthoo.ai.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.RemoteServerEntity
import com.orthoo.ai.data.model.DownloadProgress
import com.orthoo.ai.data.model.DownloadStatus
import com.orthoo.ai.data.model.HuggingFaceFile
import com.orthoo.ai.data.model.HuggingFaceModelSummary
import com.orthoo.ai.data.model.ModelOrigin
import com.orthoo.ai.data.model.RemoteModelInfo
import com.orthoo.ai.data.model.ServerType
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.di.AppContainer
import com.orthoo.ai.services.gguf.GgufReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    val models: StateFlow<List<ModelEntity>> = container.modelRepository.models
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val servers: StateFlow<List<RemoteServerEntity>> = container.modelRepository.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val downloads: StateFlow<Map<String, DownloadProgress>> = container.downloadManager.downloads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val downloadError: StateFlow<String?> = container.downloadManager.lastError
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _hfQuery = MutableStateFlow("")
    val hfQuery: StateFlow<String> = _hfQuery.asStateFlow()

    private val _hfSort = MutableStateFlow("downloads")
    val hfSort: StateFlow<String> = _hfSort.asStateFlow()

    private val _hfResults = MutableStateFlow<List<HuggingFaceModelSummary>>(emptyList())
    val hfResults: StateFlow<List<HuggingFaceModelSummary>> = _hfResults.asStateFlow()

    private val _hfFiles = MutableStateFlow<Map<String, List<HuggingFaceFile>>>(emptyMap())
    val hfFiles: StateFlow<Map<String, List<HuggingFaceFile>>> = _hfFiles.asStateFlow()

    private val _hfLoading = MutableStateFlow(false)
    val hfLoading: StateFlow<Boolean> = _hfLoading.asStateFlow()

    private val _hfError = MutableStateFlow<String?>(null)
    val hfError: StateFlow<String?> = _hfError.asStateFlow()

    private val _probedModels = MutableStateFlow<List<RemoteModelInfo>>(emptyList())
    val probedModels: StateFlow<List<RemoteModelInfo>> = _probedModels.asStateFlow()

    private val _detectedServerType = MutableStateFlow(ServerType.UNKNOWN)
    val detectedServerType: StateFlow<ServerType> = _detectedServerType.asStateFlow()

    private val _probeLoading = MutableStateFlow(false)
    val probeLoading: StateFlow<Boolean> = _probeLoading.asStateFlow()

    private val _probeError = MutableStateFlow<String?>(null)
    val probeError: StateFlow<String?> = _probeError.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun clearErrors() {
        container.downloadManager.clearError()
        _hfError.value = null
        _probeError.value = null
        _statusBanner.value = null
    }

    fun selectActiveModel(modelId: String?) {
        viewModelScope.launch {
            container.preferences.setActiveModelId(modelId)
            val activeSessionId = settings.value.activeSessionId
            if (activeSessionId != null) {
                container.chatRepository.setSessionModel(activeSessionId, modelId)
            }
        }
    }

    fun startDownload(model: ModelEntity) {
        val prefs = settings.value
        container.downloadManager.startDownload(
            model = model,
            authToken = prefs.hfToken.takeIf { it.isNotBlank() },
            maxAllowedBytes = prefs.maxModelSizeBytes
        )
    }

    fun cancelDownload(modelId: String) {
        container.downloadManager.cancelDownload(modelId)
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            if (settings.value.activeModelId == modelId) {
                container.preferences.setActiveModelId(null)
            }
            container.modelRepository.deleteLocalFileAndReset(modelId)
        }
    }

    fun resetPresetModels() {
        viewModelScope.launch {
            container.modelRepository.resetPresetModels()
            _statusBanner.value = "Default preset models restored."
        }
    }

    fun importLocalGguf(context: Context, uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val cr = context.contentResolver
                    val displayName = cr.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                    } ?: "imported-${System.currentTimeMillis()}.gguf"

                    val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
                    val safeName = displayName.replace("/", "_")
                    val destFile = File(modelsDir, safeName)

                    cr.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output, 64 * 1024)
                        }
                    } ?: throw IllegalStateException("Unable to read selected file.")

                    val gguf = GgufReader.inspectFile(destFile)
                        ?: run {
                            destFile.delete()
                            throw IllegalArgumentException("Selected file is not a valid GGUF binary.")
                        }

                    val model = ModelEntity(
                        id = "local/${UUID.randomUUID()}/$safeName",
                        name = gguf.modelName ?: safeName.removeSuffix(".gguf"),
                        author = gguf.architecture ?: "Local GGUF",
                        type = gguf.architecture?.uppercase() ?: "GGUF",
                        origin = ModelOrigin.LOCAL.name,
                        sizeBytes = destFile.length(),
                        paramsCount = 0L,
                        filename = safeName,
                        localPath = destFile.absolutePath,
                        isDownloaded = true,
                        downloadProgress = 100,
                        downloadStatus = DownloadStatus.COMPLETED.name,
                        contextLength = gguf.contextLength ?: 4096,
                        description = "Imported local GGUF v${gguf.version} (${gguf.tensorCount} tensors)."
                    )
                    container.modelRepository.upsertModel(model)
                    container.preferences.setActiveModelId(model.id)
                    _statusBanner.value = "Imported ${model.name} (${gguf.architecture ?: "GGUF"})."
                }.onFailure { err ->
                    _statusBanner.value = err.message ?: "Import failed."
                }
            }
        }
    }

    fun updateHfQuery(query: String) {
        _hfQuery.value = query
    }

    fun updateHfSort(sort: String) {
        _hfSort.value = sort
        searchHuggingFace(_hfQuery.value, sort)
    }

    fun searchHuggingFace(query: String = _hfQuery.value, sort: String = _hfSort.value) {
        viewModelScope.launch {
            _hfLoading.value = true
            _hfError.value = null
            container.huggingFaceApi.searchGgufModels(
                query = query,
                sort = sort,
                authToken = settings.value.hfToken.takeIf { it.isNotBlank() }
            ).onSuccess {
                _hfResults.value = it
            }.onFailure {
                _hfError.value = it.message ?: "Hugging Face search failed"
            }
            _hfLoading.value = false
        }
    }

    fun loadRepoFiles(repoId: String) {
        viewModelScope.launch {
            _hfError.value = null
            container.huggingFaceApi.fetchGgufFiles(
                repoId = repoId,
                authToken = settings.value.hfToken.takeIf { it.isNotBlank() }
            ).onSuccess { files ->
                _hfFiles.value = _hfFiles.value + (repoId to files)
            }.onFailure { err ->
                _hfError.value = err.message ?: "Failed to load repo files"
            }
        }
    }

    fun addAndDownloadHfFile(summary: HuggingFaceModelSummary, file: HuggingFaceFile) {
        viewModelScope.launch {
            val lower = "${summary.id} ${file.filename}".lowercase()
            val model = ModelEntity(
                id = "${summary.id}/${file.filename}",
                name = summary.id.substringAfter("/").ifBlank { file.filename.removeSuffix(".gguf") },
                author = summary.author,
                type = "Hugging Face GGUF",
                origin = ModelOrigin.HF.name,
                sizeBytes = file.sizeBytes,
                paramsCount = 0L,
                filename = file.filename.substringAfterLast("/"),
                downloadUrl = file.downloadUrl,
                hfUrl = "https://huggingface.co/${summary.id}",
                supportsThinking = lower.contains("r1") || lower.contains("qwq") || lower.contains("qwen3") || lower.contains("thinking"),
                supportsToolUse = lower.contains("instruct") || lower.contains("qwen") || lower.contains("llama-3"),
                quantization = file.quantization,
                description = "Hugging Face repo ${summary.id} (${file.quantization})"
            )
            container.modelRepository.upsertModel(model)
            startDownload(model)
        }
    }

    fun probeRemoteServer(url: String, apiKey: String?, timeoutSec: Int = 30) {
        viewModelScope.launch {
            _probeLoading.value = true
            _probeError.value = null
            _probedModels.value = emptyList()
            container.openAIClient.probeServer(
                serverUrl = url,
                apiKey = apiKey,
                timeoutMs = timeoutSec * 1000L
            ).onSuccess { result ->
                _probedModels.value = result.models
                _detectedServerType.value = result.detectedType
                if (result.models.isEmpty()) {
                    _probeError.value = "Connected, but server returned 0 models from /v1/models."
                }
            }.onFailure { err ->
                _probeError.value = err.message ?: "Connection failed"
            }
            _probeLoading.value = false
        }
    }

    fun clearProbeState() {
        _probedModels.value = emptyList()
        _probeError.value = null
        _detectedServerType.value = ServerType.UNKNOWN
    }

    fun addRemoteServerAndModel(
        serverName: String,
        serverUrl: String,
        apiKey: String?,
        timeoutSec: Int,
        remoteModel: RemoteModelInfo
    ) {
        viewModelScope.launch {
            val (_, model) = container.modelRepository.addRemoteServerAndModel(
                serverName = serverName,
                serverUrl = serverUrl,
                serverType = _detectedServerType.value,
                apiKey = apiKey,
                timeoutMs = timeoutSec * 1000L,
                remoteModel = remoteModel
            )
            selectActiveModel(model.id)
            clearProbeState()
            _statusBanner.value = "Connected remote model ${model.name}."
        }
    }

    fun addModelFromExistingServer(server: RemoteServerEntity, remoteModel: RemoteModelInfo) {
        viewModelScope.launch {
            val model = container.modelRepository.addModelForExistingServer(server, remoteModel)
            selectActiveModel(model.id)
            clearProbeState()
            _statusBanner.value = "Added ${model.name} from ${server.name}."
        }
    }

    fun addQuickHuggingFaceCloudServer(modelId: String, label: String) {
        val token = settings.value.hfToken.trim()
        if (token.isEmpty()) {
            _probeError.value = "Enter a Hugging Face token in Settings first to use HF Inference Router."
            return
        }
        viewModelScope.launch {
            val existing = servers.value.find { it.url.contains("router.huggingface.co") }
            val model = if (existing != null) {
                container.modelRepository.addModelForExistingServer(
                    existing,
                    RemoteModelInfo(id = modelId, ownedBy = "huggingface")
                )
            } else {
                container.modelRepository.addRemoteServerAndModel(
                    serverName = "Hugging Face Router",
                    serverUrl = "https://router.huggingface.co/v1",
                    serverType = ServerType.HUGGING_FACE,
                    apiKey = token,
                    timeoutMs = 60_000L,
                    remoteModel = RemoteModelInfo(id = modelId, ownedBy = "huggingface")
                ).second
            }
            container.preferences.setHfCloudConsented(true)
            selectActiveModel(model.id)
            _statusBanner.value = "Ready to chat with $label via Hugging Face Router."
        }
    }

    fun updateServer(server: RemoteServerEntity) {
        viewModelScope.launch {
            container.modelRepository.updateServer(server)
        }
    }

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            container.modelRepository.removeServer(serverId)
        }
    }

    fun updateModelSettings(
        modelId: String,
        name: String,
        chatTemplate: String,
        stopWordsRaw: String,
        reasoningOverride: Boolean?,
        supportsEffort: Boolean
    ) {
        viewModelScope.launch {
            val stops = stopWordsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            container.modelRepository.updateModelSettings(
                modelId = modelId,
                name = name,
                chatTemplate = chatTemplate,
                stopWords = stops,
                reasoningOverride = reasoningOverride,
                supportsEffort = supportsEffort
            )
        }
    }

    fun decodeStopWords(model: ModelEntity): String =
        container.modelRepository.decodeStopWords(model).joinToString(", ")
}
