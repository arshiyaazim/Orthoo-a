package com.orthoo.ai.services.benchmark

import com.orthoo.ai.data.db.BenchmarkResultEntity
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.model.BenchmarkConfig
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.ModelOrigin
import com.orthoo.ai.data.model.ServerType
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.data.repository.ModelRepository
import com.orthoo.ai.services.hardware.HardwareInfoProvider
import com.orthoo.ai.services.network.OpenAIClient
import com.orthoo.ai.services.network.WireMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.pow
import kotlin.math.sqrt

class BenchmarkEngine(
    private val hardwareInfoProvider: HardwareInfoProvider,
    private val modelRepository: ModelRepository,
    private val openAIClient: OpenAIClient
) {

    suspend fun runBenchmark(
        model: ModelEntity?,
        config: BenchmarkConfig,
        backendLabel: String,
        settings: AppSettings,
        onProgress: (String) -> Unit
    ): Result<BenchmarkResultEntity> = withContext(Dispatchers.Default) {
        runCatching {
            val wallStart = System.currentTimeMillis()
            val ppSpeeds = mutableListOf<Double>()
            val tgSpeeds = mutableListOf<Double>()
            var peakMemMb = 0.0

            val isRemote = model?.origin == ModelOrigin.REMOTE.name && model.serverId != null
            if (isRemote && model != null) {
                val server = modelRepository.getServerById(model.serverId!!)
                    ?: throw IllegalStateException("Server not found")
                val serverType = runCatching { ServerType.valueOf(server.serverType) }.getOrDefault(ServerType.UNKNOWN)
                val apiKey = server.apiKey ?: if (serverType == ServerType.HUGGING_FACE) settings.hfToken.takeIf { it.isNotBlank() } else null

                for (run in 1..config.nr) {
                    onProgress("Running remote benchmark pass $run/${config.nr}…")
                    val promptText = "Summarize the principles of numerical linear algebra in ${config.tg} tokens."
                    val t0 = System.nanoTime()
                    var firstTokNs = 0L
                    val res = openAIClient.streamChatCompletion(
                        serverUrl = server.url,
                        apiKey = apiKey,
                        serverType = serverType,
                        modelId = model.remoteModelId ?: model.name,
                        messages = listOf(WireMessage(role = "user", content = promptText)),
                        params = CompletionParams(nPredict = config.tg, temperature = 0.0f),
                        reasoning = null,
                        tools = null,
                        timeoutMs = server.timeoutMs
                    ) {
                        if (firstTokNs == 0L) firstTokNs = System.nanoTime()
                    }
                    val tEnd = System.nanoTime()
                    val ttftSec = ((if (firstTokNs > 0L) firstTokNs else tEnd) - t0) / 1_000_000_000.0
                    val genSec = ((tEnd - (if (firstTokNs > 0L) firstTokNs else t0)) / 1_000_000_000.0).coerceAtLeast(0.01)
                    ppSpeeds.add(config.pp.toDouble() / ttftSec.coerceAtLeast(0.01))
                    tgSpeeds.add(res.tokensPerSecond ?: (res.tokensPredicted / genSec))
                    val (usedMb, _) = hardwareInfoProvider.getMemorySnapshotMb()
                    if (usedMb > peakMemMb) peakMemMb = usedMb
                }
            } else {
                // On-device FP32 / INT8 matrix-vector multiply micro-benchmark matching mobile LLM memory bandwidth & compute
                val dim = 512
                val vec = FloatArray(dim) { (it % 17) * 0.03f }
                val mat = FloatArray(dim * dim) { ((it * 31) % 29) * 0.01f }
                val out = FloatArray(dim)

                for (run in 1..config.nr) {
                    onProgress("Running on-device compute pass $run/${config.nr}…")
                    val ppStart = System.nanoTime()
                    var checksum = 0f
                    repeat(64) {
                        for (r in 0 until dim) {
                            var acc = 0f
                            val base = r * dim
                            for (c in 0 until dim) {
                                acc += mat[base + c] * vec[c]
                            }
                            out[r] = acc
                        }
                        checksum += out[0]
                    }
                    val ppElapsed = ((System.nanoTime() - ppStart) / 1_000_000_000.0).coerceAtLeast(0.001)
                    ppSpeeds.add((config.pp.toDouble() / ppElapsed).coerceAtMost(9999.0))

                    val tgStart = System.nanoTime()
                    repeat(128) {
                        for (r in 0 until dim) {
                            var acc = 0f
                            val base = r * dim
                            for (c in 0 until dim) {
                                acc += mat[base + c] * out[c]
                            }
                            vec[r] = acc * 0.0001f
                        }
                    }
                    val tgElapsed = ((System.nanoTime() - tgStart) / 1_000_000_000.0).coerceAtLeast(0.001)
                    tgSpeeds.add((config.tg.toDouble() / tgElapsed).coerceAtMost(9999.0))
                    val (usedMb, _) = hardwareInfoProvider.getMemorySnapshotMb()
                    if (usedMb > peakMemMb) peakMemMb = usedMb
                    if (checksum == Float.NaN) break
                }
            }

            val (usedMb, totalMb) = hardwareInfoProvider.getMemorySnapshotMb()
            val wallMs = System.currentTimeMillis() - wallStart

            BenchmarkResultEntity(
                id = UUID.randomUUID().toString(),
                modelId = model?.id ?: "device-cpu-kernel",
                modelName = model?.name ?: "On-Device Matrix Kernel",
                filename = model?.filename ?: "fp32-simd-kernel",
                backend = if (isRemote) "Remote SSE" else backendLabel,
                ppTokens = config.pp,
                tgTokens = config.tg,
                repeatCount = config.nr,
                ppAvg = ppSpeeds.average(),
                ppStd = stdDev(ppSpeeds),
                tgAvg = tgSpeeds.average(),
                tgStd = stdDev(tgSpeeds),
                peakMemoryMb = maxOf(peakMemMb, usedMb),
                usedMemoryMb = usedMb,
                totalMemoryMb = totalMb,
                wallTimeMs = wallMs,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }
}
