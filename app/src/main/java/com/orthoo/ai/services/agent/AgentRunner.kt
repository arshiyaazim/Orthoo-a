package com.orthoo.ai.services.agent

import com.orthoo.ai.data.db.ChatMessageEntity
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.AgentUiStatus
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.ModelOrigin
import com.orthoo.ai.data.model.ReasoningIntent
import com.orthoo.ai.data.model.ServerType
import com.orthoo.ai.data.model.ToolCall
import com.orthoo.ai.data.model.ToolOutcome
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.data.repository.ChatRepository
import com.orthoo.ai.data.repository.ModelRepository
import com.orthoo.ai.data.repository.PalRepository
import com.orthoo.ai.services.gguf.GgufReader
import com.orthoo.ai.services.network.OpenAIClient
import com.orthoo.ai.services.network.WireMessage
import com.orthoo.ai.services.talents.TalentRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class AgentRunState(
    val status: AgentUiStatus = AgentUiStatus.IDLE,
    val activeToolNames: List<String> = emptyList(),
    val statusDetail: String = ""
)

class AgentRunner(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val palRepository: PalRepository,
    private val openAIClient: OpenAIClient,
    private val talentRegistry: TalentRegistry
) {
    private val _state = MutableStateFlow(AgentRunState())
    val state: StateFlow<AgentRunState> = _state.asStateFlow()

    suspend fun runTurn(
        sessionId: String,
        model: ModelEntity,
        pal: PalEntity?,
        completionParams: CompletionParams,
        reasoning: ReasoningIntent?,
        settings: AppSettings
    ) {
        val assistantMsgId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        _state.value = AgentRunState(status = AgentUiStatus.PREFILL, statusDetail = "Preparing prompt…")

        try {
            if (model.origin != ModelOrigin.REMOTE.name) {
                handleLocalGgufNotice(sessionId, assistantMsgId, createdAt, model)
                _state.value = AgentRunState(status = AgentUiStatus.DONE)
                return
            }

            val serverId = model.serverId ?: throw IllegalStateException("Remote model is missing server reference.")
            val server = modelRepository.getServerById(serverId)
                ?: throw IllegalStateException("Configured server no longer exists.")
            val serverType = runCatching { ServerType.valueOf(server.serverType) }.getOrDefault(ServerType.UNKNOWN)
            val apiKey = server.apiKey ?: if (serverType == ServerType.HUGGING_FACE) settings.hfToken.takeIf { it.isNotBlank() } else null

            val enabledTalents = pal?.let { palRepository.decodeTalents(it) } ?: emptyList()
            val toolsJson = if (enabledTalents.isNotEmpty() && model.supportsToolUse) {
                talentRegistry.toToolDefinitions(enabledTalents)
            } else null

            val wireHistory = buildWireMessages(sessionId, pal, settings)
            val allToolCalls = mutableListOf<ToolCall>()
            val allToolOutcomes = mutableListOf<ToolOutcome>()
            val combinedText = StringBuilder()
            val combinedReasoning = StringBuilder()
            var totalTokensPredicted = 0
            var lastTps: Double? = null
            var firstTtftMs: Long? = null

            val maxSteps = 5
            for (step in 0 until maxSteps) {
                _state.value = AgentRunState(status = AgentUiStatus.STREAMING_TEXT)
                val stepPrefix = if (combinedText.isNotEmpty()) "\n\n" else ""

                val result = openAIClient.streamChatCompletion(
                    serverUrl = server.url,
                    apiKey = apiKey,
                    serverType = serverType,
                    modelId = model.remoteModelId ?: model.name,
                    messages = wireHistory,
                    params = completionParams,
                    reasoning = reasoning,
                    tools = toolsJson,
                    timeoutMs = server.timeoutMs
                ) { chunk ->
                    val currentText = combinedText.toString() + (if (chunk.content.isNotEmpty()) stepPrefix + chunk.content else "")
                    val currentReasoning = (combinedReasoning.toString() + chunk.reasoningContent).takeIf { it.isNotBlank() }
                    if (chunk.toolCalls.isNotEmpty()) {
                        _state.value = AgentRunState(
                            status = AgentUiStatus.GENERATING_TOOL_CALL,
                            activeToolNames = chunk.toolCalls.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
                        )
                    } else {
                        _state.value = AgentRunState(status = AgentUiStatus.STREAMING_TEXT)
                    }
                    chatRepository.updateMessageStreaming(
                        messageId = assistantMsgId,
                        sessionId = sessionId,
                        text = currentText,
                        reasoningContent = currentReasoning,
                        toolCalls = allToolCalls + chunk.toolCalls,
                        toolOutcomes = allToolOutcomes,
                        modelId = model.id,
                        modelName = model.name,
                        createdAt = createdAt
                    )
                }

                if (result.content.isNotEmpty()) {
                    if (combinedText.isNotEmpty()) combinedText.append("\n\n")
                    combinedText.append(result.content)
                }
                if (!result.reasoningContent.isNullOrBlank()) {
                    if (combinedReasoning.isNotEmpty()) combinedReasoning.append("\n\n")
                    combinedReasoning.append(result.reasoningContent)
                }
                totalTokensPredicted += result.tokensPredicted
                lastTps = result.tokensPerSecond ?: lastTps
                if (firstTtftMs == null) firstTtftMs = result.ttftMs

                if (result.toolCalls.isEmpty()) {
                    break
                }

                allToolCalls.addAll(result.toolCalls)
                wireHistory.add(
                    WireMessage(
                        role = "assistant",
                        content = result.content.ifEmpty { null },
                        toolCalls = result.toolCalls
                    )
                )

                _state.value = AgentRunState(
                    status = AgentUiStatus.EXECUTING_TOOL,
                    activeToolNames = result.toolCalls.map { it.name }
                )

                for (tc in result.toolCalls) {
                    val engine = talentRegistry.get(tc.name)
                    val outcome = if (engine != null) {
                        engine.execute(tc.id, tc.argumentsJson, settings)
                    } else {
                        ToolOutcome(tc.id, tc.name, "Unknown tool: ${tc.name}", isError = true)
                    }
                    allToolOutcomes.add(outcome)
                    wireHistory.add(
                        WireMessage(
                            role = "tool",
                            content = outcome.output,
                            toolCallId = tc.id,
                            name = tc.name
                        )
                    )
                }

                chatRepository.updateMessageStreaming(
                    messageId = assistantMsgId,
                    sessionId = sessionId,
                    text = combinedText.toString(),
                    reasoningContent = combinedReasoning.toString().takeIf { it.isNotBlank() },
                    toolCalls = allToolCalls,
                    toolOutcomes = allToolOutcomes,
                    modelId = model.id,
                    modelName = model.name,
                    createdAt = createdAt
                )
            }

            chatRepository.updateMessageStreaming(
                messageId = assistantMsgId,
                sessionId = sessionId,
                text = combinedText.toString().ifBlank { "(Completed with tool output)" },
                reasoningContent = combinedReasoning.toString().takeIf { it.isNotBlank() },
                toolCalls = allToolCalls,
                toolOutcomes = allToolOutcomes,
                tokensPerSecond = lastTps,
                ttftMs = firstTtftMs,
                tokensPredicted = totalTokensPredicted,
                modelId = model.id,
                modelName = model.name,
                createdAt = createdAt
            )
            _state.value = AgentRunState(status = AgentUiStatus.DONE)
        } catch (e: CancellationException) {
            _state.value = AgentRunState(status = AgentUiStatus.IDLE)
            throw e
        } catch (e: Exception) {
            _state.value = AgentRunState(status = AgentUiStatus.FAILED, statusDetail = e.message.orEmpty())
            chatRepository.updateMessageStreaming(
                messageId = assistantMsgId,
                sessionId = sessionId,
                text = "Generation failed: ${e.message ?: "Unknown error"}",
                modelId = model.id,
                modelName = model.name,
                isError = true,
                createdAt = createdAt
            )
        }
    }

    private suspend fun handleLocalGgufNotice(
        sessionId: String,
        assistantMsgId: String,
        createdAt: Long,
        model: ModelEntity
    ) {
        val file = model.localPath?.let { File(it) }
        val meta = file?.let { GgufReader.inspectFile(it) }
        val metaSummary = if (meta != null) {
            "\n\n**Verified GGUF Header:**\n" +
                "- Format: GGUF v${meta.version}\n" +
                "- Architecture: `${meta.architecture ?: "unknown"}`\n" +
                "- Tensors: ${meta.tensorCount}\n" +
                "- Context length: ${meta.contextLength ?: model.contextLength}"
        } else {
            ""
        }
        val notice = buildString {
            append("**${model.name}** is stored on-device")
            if (file != null && file.exists()) {
                append(" (`${file.name}`).")
            } else {
                append(".")
            }
            append(metaSummary)
            append(
                "\n\nNative C++ `llama.cpp` JNI bindings (`libllama.so`) are not bundled in this pure-Kotlin build, so local GGUF token inference cannot execute directly here yet. " +
                    "To chat with live streaming inference and tool use right now, add a **Remote Server** (Ollama, llama-server, LM Studio, or Hugging Face Router) via **Models → + → Add Remote Model**."
            )
        }
        chatRepository.updateMessageStreaming(
            messageId = assistantMsgId,
            sessionId = sessionId,
            text = notice,
            modelId = model.id,
            modelName = model.name,
            createdAt = createdAt
        )
    }

    private suspend fun buildWireMessages(
        sessionId: String,
        pal: PalEntity?,
        settings: AppSettings
    ): MutableList<WireMessage> {
        val list = mutableListOf<WireMessage>()
        if (pal != null) {
            val renderedPrompt = palRepository.renderSystemPrompt(pal)
            if (renderedPrompt.isNotBlank()) {
                list.add(WireMessage(role = "system", content = renderedPrompt))
            }
        }
        val history = chatRepository.getMessages(sessionId)
        for (msg in history) {
            if (msg.isError) continue
            val images = chatRepository.decodeImageUris(msg)
            val content = if (msg.role == "assistant" && settings.globalCompletionParams.includeThinkingInContext && !msg.reasoningContent.isNullOrBlank()) {
                "<think>\n${msg.reasoningContent}\n</think>\n${msg.text}"
            } else {
                msg.text
            }
            list.add(WireMessage(role = msg.role, content = content, imageUris = images))
        }
        return list
    }
}
