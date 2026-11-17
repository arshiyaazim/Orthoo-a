package com.orthoo.ai.services.talents

import com.orthoo.ai.data.model.ToolOutcome
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.services.search.SearchService
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class TalentMetadata(
    val name: String,
    val title: String,
    val description: String
)

interface TalentEngine {
    val metadata: TalentMetadata
    fun toToolDefinition(): JSONObject
    suspend fun execute(callId: String, argumentsJson: String, settings: AppSettings): ToolOutcome
}

class CalculateTalent : TalentEngine {
    override val metadata = TalentMetadata(
        name = "calculate",
        title = "Calculator",
        description = "Evaluate arithmetic and scientific math expressions accurately."
    )

    override fun toToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", metadata.name)
                .put("description", "Evaluate a mathematical expression (supports +, -, *, /, ^, %, sqrt, sin, cos, tan, log, abs, pi, e).")
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject().put(
                                "expression",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "Math expression to evaluate, e.g. '5000 * (1 + 0.06)^10'")
                            )
                        )
                        .put("required", JSONArray().put("expression"))
                )
        )

    override suspend fun execute(callId: String, argumentsJson: String, settings: AppSettings): ToolOutcome {
        val expr = runCatching { JSONObject(argumentsJson).optString("expression") }.getOrDefault("").trim()
        if (expr.isEmpty()) {
            return ToolOutcome(callId, metadata.name, "Missing expression argument", isError = true)
        }
        return runCatching {
            val value = MathParser(expr).parse()
            val formatted = if (value % 1.0 == 0.0 && abs(value) < 1e15) {
                value.toLong().toString()
            } else {
                String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
            }
            ToolOutcome(callId, metadata.name, "$expr = $formatted")
        }.getOrElse { err ->
            ToolOutcome(callId, metadata.name, "Could not evaluate '$expr': ${err.message}", isError = true)
        }
    }

    private class MathParser(private val input: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                when {
                    eat('*'.code) -> x *= parsePower()
                    eat('/'.code) -> {
                        val d = parsePower()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        x /= d
                    }
                    eat('%'.code) -> x %= parsePower()
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            var x = parseFactor()
            if (eat('^'.code)) x = x.pow(parsePower())
            return x
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing parenthesis")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val name = input.substring(startPos, pos).lowercase(Locale.US)
                when (name) {
                    "pi" -> x = PI
                    "e" -> x = kotlin.math.E
                    else -> {
                        val arg = parseFactor()
                        x = when (name) {
                            "sqrt" -> sqrt(arg)
                            "sin" -> sin(arg)
                            "cos" -> cos(arg)
                            "tan" -> tan(arg)
                            "log", "ln" -> ln(arg)
                            "abs" -> abs(arg)
                            else -> throw IllegalArgumentException("Unknown function: $name")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected syntax at position $pos")
            }
            return x
        }
    }
}

class DatetimeTalent : TalentEngine {
    override val metadata = TalentMetadata(
        name = "datetime",
        title = "Date & Time",
        description = "Read the device's current local date, time, day of week, and timezone."
    )

    override fun toToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", metadata.name)
                .put("description", "Get the current local date, time, timezone, and ISO timestamp.")
                .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()))
        )

    override suspend fun execute(callId: String, argumentsJson: String, settings: AppSettings): ToolOutcome {
        val now = ZonedDateTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm:ss z (XXX)", Locale.US))
        return ToolOutcome(callId, metadata.name, formatted)
    }
}

class RenderHtmlTalent : TalentEngine {
    override val metadata = TalentMetadata(
        name = "render_html",
        title = "Render HTML Preview",
        description = "Render interactive HTML/CSS/JS widgets or mini-apps directly inside the chat."
    )

    override fun toToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", metadata.name)
                .put("description", "Render a self-contained HTML snippet in a sandboxed preview card in chat.")
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", JSONObject().put("type", "string"))
                                .put("html", JSONObject().put("type", "string"))
                        )
                        .put("required", JSONArray().put("html"))
                )
        )

    override suspend fun execute(callId: String, argumentsJson: String, settings: AppSettings): ToolOutcome {
        val obj = runCatching { JSONObject(argumentsJson) }.getOrNull()
        val html = obj?.optString("html").orEmpty()
        val title = obj?.optString("title", "HTML Preview").orEmpty().ifBlank { "HTML Preview" }
        if (html.isBlank()) {
            return ToolOutcome(callId, metadata.name, "Empty HTML content", isError = true)
        }
        return ToolOutcome(
            toolCallId = callId,
            toolName = metadata.name,
            output = "Rendered interactive preview: $title",
            htmlPreview = html
        )
    }
}

