package com.orthoo.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        PalEntity::class,
        ModelEntity::class,
        RemoteServerEntity::class,
        BenchmarkResultEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OrthooDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun palDao(): PalDao
    abstract fun modelDao(): ModelDao
    abstract fun remoteServerDao(): RemoteServerDao
    abstract fun benchmarkResultDao(): BenchmarkResultDao

    companion object {
        @Volatile
        private var INSTANCE: OrthooDatabase? = null

        fun getInstance(context: Context): OrthooDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): OrthooDatabase {
            lateinit var db: OrthooDatabase
            db = Room.databaseBuilder(
                context,
                OrthooDatabase::class.java,
                "orthoo_ai.db"
            )
                .addCallback(object : Callback() {
                    override fun onCreate(sqliteDb: SupportSQLiteDatabase) {
                        super.onCreate(sqliteDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedInitialData(db)
                        }
                    }
                })
                .build()
            return db
        }

        suspend fun seedInitialData(db: OrthooDatabase) {
            val now = System.currentTimeMillis()
            db.modelDao().insertAllIgnore(defaultPresetModels())
            db.palDao().insertAllIgnore(defaultStarterPals(now))
        }

        fun defaultPresetModels(): List<ModelEntity> = listOf(
            ModelEntity(
                id = "bartowski/SmolLM2-360M-Instruct-GGUF/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                name = "SmolLM2 360M Instruct",
                author = "HuggingFaceTB",
                type = "SmolLM2",
                origin = "PRESET",
                sizeBytes = 271_000_000L,
                paramsCount = 360_000_000L,
                filename = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                hfUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF",
                supportsThinking = false,
                supportsVision = false,
                supportsToolUse = false,
                contextLength = 8192,
                quantization = "Q4_K_M",
                description = "Ultra-lightweight model that fits comfortably on almost every phone."
            ),
            ModelEntity(
                id = "bartowski/Llama-3.2-1B-Instruct-GGUF/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                name = "Llama 3.2 1B Instruct",
                author = "Meta",
                type = "Llama 3.2",
                origin = "PRESET",
                sizeBytes = 808_000_000L,
                paramsCount = 1_240_000_000L,
                filename = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                hfUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF",
                supportsThinking = false,
                supportsVision = false,
                supportsToolUse = true,
                contextLength = 8192,
                quantization = "Q4_K_M",
                description = "Fast multilingual instruction model from Meta tuned for mobile devices."
            ),
            ModelEntity(
                id = "unsloth/Qwen3-1.7B-GGUF/Qwen3-1.7B-Q4_K_M.gguf",
                name = "Qwen 3 1.7B",
                author = "Alibaba",
                type = "Qwen 3",
                origin = "PRESET",
                sizeBytes = 1_110_000_000L,
                paramsCount = 1_700_000_000L,
                filename = "Qwen3-1.7B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
                hfUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF",
                supportsThinking = true,
                supportsVision = false,
                supportsToolUse = true,
                contextLength = 32768,
                quantization = "Q4_K_M",
                description = "Hybrid thinking & tool-use model with strong reasoning in a compact footprint."
            ),
            ModelEntity(
                id = "unsloth/gemma-3-1b-it-GGUF/gemma-3-1b-it-Q4_K_M.gguf",
                name = "Gemma 3 1B Instruct",
                author = "Google",
                type = "Gemma 3",
                origin = "PRESET",
                sizeBytes = 806_000_000L,
                paramsCount = 1_000_000_000L,
                filename = "gemma-3-1b-it-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
                hfUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF",
                supportsThinking = false,
                supportsVision = false,
                supportsToolUse = true,
                contextLength = 32768,
                quantization = "Q4_K_M",
                description = "Google's compact Gemma 3 model for crisp everyday chat and summarization."
            ),
            ModelEntity(
                id = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                name = "DeepSeek R1 Distill 1.5B",
                author = "DeepSeek",
                type = "DeepSeek R1",
                origin = "PRESET",
                sizeBytes = 1_120_000_000L,
                paramsCount = 1_500_000_000L,
                filename = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                hfUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
                supportsThinking = true,
                supportsVision = false,
                supportsToolUse = false,
                contextLength = 16384,
                quantization = "Q4_K_M",
                description = "Chain-of-thought reasoning specialist distilled from DeepSeek R1."
            ),
            ModelEntity(
                id = "bartowski/Phi-4-mini-instruct-GGUF/Phi-4-mini-instruct-Q4_K_M.gguf",
                name = "Phi-4 Mini Instruct",
                author = "Microsoft",
                type = "Phi-4",
                origin = "PRESET",
                sizeBytes = 2_490_000_000L,
                paramsCount = 3_800_000_000L,
                filename = "Phi-4-mini-instruct-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-Q4_K_M.gguf",
                hfUrl = "https://huggingface.co/bartowski/Phi-4-mini-instruct-GGUF",
                supportsThinking = false,
                supportsVision = false,
                supportsToolUse = true,
                contextLength = 16384,
                quantization = "Q4_K_M",
                description = "Strong math, logic, and coding performance for 6 GB+ RAM devices."
            )
        )

        fun defaultStarterPals(now: Long): List<PalEntity> = listOf(
            PalEntity(
                id = "pal-orthoo-assistant",
                name = "Orthoo Assistant",
                description = "Balanced everyday assistant with calculator, clock, and HTML tools.",
                palType = "ASSISTANT",
                systemPrompt = "You are Orthoo, a thoughtful, concise, privacy-first AI assistant. Answer clearly and use your available tools when calculation, date/time, or web lookup helps.",
                originalSystemPrompt = "You are Orthoo, a thoughtful, concise, privacy-first AI assistant. Answer clearly and use your available tools when calculation, date/time, or web lookup helps.",
                colorPrimaryHex = 0xFF4F46E5,
                colorSecondaryHex = 0xFF10B981,
                greetingText = "Hi! I'm Orthoo. Ask me anything, run calculations, or connect a model to begin.",
                suggestedPromptsJson = """["Explain quantum computing simply","Calculate compound interest on $5,000 at 6% for 10 years","What's today's date and time?","Create an interactive HTML Pomodoro timer"]""",
                talentsJson = """["calculate","datetime","render_html"]""",
                createdAt = now,
                updatedAt = now
            ),
            PalEntity(
                id = "pal-code-architect",
                name = "Code Companion",
                description = "Focused software engineer for Kotlin, TypeScript, Python, and debugging.",
                palType = "ASSISTANT",
                systemPrompt = "You are a senior software engineer. Provide clean, idiomatic, production-ready code with brief, high-signal explanations.",
                originalSystemPrompt = "You are a senior software engineer. Provide clean, idiomatic, production-ready code with brief, high-signal explanations.",
                colorPrimaryHex = 0xFF2444DA,
                colorSecondaryHex = 0xFF70A6F5,
                greetingText = "Ready to review, debug, or build. Paste a snippet or describe what you're shipping.",
                suggestedPromptsJson = """["Write a Kotlin Coroutine Flow debounce helper","Build a responsive HTML dashboard card","Review my regex for email validation"]""",
                talentsJson = """["calculate","render_html"]""",
                createdAt = now - 1000,
                updatedAt = now - 1000
            ),
            PalEntity(
                id = "pal-roleplay-chronicler",
                name = "World Chronicler",
                description = "Interactive roleplay persona with customizable setting, world, and character role.",
                palType = "ROLEPLAY",
                systemPrompt = "Stay in character as {{aiRole}} inside {{world}} at {{location}}. Keep responses vivid, atmospheric, and responsive to the user's choices.",
                originalSystemPrompt = "Stay in character as {{aiRole}} inside {{world}} at {{location}}. Keep responses vivid, atmospheric, and responsive to the user's choices.",
                colorPrimaryHex = 0xFF9E204F,
                colorSecondaryHex = 0xFFF6E1EA,
                greetingText = "The lantern flickers across the stone table. Where does our story begin?",
                suggestedPromptsJson = """["Describe the room around us","Ask about rumors in the harbor","Offer a coded map"]""",
                parametersJson = """{"world":"A rain-slicked clockwork archipelago","location":"The Brass Observatory","aiRole":"Archivist Navigator"}""",
                parameterSchemaJson = """[{"key":"world","label":"World / Setting","placeholder":"e.g., Medieval kingdom","required":true},{"key":"location","label":"Location","placeholder":"e.g., Observatory","required":true},{"key":"aiRole","label":"AI Role","placeholder":"e.g., Wise archivist","required":true}]""",
                talentsJson = """[]""",
                createdAt = now - 2000,
                updatedAt = now - 2000
            )
        )
    }
}
