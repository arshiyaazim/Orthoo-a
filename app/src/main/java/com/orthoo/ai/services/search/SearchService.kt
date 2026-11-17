package com.orthoo.ai.services.search

import com.orthoo.ai.data.model.SearchProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

class SearchService(private val client: OkHttpClient) {

    suspend fun search(
        query: String,
        provider: SearchProviderId,
        resultCount: Int = 5,
        wikipediaLanguage: String = "en",
        searxngUrl: String = "",
        apiKey: String = ""
    ): Result<List<SearchHit>> = withContext(Dispatchers.IO) {
        runCatching {
            when (provider) {
                SearchProviderId.WIKIPEDIA -> searchWikipedia(query, wikipediaLanguage, resultCount)
                SearchProviderId.SEARXNG -> searchSearxng(query, searxngUrl, resultCount)
                SearchProviderId.TAVILY -> searchTavily(query, apiKey, resultCount)
                SearchProviderId.BRAVE -> searchBrave(query, apiKey, resultCount)
                SearchProviderId.EXA -> searchExa(query, apiKey, resultCount)
            }
        }
    }

    suspend fun readUrl(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = URI(rawUrl.trim())
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                throw IllegalArgumentException("Only http/https URLs are permitted.")
            }
            val host = uri.host ?: throw IllegalArgumentException("Missing hostname")
            if (isPrivateOrLocalHost(host)) {
                throw SecurityException("Local or private network addresses are blocked for privacy.")
            }

            // Use Wikipedia plain-text summary API for Wikipedia links
            if (host.endsWith("wikipedia.org") && uri.path.startsWith("/wiki/")) {
                val lang = host.substringBefore(".")
                val title = uri.path.removePrefix("/wiki/")
                val apiUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$title"
                val req = Request.Builder().url(apiUrl).header("User-Agent", "orthoo-ai-android/1.23.0").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val extract = json.optString("extract")
                        if (extract.isNotBlank()) {
                            return@runCatching "${json.optString("title")}\n\n$extract"
                        }
                    }
                }
            }

            val req = Request.Builder()
                .url(uri.toString())
                .header("User-Agent", "orthoo-ai-android/1.23.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val finalHost = resp.request.url.host
                if (isPrivateOrLocalHost(finalHost)) {
                    throw SecurityException("Redirect to private network address blocked.")
                }
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Failed to fetch page (HTTP ${resp.code})")
                }
                val html = resp.body?.string().orEmpty()
                htmlToPlainText(html).take(8_000)
            }
        }
    }

    private fun searchWikipedia(query: String, lang: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val safeLang = lang.ifBlank { "en" }
        val url = "https://$safeLang.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=$limit&format=json&utf8=1"
        val req = Request.Builder().url(url).header("User-Agent", "orthoo-ai-android/1.23.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Wikipedia HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            val results = json.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            return buildList {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val title = item.optString("title")
                    val slug = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                    val snippet = htmlToPlainText(item.optString("snippet"))
                    add(
                        SearchHit(
                            title = title,
                            url = "https://$safeLang.wikipedia.org/wiki/$slug",
                            snippet = snippet
                        )
                    )
                }
            }
        }
    }

    private fun searchSearxng(query: String, baseUrl: String, limit: Int): List<SearchHit> {
        if (baseUrl.isBlank()) throw IllegalStateException("Configure your SearXNG server URL in Settings.")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${baseUrl.trimEnd('/')}/search?q=$encoded&format=json"
        val req = Request.Builder().url(url).header("User-Agent", "orthoo-ai-android/1.23.0").build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 403) throw IllegalStateException("SearXNG server returned 403 (JSON format must be enabled in settings.yml).")
            if (!resp.isSuccessful) throw IllegalStateException("SearXNG HTTP ${resp.code}")
            val results = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return emptyList()
            return buildList {
                for (i in 0 until minOf(limit, results.length())) {
                    val item = results.getJSONObject(i)
                    add(
                        SearchHit(
                            title = item.optString("title"),
                            url = item.optString("url"),
                            snippet = item.optString("content")
                        )
                    )
                }
            }
        }
    }

    private fun searchTavily(query: String, apiKey: String, limit: Int): List<SearchHit> {
        if (apiKey.isBlank()) throw IllegalStateException("Tavily API key is required.")
        val body = JSONObject()
            .put("api_key", apiKey)
            .put("query", query)
            .put("max_results", limit)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url("https://api.tavily.com/search").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Tavily HTTP ${resp.code}")
            val results = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return emptyList()
            return buildList {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    add(SearchHit(item.optString("title"), item.optString("url"), item.optString("content")))
                }
            }
        }
    }

    private fun searchBrave(query: String, apiKey: String, limit: Int): List<SearchHit> {
        if (apiKey.isBlank()) throw IllegalStateException("Brave Search API key is required.")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=$limit")
            .header("X-Subscription-Token", apiKey)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Brave HTTP ${resp.code}")
            val results = JSONObject(resp.body?.string().orEmpty())
                .optJSONObject("web")?.optJSONArray("results") ?: return emptyList()
            return buildList {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    add(SearchHit(item.optString("title"), item.optString("url"), item.optString("description")))
                }
            }
        }
    }

    private fun searchExa(query: String, apiKey: String, limit: Int): List<SearchHit> {
        if (apiKey.isBlank()) throw IllegalStateException("Exa API key is required.")
        val body = JSONObject()
            .put("query", query)
            .put("numResults", limit)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("https://api.exa.ai/search")
            .header("x-api-key", apiKey)
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Exa HTTP ${resp.code}")
            val results = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return emptyList()
            return buildList {
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    add(SearchHit(item.optString("title"), item.optString("url"), item.optString("text")))
                }
            }
        }
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower.endsWith(".local") || lower == "::1") return true
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress
    }

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