class WebSearchTalent(private val searchService: SearchService) : TalentEngine {
    override val metadata = TalentMetadata(
        name = "web_search",
        title = "Web Search (opt-in)",
        description = "Search Wikipedia, SearXNG, or configured search providers when internet search is enabled in Settings."
    )

    override fun toToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", metadata.name)
                .put("description", "Search the web using the user's configured search provider.")
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject().put(
                                "query",
                                JSONObject().put("type", "string").put("description", "Search query")
                            )
                        )
                        .put("required", JSONArray().put("query"))
                )
        )

    override suspend fun execute(callId: String, argumentsJson: String, settings: AppSettings): ToolOutcome {
        if (!settings.searchConsented) {
            return ToolOutcome(
                callId,
                metadata.name,
                "Internet Search is turned off. Enable it in Settings → Internet Search.",
                isError = true
            )
        }
        val query = runCatching { JSONObject(argumentsJson).optString("query") }.getOrDefault("").trim()
        if (query.isBlank()) {
            return ToolOutcome(callId, metadata.name, "Search query is empty.", isError = true)
        }
        return searchService.search(
            query = query,
            provider = settings.searchProvider,
            resultCount = settings.searchResultCount,
            wikipediaLanguage = settings.wikipediaLanguage,
            searxngUrl = settings.searxngUrl,
            apiKey = settings.searchApiKey
        ).fold(
            onSuccess = { hits ->
                if (hits.isEmpty()) {
                    ToolOutcome(callId, metadata.name, "No results found for \"$query\".")
                } else {
                    val summary = hits.mapIndexed { idx, hit ->
                        "${idx + 1}. ${hit.title}\n   ${hit.url}\n   ${hit.snippet}"
                    }.joinToString("\n\n")
                    ToolOutcome(callId, metadata.name, summary)
                }
            },
            onFailure = { err ->
                ToolOutcome(callId, metadata.name, "Search error: ${err.message}", isError = true)
            }
        )
    }
}

class ReadUrlTalent(private val searchService: SearchService) : TalentEngine {
    override val metadata = TalentMetadata(
        name = "read_url",
        title = "Read Web Page (opt-in)",
        description = "Fetch and extract clean plain text from an external web URL on-device."
    )

    override fun toToolDefinition(): JSONObject = JSONObject()
        .put("type", "function")
        .put(
            "function",
            JSONObject()
                .put("name", metadata.name)
                .put("description", "Read plain text from an http/https webpage URL.")
                .put(
                    "parameters",
                    JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject().put(
                                "url",
                                JSONObject().put("type", "string").put("description", "HTTP or HTTPS URL to read")
                            )
                        )
                        .put("required", JSONArray().put("url"))
                )
        )

    override suspend fun execute(callId: String, argumentsJson: String, settings: AppSettings): ToolOutcome {
        if (!settings.searchConsented) {
            return ToolOutcome(
                callId,
                metadata.name,
                "Internet Search & Page Reading are turned off in Settings.",
                isError = true
            )
        }
        val url = runCatching { JSONObject(argumentsJson).optString("url") }.getOrDefault("").trim()
        if (url.isBlank()) {
            return ToolOutcome(callId, metadata.name, "Missing URL argument", isError = true)
        }
        return searchService.readUrl(url).fold(
            onSuccess = { text -> ToolOutcome(callId, metadata.name, text) },
            onFailure = { err -> ToolOutcome(callId, metadata.name, "Page read failed: ${err.message}", isError = true) }
        )
    }
}

class TalentRegistry(searchService: SearchService) {
    private val engines: List<TalentEngine> = listOf(
        CalculateTalent(),
        DatetimeTalent(),
        RenderHtmlTalent(),
        WebSearchTalent(searchService),
        ReadUrlTalent(searchService)
    )

    fun allTalents(): List<TalentEngine> = engines

    fun get(name: String): TalentEngine? = engines.find { it.metadata.name == name }

    fun toToolDefinitions(enabledNames: List<String>): JSONArray {
        val arr = JSONArray()
        enabledNames.mapNotNull { get(it) }.forEach { arr.put(it.toToolDefinition()) }
        return arr
    }
}
