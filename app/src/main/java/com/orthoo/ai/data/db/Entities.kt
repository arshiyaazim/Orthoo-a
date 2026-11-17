package com.orthoo.ai.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val activePalId: String? = null,
    val modelId: String? = null,
    val isPinned: Boolean = false,
    val thinkingEnabled: Boolean = true,
    val reasoningEffort: String? = null,
    val completionSettingsJson: String? = null
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user", "assistant", "system"
    val text: String,
    val reasoningContent: String? = null,
    val imageUrisJson: String? = null,
    val toolCallsJson: String? = null,
    val toolOutcomesJson: String? = null,
    val modelId: String? = null,
    val modelName: String? = null,
    val tokensPerSecond: Double? = null,
    val ttftMs: Long? = null,
    val tokensPredicted: Int? = null,
    val isError: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "pals")
data class PalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val palType: String, // "ASSISTANT" or "ROLEPLAY"
    val systemPrompt: String,
    val originalSystemPrompt: String,
    val isSystemPromptChanged: Boolean = false,
    val defaultModelId: String? = null,
    val colorPrimaryHex: Long = 0xFF4F46E5,
    val colorSecondaryHex: Long = 0xFF10B981,
    val greetingText: String = "",
    val suggestedPromptsJson: String = "[]",
    val parametersJson: String = "{}",
    val parameterSchemaJson: String = "[]",
    val talentsJson: String = "[]",
    val completionSettingsJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String,
    val type: String,
    val origin: String, // PRESET, HF, LOCAL, REMOTE
    val sizeBytes: Long,
    val paramsCount: Long,
    val filename: String,
    val localPath: String? = null,
    val downloadUrl: String = "",
    val hfUrl: String = "",
    val isDownloaded: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadStatus: String = "NOT_DOWNLOADED",
    val serverId: String? = null,
    val remoteModelId: String? = null,
    val supportsThinking: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsToolUse: Boolean = false,
    val supportsEffort: Boolean = false,
    val contextLength: Int = 4096,
    val chatTemplate: String = "",
    val stopWordsJson: String = "[]",
    val isReasoningOverride: Boolean? = null,
    val description: String = "",
    val quantization: String = ""
)

@Entity(tableName = "remote_servers")
data class RemoteServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val serverType: String, // ServerType name
    val apiKey: String? = null,
    val timeoutMs: Long = 30_000L,
    val createdAt: Long
)

@Entity(tableName = "benchmark_results")
data class BenchmarkResultEntity(
    @PrimaryKey val id: String,
    val modelId: String,
    val modelName: String,
    val filename: String,
    val backend: String,
    val ppTokens: Int,
    val tgTokens: Int,
    val repeatCount: Int,
    val ppAvg: Double,
    val ppStd: Double,
    val tgAvg: Double,
    val tgStd: Double,
    val peakMemoryMb: Double,
    val usedMemoryMb: Double,
    val totalMemoryMb: Double,
    val wallTimeMs: Long,
    val timestamp: Long
)
