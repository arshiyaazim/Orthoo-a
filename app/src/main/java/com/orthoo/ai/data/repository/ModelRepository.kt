package com.orthoo.ai.data.repository

import com.orthoo.ai.data.db.ModelDao
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.OrthooDatabase
import com.orthoo.ai.data.db.RemoteServerDao
import com.orthoo.ai.data.db.RemoteServerEntity
import com.orthoo.ai.data.model.DownloadStatus
import com.orthoo.ai.data.model.ModelOrigin
import com.orthoo.ai.data.model.RemoteModelInfo
import com.orthoo.ai.data.model.ServerType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ModelRepository(
    private val modelDao: ModelDao,
    private val serverDao: RemoteServerDao
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val models: Flow<List<ModelEntity>> = modelDao.observeAll()
    val servers: Flow<List<RemoteServerEntity>> = serverDao.observeAll()

    suspend fun getModelById(id: String): ModelEntity? = modelDao.getById(id)

    suspend fun getServerById(id: String): RemoteServerEntity? = serverDao.getById(id)

    suspend fun upsertModel(model: ModelEntity) = modelDao.upsert(model)

    suspend fun markDownloadProgress(modelId: String, progress: Int, status: DownloadStatus, localPath: String? = null) {
        val model = modelDao.getById(modelId) ?: return
        modelDao.upsert(
            model.copy(
                downloadProgress = progress,
                downloadStatus = status.name,
                isDownloaded = status == DownloadStatus.COMPLETED,
                localPath = localPath ?: model.localPath
            )
        )
    }

    suspend fun deleteLocalFileAndReset(modelId: String) {
        val model = modelDao.getById(modelId) ?: return
        model.localPath?.let { path ->
            runCatching {
                File(path).delete()
                File("$path.part").delete()
            }
        }
        if (model.origin == ModelOrigin.PRESET.name) {
            modelDao.upsert(
                model.copy(
                    isDownloaded = false,
                    downloadProgress = 0,
                    downloadStatus = DownloadStatus.NOT_DOWNLOADED.name,
                    localPath = null
                )
            )
        } else {
            modelDao.deleteById(modelId)
        }
    }

    suspend fun resetPresetModels() {
        modelDao.deleteUndownloadedPresets()
        modelDao.insertAllIgnore(OrthooDatabase.defaultPresetModels())
    }

    suspend fun updateModelSettings(
        modelId: String,
        name: String,
        chatTemplate: String,
        stopWords: List<String>,
        reasoningOverride: Boolean?,
        supportsEffort: Boolean
    ) {
        val existing = modelDao.getById(modelId) ?: return
        modelDao.upsert(
            existing.copy(
                name = name.trim().ifEmpty { existing.name },
                chatTemplate = chatTemplate,
                stopWordsJson = json.encodeToString(stopWords),
                isReasoningOverride = reasoningOverride,
                supportsThinking = reasoningOverride ?: existing.supportsThinking,
                supportsEffort = supportsEffort
            )
        )
    }

    fun decodeStopWords(model: ModelEntity): List<String> =
        runCatching { json.decodeFromString<List<String>>(model.stopWordsJson) }.getOrDefault(emptyList())

    suspend fun addRemoteServerAndModel(
        serverName: String,
        serverUrl: String,
        serverType: ServerType,
        apiKey: String?,
        timeoutMs: Long,
        remoteModel: RemoteModelInfo
    ): Pair<RemoteServerEntity, ModelEntity> {
        val server = RemoteServerEntity(
            id = UUID.randomUUID().toString(),
            name = serverName.trim().ifEmpty { serverType.displayName },
            url = serverUrl.trim().trimEnd('/'),
            serverType = serverType.name,
            apiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() },
            timeoutMs = timeoutMs.coerceIn(5_000L, 300_000L),
            createdAt = System.currentTimeMillis()
        )
        serverDao.upsert(server)
        val model = addModelForExistingServer(server, remoteModel)
        return server to model
    }

    suspend fun addModelForExistingServer(
        server: RemoteServerEntity,
        remoteModel: RemoteModelInfo
    ): ModelEntity {
        val lower = remoteModel.id.lowercase()
        val looksLikeReasoning = lower.contains("r1") || lower.contains("qwq") ||
            lower.contains("qwen3") || lower.contains("thinking") || lower.contains("o1") || lower.contains("o3")
        val model = ModelEntity(
            id = "${server.id}/${remoteModel.id}",
            name = remoteModel.id,
            author = server.name,
            type = ServerType.valueOf(server.serverType).displayName,
            origin = ModelOrigin.REMOTE.name,
            sizeBytes = 0L,
            paramsCount = 0L,
            filename = remoteModel.id,
            isDownloaded = true,
            downloadStatus = DownloadStatus.COMPLETED.name,
            serverId = server.id,
            remoteModelId = remoteModel.id,
            supportsThinking = looksLikeReasoning,
            supportsVision = remoteModel.supportsVision || lower.contains("vision") || lower.contains("-vl") || lower.contains("4o"),
            supportsToolUse = true,
            contextLength = remoteModel.contextLength ?: 16384,
            description = "Remote model served by ${server.name} (${server.url})"
        )
        modelDao.upsert(model)
        return model
    }

    suspend fun updateServer(server: RemoteServerEntity) {
        serverDao.upsert(server)
    }

    suspend fun removeServer(serverId: String) {
        modelDao.deleteByServerId(serverId)
        serverDao.deleteById(serverId)
    }
}
