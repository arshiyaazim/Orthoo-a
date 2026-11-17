package com.orthoo.ai.services.network

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.ReasoningIntent
import com.orthoo.ai.data.model.RemoteModelInfo
import com.orthoo.ai.data.model.ServerType
import com.orthoo.ai.data.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class WireMessage(
    val role: String,
    val content: String? = null,
    val imageUris: List<String> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null
)

data class StreamChunkUpdate(
    val content: String,
    val reasoningContent: String,
    val toolCalls: List<ToolCall>
)

data class OpenAICompletionResult(
    val content: String,
    val reasoningContent: String?,
    val toolCalls: List<ToolCall>,
    val tokensPredicted: Int,
    val tokensPerSecond: Double?,
    val ttftMs: Long?,
    val interrupted: Boolean = false
)

data class ServerProbeResult(
    val models: List<RemoteModelInfo>,
    val detectedType: ServerType
)

class OpenAIClient(
    private val context: Context,
    private val baseClient: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun probeServer(
        serverUrl: String,
        apiKey: String? = null,
        timeoutMs: Long = 15_000L
    ): Result<ServerProbeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = serverUrl.trim().trimEnd('/')
            val client = baseClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

            val reqBuilder = Request.Builder()
                .url("$normalized/v1/models")
                .get()
            if (!apiKey.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 401) throw IllegalStateException("Unauthorized: Invalid or missing API key")
                    throw IllegalStateException("Server error: HTTP ${resp.code}")
                }
                val serverHeader = resp.header("Server").orEmpty()
                val bodyStr = resp.body?.string().orEmpty()
                val root = JSONObject(bodyStr)
                val dataArr = root.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<RemoteModelInfo>()
                for (i in 0 until dataArr.length()) {
                    val item = dataArr.getJSONObject(i)
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    val ownedBy = item.optString("owned_by").takeIf { it.isNotBlank() }
                    val modalities = item.optJSONObject("architecture")?.optJSONArray("input_modalities")
                    var vision = false
                    if (modalities != null) {
                        for (m in 0 until modalities.length()) {
                            if (modalities.optString(m) == "image") vision = true
                        }
                    }
                    val ctxLen = item.optJSONObject("meta")?.optInt("n_ctx", 0)?.takeIf { it > 0 }
                    models.add(
                        RemoteModelInfo(
                            id = id,
                            ownedBy = ownedBy,
                            contextLength = ctxLen,
                            supportsVision = vision
                        )
                    )
                }

                val detected = detectServerType(normalized, serverHeader, models, client)
                ServerProbeResult(models = models, detectedType = detected)
            }
        }
    }

    private fun detectServerType(
        normalizedUrl: String,
        serverHeader: String,
        models: List<RemoteModelInfo>,
        client: OkHttpClient
    ): ServerType {
        if (normalizedUrl.contains("router.huggingface.co", ignoreCase = true)) {
            return ServerType.HUGGING_FACE
        }
        if (normalizedUrl.contains("api.openai.com", ignoreCase = true)) {
            return ServerType.OPENAI
        }
        if (serverHeader.equals("llama.cpp", ignoreCase = true)) {
            return ServerType.LLAMA_CPP
        }
        if (models.any { it.ownedBy == "organization_owner" }) {
            return ServerType.LM_STUDIO
        }
        runCatching {
            val ollamaCheck = Request.Builder().url(normalizedUrl).get().build()
            client.newCall(ollamaCheck).execute().use { resp ->
                if (resp.isSuccessful && resp.body?.string()?.trim() == "Ollama is running") {
                    return ServerType.OLLAMA
                }
            }
        }
        return ServerType.UNKNOWN
    }

    suspend fun streamChatCompletion(
        serverUrl: String,
        apiKey: String?,
        serverType: ServerType,
        modelId: String,
        messages: List<WireMessage>,
        params: CompletionParams,
        reasoning: ReasoningIntent?,
        tools: JSONArray?,
        timeoutMs: Long = 60_000L,
        onChunk: suspend (StreamChunkUpdate) -> Unit
    ): OpenAICompletionResult = withContext(Dispatchers.IO) {
        val normalized = serverUrl.trim().trimEnd('/')
        val endpoint = if (normalized.endsWith("/v1")) {
            "$normalized/chat/completions"
        } else {
            "$normalized/v1/chat/completions"
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("stream", true)
            put("messages", buildMessagesArray(messages))
            put("temperature", params.temperature.toDouble())
            put("top_p", params.topP.toDouble())
            if (params.nPredict > 0) {
                if (serverType == ServerType.HUGGING_FACE) {
                    put("max_tokens", params.nPredict)
                } else {
                    put("max_completion_tokens", params.nPredict)
                }
            }
            if (params.seed >= 0) {
                put("seed", params.seed)
            }
            if (serverType == ServerType.LLAMA_CPP) {
                put("top_k", params.topK)
                put("min_p", params.minP.toDouble())
                put("repeat_penalty", params.penaltyRepeat.toDouble())
                put("repeat_last_n", params.penaltyLastN)
            }
            applyReasoningExtras(this, serverType, reasoning)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }

        val client = baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Accept", "text/event-stream")
        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        val startTimeNs = System.nanoTime()
        var firstTokenMs: Long? = null
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        val toolCallAccumulators = sortedMapOf<Int, MutableToolCall>()
        var tokensPredicted = 0
        var serverTps: Double? = null

        val call = client.newCall(reqBuilder.build())
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                val errMsg = runCatching {
                    JSONObject(errBody).optJSONObject("error")?.optString("message")
                        ?: errBody
                }.getOrDefault(errBody)
                throw IllegalStateException("Server HTTP ${response.code}: ${errMsg.take(240)}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue

                    val chunkJson = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    chunkJson.optJSONObject("timings")?.let { timings ->
                        val predictedPerSec = timings.optDouble("predicted_per_second", Double.NaN)
                        if (!predictedPerSec.isNaN() && predictedPerSec > 0.0) {
                            serverTps = predictedPerSec
                        }
                    }

                    val choices = chunkJson.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue

                    val contentDelta = delta.optString("content", "")
                    val reasoningDelta = delta.optString("reasoning_content", "")
                        .ifEmpty { delta.optString("reasoning", "") }

                    if (contentDelta.isNotEmpty() || reasoningDelta.isNotEmpty()) {
                        if (firstTokenMs == null) {
                            firstTokenMs = (System.nanoTime() - startTimeNs) / 1_000_000L
                        }
                        if (contentDelta.isNotEmpty()) {
                            contentBuilder.append(contentDelta)
                            tokensPredicted++
                        }
                        if (reasoningDelta.isNotEmpty()) {
                            reasoningBuilder.append(reasoningDelta)
                            tokensPredicted++
                        }
                    }

                    val deltaToolCalls = delta.optJSONArray("tool_calls")
                    if (deltaToolCalls != null) {
                        for (i in 0 until deltaToolCalls.length()) {
                            val tc = deltaToolCalls.getJSONObject(i)
                            val index = tc.optInt("index", i)
                            val acc = toolCallAccumulators.getOrPut(index) { MutableToolCall() }
                            tc.optString("id").takeIf { it.isNotEmpty() }?.let { acc.id = it }
                            val fn = tc.optJSONObject("function")
                            if (fn != null) {
                                fn.optString("name").takeIf { it.isNotEmpty() }?.let { acc.name = it }
                                fn.optString("arguments").takeIf { it.isNotEmpty() }?.let { acc.args.append(it) }
                            }
                        }
                    }

                    onChunk(
                        StreamChunkUpdate(
                            content = contentBuilder.toString(),
                            reasoningContent = reasoningBuilder.toString(),
                            toolCalls = toolCallAccumulators.values.map { it.toToolCall() }
                        )
                    )
                }
            }
        }

        val elapsedSec = ((System.nanoTime() - startTimeNs) / 1_000_000_000.0).coerceAtLeast(0.001)
        val computedTps = serverTps ?: if (tokensPredicted > 0) tokensPredicted / elapsedSec else null

        OpenAICompletionResult(
            content = contentBuilder.toString(),
            reasoningContent = reasoningBuilder.toString().takeIf { it.isNotBlank() },
            toolCalls = toolCallAccumulators.values.map { it.toToolCall() }.filter { it.name.isNotBlank() },
            tokensPredicted = tokensPredicted,
            tokensPerSecond = computedTps,
            ttftMs = firstTokenMs
        )
    }

    private fun applyReasoningExtras(
        body: JSONObject,
        serverType: ServerType,
        reasoning: ReasoningIntent?
    ) {
        if (reasoning == null) return
        when (serverType) {
            ServerType.LLAMA_CPP -> {
                body.put("reasoning_format", "auto")
                if (!reasoning.enabled) {
                    body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                } else if (!reasoning.effort.isNullOrBlank()) {
                    body.put("chat_template_kwargs", JSONObject().put("reasoning_effort", reasoning.effort))
                }
            }
            ServerType.LM_STUDIO -> {
                if (!reasoning.enabled) {
                    body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                }
            }
            ServerType.OLLAMA -> {
                if (!reasoning.enabled) {
                    body.put("reasoning_effort", "none")
                }
            }
            ServerType.OPENAI, ServerType.HUGGING_FACE -> {
                if (!reasoning.effort.isNullOrBlank()) {
                    body.put("reasoning_effort", reasoning.effort)
                }
            }
            ServerType.VLLM -> {
                if (!reasoning.enabled) {
                    body.put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                } else if (!reasoning.effort.isNullOrBlank()) {
                    body.put("chat_template_kwargs", JSONObject().put("reasoning_effort", reasoning.effort))
                }
            }
            ServerType.UNKNOWN -> Unit
        }
    }

    private fun buildMessagesArray(messages: List<WireMessage>): JSONArray {
        val arr = JSONArray()
        for (msg in messages) {
            val obj = JSONObject().put("role", msg.role)
            if (msg.role == "tool") {
                obj.put("tool_call_id", msg.toolCallId.orEmpty())
                if (!msg.name.isNullOrBlank()) obj.put("name", msg.name)
                obj.put("content", msg.content.orEmpty())
                arr.put(obj)
                continue
            }

            if (msg.toolCalls.isNotEmpty()) {
                val tcArr = JSONArray()
                for (tc in msg.toolCalls) {
                    tcArr.put(
                        JSONObject()
                            .put("id", tc.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", tc.name)
                                    .put("arguments", tc.argumentsJson)
                            )
                    )
                }
                obj.put("tool_calls", tcArr)
                if (!msg.content.isNullOrEmpty()) {
                    obj.put("content", msg.content)
                } else {
                    obj.put("content", JSONObject.NULL)
                }
                arr.put(obj)
                continue
            }

            if (msg.imageUris.isNotEmpty()) {
                val parts = JSONArray()
                if (!msg.content.isNullOrBlank()) {
                    parts.put(JSONObject().put("type", "text").put("text", msg.content))
                }
                for (uriStr in msg.imageUris) {
                    encodeUriToDataUri(uriStr)?.let { dataUri ->
                        parts.put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", dataUri))
                        )
                    }
                }
                obj.put("content", parts)
            } else {
                obj.put("content", msg.content.orEmpty())
            }
            arr.put(obj)
        }
        return arr
    }

    private fun encodeUriToDataUri(uriStr: String): String? {
        if (uriStr.startsWith("data:") || uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
            return uriStr
        }
        return runCatching {
            val uri = Uri.parse(uriStr)
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > 12 * 1024 * 1024) return@runCatching null
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mime;base64,$b64"
            }
        }.getOrNull()
    }

    private class MutableToolCall {
        var id: String = "call_${System.nanoTime()}"
        var name: String = ""
        val args = StringBuilder()
        fun toToolCall() = ToolCall(id = id, name = name, argumentsJson = args.toString())
    }
}
