package com.orthoo.ai.services.network

import com.orthoo.ai.data.model.HuggingFaceFile
import com.orthoo.ai.data.model.HuggingFaceModelSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class HuggingFaceApi(private val client: OkHttpClient) {

    suspend fun searchGgufModels(
        query: String,
        sort: String = "downloads",
        limit: Int = 25,
        authToken: String? = null
    ): Result<List<HuggingFaceModelSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = buildString {
                append("https://huggingface.co/api/models?filter=gguf&sort=")
                append(URLEncoder.encode(sort, "UTF-8"))
                append("&direction=-1&limit=")
                append(limit)
                append("&full=true")
                if (encodedQuery.isNotEmpty()) {
                    append("&search=").append(encodedQuery)
                }
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "orthoo-ai-android/1.23.0")
            if (!authToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $authToken")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Hugging Face returned HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                val list = ArrayList<HuggingFaceModelSummary>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id", obj.optString("modelId"))
                    if (id.isBlank()) continue
                    val author = obj.optString("author", id.substringBefore("/", "huggingface"))
                    val tagsArr = obj.optJSONArray("tags")
                    val tags = buildList {
                        if (tagsArr != null) {
                            for (t in 0 until tagsArr.length()) {
                                add(tagsArr.optString(t))
                            }
                        }
                    }
                    list.add(
                        HuggingFaceModelSummary(
                            id = id,
                            author = author,
                            downloads = obj.optLong("downloads", 0L),
                            likes = obj.optLong("likes", 0L),
                            tags = tags,
                            gated = obj.optBoolean("gated", false),
                            lastModified = obj.optString("lastModified", "")
                        )
                    )
                }
                list
            }
        }
    }

    suspend fun fetchGgufFiles(
        repoId: String,
        authToken: String? = null
    ): Result<List<HuggingFaceFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://huggingface.co/api/models/$repoId/tree/main?recursive=true"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "orthoo-ai-android/1.23.0")
            if (!authToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $authToken")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 401 || resp.code == 403) {
                        throw IllegalStateException("Access denied (HTTP ${resp.code}). Add a valid Hugging Face token in Settings for gated repos.")
                    }
                    throw IllegalStateException("Failed to list files: HTTP ${resp.code}")
                }
                val arr = JSONArray(resp.body?.string().orEmpty())
                val files = mutableListOf<HuggingFaceFile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val path = obj.optString("path")
                    if (path.endsWith(".gguf", ignoreCase = true) && !path.contains("mmproj", ignoreCase = true)) {
                        val size = obj.optJSONObject("lfs")?.optLong("size", 0L)
                            ?: obj.optLong("size", 0L)
                        files.add(
                            HuggingFaceFile(
                                filename = path,
                                sizeBytes = size,
                                downloadUrl = "https://huggingface.co/$repoId/resolve/main/$path",
                                quantization = extractQuantization(path)
                            )
                        )
                    }
                }
                files.sortedBy { it.sizeBytes }
            }
        }
    }

    suspend fun verifyToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://huggingface.co/api/whoami-v2")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("User-Agent", "orthoo-ai-android/1.23.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Invalid token (HTTP ${resp.code})")
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                json.optString("name", json.optString("fullname", "Authenticated User"))
            }
        }
    }

    private fun extractQuantization(filename: String): String {
        val regex = Regex("""(Q[0-9]_[A-Z0-9_]+|IQ[0-9]_[A-Z0-9_]+|F16|BF16|F32)""", RegexOption.IGNORE_CASE)
        return regex.find(filename)?.value?.uppercase() ?: "GGUF"
    }
}
