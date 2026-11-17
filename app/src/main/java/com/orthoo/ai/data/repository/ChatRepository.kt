package com.orthoo.ai.data.repository

import com.orthoo.ai.data.db.ChatMessageDao
import com.orthoo.ai.data.db.ChatMessageEntity
import com.orthoo.ai.data.db.ChatSessionDao
import com.orthoo.ai.data.db.ChatSessionEntity
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.ToolCall
import com.orthoo.ai.data.model.ToolOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepository(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val sessions: Flow<List<ChatSessionEntity>> = sessionDao.observeAll()

    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        messageDao.observeForSession(sessionId)

    suspend fun getSession(id: String): ChatSessionEntity? = sessionDao.getById(id)

    suspend fun getMessages(sessionId: String): List<ChatMessageEntity> =
        messageDao.getForSession(sessionId)

    suspend fun createSession(
        title: String = "New Chat",
        palId: String? = null,
        modelId: String? = null
    ): ChatSessionEntity {
        val now = System.currentTimeMillis()
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            activePalId = palId,
            modelId = modelId
        )
        sessionDao.upsert(session)
        return session
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(existing.copy(title = title.trim().ifEmpty { existing.title }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun togglePinSession(sessionId: String) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(existing.copy(isPinned = !existing.isPinned))
    }

    suspend fun setSessionPal(sessionId: String, palId: String?) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(existing.copy(activePalId = palId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setSessionModel(sessionId: String, modelId: String?) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(existing.copy(modelId = modelId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setSessionThinking(sessionId: String, enabled: Boolean, effort: String?) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(existing.copy(thinkingEnabled = enabled, reasoningEffort = effort))
    }

    suspend fun updateSessionCompletionParams(sessionId: String, params: CompletionParams) {
        val existing = sessionDao.getById(sessionId) ?: return
        sessionDao.upsert(existing.copy(completionSettingsJson = json.encodeToString(params)))
    }

    fun parseCompletionParams(session: ChatSessionEntity?): CompletionParams? {
        return session?.completionSettingsJson?.let { raw ->
            runCatching { json.decodeFromString<CompletionParams>(raw) }.getOrNull()
        }
    }

    suspend fun duplicateSession(sessionId: String): ChatSessionEntity? {
        val orig = sessionDao.getById(sessionId) ?: return null
        val now = System.currentTimeMillis()
        val copy = orig.copy(
            id = UUID.randomUUID().toString(),
            title = "${orig.title} (Copy)",
            createdAt = now,
            updatedAt = now,
            isPinned = false
        )
        sessionDao.upsert(copy)
        val messages = messageDao.getForSession(sessionId)
        for (msg in messages) {
            messageDao.upsert(msg.copy(id = UUID.randomUUID().toString(), sessionId = copy.id))
        }
        return copy
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteById(sessionId)
    }

    suspend fun deleteSessions(ids: List<String>) {
        sessionDao.deleteByIds(ids)
    }

    suspend fun clearSessionMessages(sessionId: String) {
        messageDao.clearSession(sessionId)
    }

    suspend fun deleteMessagesFrom(sessionId: String, fromTimestamp: Long) {
        messageDao.deleteFromTimestamp(sessionId, fromTimestamp)
    }

    suspend fun upsertMessage(message: ChatMessageEntity) {
        messageDao.upsert(message)
        sessionDao.getById(message.sessionId)?.let { session ->
            val updatedTitle = if (session.title == "New Chat" && message.role == "user" && message.text.isNotBlank()) {
                message.text.trim().replace("\n", " ").take(42)
            } else {
                session.title
            }
            sessionDao.upsert(session.copy(title = updatedTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateMessageStreaming(
        messageId: String,
        sessionId: String,
        text: String,
        reasoningContent: String? = null,
        toolCalls: List<ToolCall>? = null,
        toolOutcomes: List<ToolOutcome>? = null,
        tokensPerSecond: Double? = null,
        ttftMs: Long? = null,
        tokensPredicted: Int? = null,
        modelId: String? = null,
        modelName: String? = null,
        isError: Boolean = false,
        createdAt: Long
    ) {
        messageDao.upsert(
            ChatMessageEntity(
                id = messageId,
                sessionId = sessionId,
                role = "assistant",
                text = text,
                reasoningContent = reasoningContent,
                toolCallsJson = toolCalls?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
                toolOutcomesJson = toolOutcomes?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
                modelId = modelId,
                modelName = modelName,
                tokensPerSecond = tokensPerSecond,
                ttftMs = ttftMs,
                tokensPredicted = tokensPredicted,
                isError = isError,
                createdAt = createdAt
            )
        )
    }

    fun decodeImageUris(msg: ChatMessageEntity): List<String> =
        msg.imageUrisJson?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()

    fun encodeImageUris(uris: List<String>): String? =
        uris.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    fun decodeToolCalls(msg: ChatMessageEntity): List<ToolCall> =
        msg.toolCallsJson?.let { runCatching { json.decodeFromString<List<ToolCall>>(it) }.getOrNull() } ?: emptyList()

    fun decodeToolOutcomes(msg: ChatMessageEntity): List<ToolOutcome> =
        msg.toolOutcomesJson?.let { runCatching { json.decodeFromString<List<ToolOutcome>>(it) }.getOrNull() } ?: emptyList()
}
