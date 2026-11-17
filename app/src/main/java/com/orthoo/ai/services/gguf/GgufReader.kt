package com.orthoo.ai.services.gguf

import com.orthoo.ai.data.model.GgufMetadata
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight binary GGUF header & metadata parser in pure Kotlin.
 * Reads magic ("GGUF"), format version, tensor count, and key-value metadata
 * (architecture, model name, context length, chat template presence).
 */
object GgufReader {
    private const val GGUF_MAGIC = 0x46554747 // "GGUF" little-endian
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun inspectFile(file: File): GgufMetadata? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            file.inputStream().use { inspectStream(it) }
        }.getOrNull()
    }

    fun inspectStream(input: InputStream): GgufMetadata? {
        val dis = DataInputStream(BufferedInputStream(input))
        val magic = readIntLE(dis)
        if (magic != GGUF_MAGIC) return null

        val version = readIntLE(dis)
        val tensorCount = readLongLE(dis)
        val kvCount = readLongLE(dis)

        var architecture: String? = null
        var modelName: String? = null
        var contextLength: Int? = null
        var hasChatTemplate = false

        val maxKeysToRead = kvCount.coerceAtMost(96)
        for (i in 0 until maxKeysToRead) {
            val key = readString(dis) ?: break
            val valueType = readIntLE(dis)
            when (valueType) {
                TYPE_STRING -> {
                    val value = readString(dis)
                    when {
                        key == "general.architecture" -> architecture = value
                        key == "general.name" -> modelName = value
                        key == "tokenizer.chat_template" -> hasChatTemplate = !value.isNullOrBlank()
                    }
                }
                TYPE_UINT32, TYPE_INT32 -> {
                    val value = readIntLE(dis)
                    if (key.endsWith(".context_length")) {
                        contextLength = value
                    }
                }
                TYPE_UINT64, TYPE_INT64 -> {
                    val value = readLongLE(dis)
                    if (key.endsWith(".context_length") && value in 1..1_000_000) {
                        contextLength = value.toInt()
                    }
                }
                else -> {
                    if (!skipValue(dis, valueType)) break
                }
            }
        }

        return GgufMetadata(
            version = version,
            tensorCount = tensorCount,
            kvCount = kvCount,
            architecture = architecture,
            modelName = modelName,
            contextLength = contextLength,
            hasChatTemplate = hasChatTemplate
        )
    }

    private fun skipValue(dis: DataInputStream, type: Int): Boolean {
        when (type) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> dis.readByte()
            TYPE_UINT16, TYPE_INT16 -> skipBytes(dis, 2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skipBytes(dis, 4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skipBytes(dis, 8)
            TYPE_STRING -> readString(dis) ?: return false
            TYPE_ARRAY -> {
                val elemType = readIntLE(dis)
                val len = readLongLE(dis)
                if (len < 0 || len > 500_000) return false
                for (j in 0 until len) {
                    if (!skipValue(dis, elemType)) return false
                }
            }
            else -> return false
        }
        return true
    }

    private fun skipBytes(dis: DataInputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = dis.skipBytes(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }

    private fun readIntLE(dis: DataInputStream): Int {
        val bytes = ByteArray(4)
        dis.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readLongLE(dis: DataInputStream): Long {
        val bytes = ByteArray(8)
        dis.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readString(dis: DataInputStream): String? {
        val len = readLongLE(dis)
        if (len < 0 || len > 1_048_576) return null
        val bytes = ByteArray(len.toInt())
        dis.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}
