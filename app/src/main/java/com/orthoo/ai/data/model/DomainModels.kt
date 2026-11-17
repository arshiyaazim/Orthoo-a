package com.orthoo.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ModelOrigin {
    PRESET,
    HF,
    LOCAL,
    REMOTE
}

@Serializable
enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
enum class ServerType(val displayName: String) {
    LLAMA_CPP("llama.cpp"),
    LM_STUDIO("LM Studio"),
    OLLAMA("Ollama"),
    OPENAI("OpenAI"),
    VLLM("vLLM"),
    HUGGING_FACE("Hugging Face"),
    UNKNOWN("OpenAI-Compatible")
}

@Serializable
enum class SearchProviderId(
    val displayName: String,
    val requiresKey: Boolean,
    val description: String
) {
    WIKIPEDIA("Wikipedia", false, "Free encyclopedic search across Wikipedia editions. No API key required."),
    SEARXNG("SearXNG", false, "Self-hosted metasearch instance with JSON format enabled."),
    TAVILY("Tavily", true, "AI-optimized web search API (requires API key)."),
    BRAVE("Brave Search", true, "Independent web index API (requires API key)."),
    EXA("Exa", true, "Neural semantic web search API (requires API key).")
}

@Serializable
enum class HardwareBackend(val id: String, val label: String) {
    CPU("cpu", "CPU"),
    GPU("gpu", "GPU (OpenCL)"),
    HEXAGON("hexagon", "NPU (Hexagon)")
}

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Serializable
enum class PalType(val label: String) {
    ASSISTANT("Assistant"),
    ROLEPLAY("Roleplay")
}

@Serializable
enum class ReasoningEffort(val wireValue: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High")
}

@Serializable
data class CompletionParams(
    val nPredict: Int = 1024,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    val typicalP: Float = 1.0f,
    val penaltyLastN: Int = 64,
    val penaltyRepeat: Float = 1.05f,
    val penaltyFreq: Float = 0.0f,
    val penaltyPresent: Float = 0.0f,
    val mirostat: Int = 0,
    val mirostatTau: Float = 5.0f,
    val mirostatEta: Float = 0.1f,
    val seed: Int = -1,
    val enableThinking: Boolean = true,
    val includeThinkingInContext: Boolean = false
)

@Serializable
data class ReasoningIntent(
    val enabled: Boolean,
    val effort: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

@Serializable
data class ToolOutcome(
    val toolCallId: String,
    val toolName: String,
    val output: String,
    val isError: Boolean = false,
    val htmlPreview: String? = null
)

enum class AgentUiStatus {
    IDLE,
    PREFILL,
    STREAMING_TEXT,
    GENERATING_TOOL_CALL,
    EXECUTING_TOOL,
    DONE,
    FAILED
}

@Serializable
data class PalParameterSchema(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val required: Boolean = false
)

data class PalColorPair(
    val id: String,
    val primaryHex: Long,
    val secondaryHex: Long
)

val PAL_COLOR_PAIRS = listOf(
    PalColorPair("indigo-emerald", 0xFF4F46E5, 0xFF10B981),
    PalColorPair("light-mode", 0xFF858585, 0xFF333333),
    PalColorPair("blue-light", 0xFF70A6F5, 0xFFD9F2FF),
    PalColorPair("blue-mid", 0xFF2444DA, 0xFF70A6F5),
    PalColorPair("green-light", 0xFFA2D29E, 0xFFCBFFDC),
    PalColorPair("pink-light", 0xFFFFBBFE, 0xFFFAEFFE),
    PalColorPair("orange-beige", 0xFFFFAD50, 0xFFF1EAD9),
    PalColorPair("pink-rose", 0xFF9E204F, 0xFFF6E1EA),
    PalColorPair("navy-purple", 0xFF253375, 0xFF8B94C1)
)

data class GpuInfo(
    val renderer: String,
    val vendor: String,
    val version: String,
    val gpuType: String,
    val hasAdreno: Boolean,
    val hasMali: Boolean,
    val hasPowerVr: Boolean,
    val supportsOpenCl: Boolean
)

data class CpuInfo(
    val cores: Int,
    val socModel: String,
    val hardware: String,
    val maxFreqMhz: Int?,
    val features: List<String>,
    val hasFp16: Boolean,
    val hasDotProd: Boolean,
    val hasSve: Boolean,
    val hasI8mm: Boolean
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val abis: List<String>,
    val totalMemoryBytes: Long,
    val availableMemoryBytes: Long,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val chipset: String,
    val wifiIpv4: String?,
    val cpuInfo: CpuInfo,
    val gpuInfo: GpuInfo
)

data class HuggingFaceFile(
    val filename: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val quantization: String
)

data class HuggingFaceModelSummary(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val tags: List<String>,
    val gated: Boolean,
    val lastModified: String,
    val files: List<HuggingFaceFile> = emptyList()
)

data class RemoteModelInfo(
    val id: String,
    val ownedBy: String? = null,
    val contextLength: Int? = null,
    val supportsVision: Boolean = false
)

data class DownloadProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class GgufMetadata(
    val version: Int,
    val tensorCount: Long,
    val kvCount: Long,
    val architecture: String?,
    val modelName: String?,
    val contextLength: Int?,
    val hasChatTemplate: Boolean
)

data class BenchmarkConfig(
    val pp: Int = 512,
    val tg: Int = 128,
    val nr: Int = 3
)

object AppTopics {
    val onboardingTopics = listOf(
        "smart_chat" to "Smart Chat",
        "coding" to "Coding",
        "creative_writing" to "Creative Writing",
        "education" to "Learning & Study",
        "roleplay" to "Roleplay"
    )
}
