package com.orthoo.ai.services.download

import android.content.Context
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.model.DownloadProgress
import com.orthoo.ai.data.model.DownloadStatus
import com.orthoo.ai.data.repository.ModelRepository
import com.orthoo.ai.services.gguf.GgufReader
import com.orthoo.ai.services.hardware.HardwareInfoProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class ModelDownloadManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val modelRepository: ModelRepository,
    private val hardwareInfoProvider: HardwareInfoProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearError() {
        _lastError.value = null
    }

    fun startDownload(
        model: ModelEntity,
        authToken: String? = null,
        maxAllowedBytes: Long = 0L
    ) {
        if (activeJobs.containsKey(model.id)) return
        if (model.downloadUrl.isBlank()) {
            _lastError.value = "No download URL configured for ${model.name}."
            return
        }
        if (maxAllowedBytes > 0L && model.sizeBytes > maxAllowedBytes) {
            val limitGb = String.format("%.1f GB", maxAllowedBytes / 1_000_000_000.0)
            _lastError.value = "${model.name} exceeds your configured model size limit ($limitGb). Adjust it in Settings."
            return
        }
        val freeBytes = hardwareInfoProvider.getFreeStorageBytes()
        if (model.sizeBytes > 0L && model.sizeBytes > (freeBytes * 0.95).toLong()) {
            _lastError.value = "Not enough free storage space to download ${model.name}."
            return
        }

        val job = scope.launch {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val safeFileName = model.filename.replace("/", "_")
            val destFile = File(modelsDir, safeFileName)
            val partFile = File(modelsDir, "$safeFileName.part")

            _downloads.update {
                it + (model.id to DownloadProgress(model.id, 0L, model.sizeBytes, 0L, DownloadStatus.DOWNLOADING))
            }
            modelRepository.markDownloadProgress(model.id, 0, DownloadStatus.DOWNLOADING)

            try {
                val existingPartBytes = if (partFile.exists()) partFile.length() else 0L
                val reqBuilder = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "orthoo-ai-android/1.23.0")
                if (!authToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${authToken.trim()}")
                }
                if (existingPartBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingPartBytes-")
                }

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) {
                        throw IllegalStateException("Download failed with HTTP ${resp.code}")
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty download response")
                    val append = resp.code == 206 && existingPartBytes > 0L
                    val contentLength = body.contentLength().coerceAtLeast(0L)
                    val totalBytes = when {
                        append && contentLength > 0L -> existingPartBytes + contentLength
                        contentLength > 0L -> contentLength
                        else -> model.sizeBytes
                    }

                    var downloaded = if (append) existingPartBytes else 0L
                    var lastEmitTime = System.currentTimeMillis()
                    var bytesSinceLastEmit = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, append).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                bytesSinceLastEmit += read

                                val now = System.currentTimeMillis()
                                val dt = now - lastEmitTime
                                if (dt >= 350L) {
                                    val speed = (bytesSinceLastEmit * 1000L) / dt.coerceAtLeast(1L)
                                    val pct = if (totalBytes > 0L) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 99) else 0
                                    _downloads.update {
                                        it + (model.id to DownloadProgress(model.id, downloaded, totalBytes, speed, DownloadStatus.DOWNLOADING))
                                    }
                                    modelRepository.markDownloadProgress(model.id, pct, DownloadStatus.DOWNLOADING)
                                    lastEmitTime = now
                                    bytesSinceLastEmit = 0L
                                }
                            }
                            output.flush()
                        }
                    }

                    if (destFile.exists()) destFile.delete()
                    if (!partFile.renameTo(destFile)) {
                        partFile.copyTo(destFile, overwrite = true)
                        partFile.delete()
                    }

                    val ggufMeta = GgufReader.inspectFile(destFile)
                    val updatedModel = model.copy(
                        isDownloaded = true,
                        downloadProgress = 100,
                        downloadStatus = DownloadStatus.COMPLETED.name,
                        localPath = destFile.absolutePath,
                        sizeBytes = destFile.length().takeIf { it > 0L } ?: model.sizeBytes,
                        contextLength = ggufMeta?.contextLength ?: model.contextLength
                    )
                    modelRepository.upsertModel(updatedModel)
                    _downloads.update { it - model.id }
                }
            } catch (e: CancellationException) {
                modelRepository.markDownloadProgress(model.id, 0, DownloadStatus.CANCELLED)
                _downloads.update { it - model.id }
                throw e
            } catch (e: Exception) {
                _lastError.value = "${model.name}: ${e.message ?: "Download failed"}"
                modelRepository.markDownloadProgress(model.id, 0, DownloadStatus.FAILED)
                _downloads.update { it - model.id }
            } finally {
                activeJobs.remove(model.id)
            }
        }
        activeJobs[model.id] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        scope.launch {
            modelRepository.markDownloadProgress(modelId, 0, DownloadStatus.CANCELLED)
            _downloads.update { it - modelId }
        }
    }
}
