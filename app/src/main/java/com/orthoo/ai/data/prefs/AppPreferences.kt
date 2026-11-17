package com.orthoo.ai.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.HardwareBackend
import com.orthoo.ai.data.model.SearchProviderId
import com.orthoo.ai.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orthoo_settings")

data class AppSettings(
    val hasCompletedOnboarding: Boolean = false,
    val onboardingTopic: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val languageCode: String = "en",
    val displayMemoryUsage: Boolean = true,
    val autoNavigateToChat: Boolean = true,
    val activeModelId: String? = null,
    val activeSessionId: String? = null,
    val contextSize: Int = 4096,
    val nThreads: Int = 4,
    val nBatch: Int = 512,
    val nUBatch: Int = 512,
    val nGpuLayers: Int = 0,
    val flashAttn: Boolean = false,
    val useMmap: Boolean = true,
    val autoRelease: Boolean = true,
    val hardwareBackend: HardwareBackend = HardwareBackend.CPU,
    val maxModelSizeBytes: Long = 3_000_000_000L,
    val globalCompletionParams: CompletionParams = CompletionParams(),
    val hfToken: String = "",
    val hfCloudConsented: Boolean = false,
    val hfCloudChatModel: String = "Qwen/Qwen2.5-72B-Instruct",
    val hfCloudCodeModel: String = "Qwen/Qwen2.5-Coder-32B-Instruct",
    val searchConsented: Boolean = false,
    val searchProvider: SearchProviderId = SearchProviderId.WIKIPEDIA,
    val wikipediaLanguage: String = "en",
    val searxngUrl: String = "",
    val searchResultCount: Int = 5,
    val searchApiKey: String = "",
    val ttsAutoSpeak: Boolean = false,
    val ttsSelectedVoiceName: String = "",
    val privacyNoticeAcknowledged: Boolean = false
)

class AppPreferences(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val ONBOARDING_TOPIC = stringPreferencesKey("onboarding_topic")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val DISPLAY_MEM_USAGE = booleanPreferencesKey("display_memory_usage")
        val AUTO_NAVIGATE_TO_CHAT = booleanPreferencesKey("auto_navigate_to_chat")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val N_THREADS = intPreferencesKey("n_threads")
        val N_BATCH = intPreferencesKey("n_batch")
        val N_UBATCH = intPreferencesKey("n_ubatch")
        val N_GPU_LAYERS = intPreferencesKey("n_gpu_layers")
        val FLASH_ATTN = booleanPreferencesKey("flash_attn")
        val USE_MMAP = booleanPreferencesKey("use_mmap")
        val AUTO_RELEASE = booleanPreferencesKey("auto_release")
        val HARDWARE_BACKEND = stringPreferencesKey("hardware_backend")
        val MAX_MODEL_SIZE_BYTES = longPreferencesKey("max_model_size_bytes")
        val GLOBAL_COMPLETION_JSON = stringPreferencesKey("global_completion_json")
        val HF_TOKEN = stringPreferencesKey("hf_token")
        val HF_CLOUD_CONSENTED = booleanPreferencesKey("hf_cloud_consented")
        val HF_CLOUD_CHAT_MODEL = stringPreferencesKey("hf_cloud_chat_model")
        val HF_CLOUD_CODE_MODEL = stringPreferencesKey("hf_cloud_code_model")
        val SEARCH_CONSENTED = booleanPreferencesKey("search_consented")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val WIKIPEDIA_LANG = stringPreferencesKey("wikipedia_lang")
        val SEARXNG_URL = stringPreferencesKey("searxng_url")
        val SEARCH_RESULT_COUNT = intPreferencesKey("search_result_count")
        val SEARCH_API_KEY = stringPreferencesKey("search_api_key")
        val TTS_AUTO_SPEAK = booleanPreferencesKey("tts_auto_speak")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val PRIVACY_NOTICE_ACK = booleanPreferencesKey("privacy_notice_ack")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val completionParams = prefs[Keys.GLOBAL_COMPLETION_JSON]?.let { raw ->
            runCatching { json.decodeFromString<CompletionParams>(raw) }.getOrNull()
        } ?: CompletionParams()

        AppSettings(
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            onboardingTopic = prefs[Keys.ONBOARDING_TOPIC],
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            displayMemoryUsage = prefs[Keys.DISPLAY_MEM_USAGE] ?: true,
            autoNavigateToChat = prefs[Keys.AUTO_NAVIGATE_TO_CHAT] ?: true,
            activeModelId = prefs[Keys.ACTIVE_MODEL_ID],
            activeSessionId = prefs[Keys.ACTIVE_SESSION_ID],
            contextSize = prefs[Keys.CONTEXT_SIZE] ?: 4096,
            nThreads = prefs[Keys.N_THREADS] ?: 4,
            nBatch = prefs[Keys.N_BATCH] ?: 512,
            nUBatch = prefs[Keys.N_UBATCH] ?: 512,
            nGpuLayers = prefs[Keys.N_GPU_LAYERS] ?: 0,
            flashAttn = prefs[Keys.FLASH_ATTN] ?: false,
            useMmap = prefs[Keys.USE_MMAP] ?: true,
            autoRelease = prefs[Keys.AUTO_RELEASE] ?: true,
            hardwareBackend = prefs[Keys.HARDWARE_BACKEND]?.let { runCatching { HardwareBackend.valueOf(it) }.getOrNull() } ?: HardwareBackend.CPU,
            maxModelSizeBytes = prefs[Keys.MAX_MODEL_SIZE_BYTES] ?: 3_000_000_000L,
            globalCompletionParams = completionParams,
            hfToken = prefs[Keys.HF_TOKEN] ?: "",
            hfCloudConsented = prefs[Keys.HF_CLOUD_CONSENTED] ?: false,
            hfCloudChatModel = prefs[Keys.HF_CLOUD_CHAT_MODEL] ?: "Qwen/Qwen2.5-72B-Instruct",
            hfCloudCodeModel = prefs[Keys.HF_CLOUD_CODE_MODEL] ?: "Qwen/Qwen2.5-Coder-32B-Instruct",
            searchConsented = prefs[Keys.SEARCH_CONSENTED] ?: false,
            searchProvider = prefs[Keys.SEARCH_PROVIDER]?.let { runCatching { SearchProviderId.valueOf(it) }.getOrNull() } ?: SearchProviderId.WIKIPEDIA,
            wikipediaLanguage = prefs[Keys.WIKIPEDIA_LANG] ?: "en",
            searxngUrl = prefs[Keys.SEARXNG_URL] ?: "",
            searchResultCount = prefs[Keys.SEARCH_RESULT_COUNT] ?: 5,
            searchApiKey = prefs[Keys.SEARCH_API_KEY] ?: "",
            ttsAutoSpeak = prefs[Keys.TTS_AUTO_SPEAK] ?: false,
            ttsSelectedVoiceName = prefs[Keys.TTS_VOICE_NAME] ?: "",
            privacyNoticeAcknowledged = prefs[Keys.PRIVACY_NOTICE_ACK] ?: false
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean, topic: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = completed
            if (topic != null) {
                prefs[Keys.ONBOARDING_TOPIC] = topic
            }
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDisplayMemoryUsage(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISPLAY_MEM_USAGE] = enabled }
    }

    suspend fun setAutoNavigateToChat(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_NAVIGATE_TO_CHAT] = enabled }
    }

    suspend fun setActiveModelId(modelId: String?) {
        context.dataStore.edit { prefs ->
            if (modelId == null) prefs.remove(Keys.ACTIVE_MODEL_ID) else prefs[Keys.ACTIVE_MODEL_ID] = modelId
        }
    }

    suspend fun setActiveSessionId(sessionId: String?) {
        context.dataStore.edit { prefs ->
            if (sessionId == null) prefs.remove(Keys.ACTIVE_SESSION_ID) else prefs[Keys.ACTIVE_SESSION_ID] = sessionId
        }
    }

    suspend fun setContextSize(size: Int) {
        context.dataStore.edit { it[Keys.CONTEXT_SIZE] = size.coerceIn(256, 131072) }
    }

    suspend fun setNThreads(threads: Int) {
        context.dataStore.edit { it[Keys.N_THREADS] = threads.coerceIn(1, 16) }
    }

    suspend fun setNBatch(batch: Int) {
        context.dataStore.edit { it[Keys.N_BATCH] = batch.coerceIn(32, 4096) }
    }

    suspend fun setNGpuLayers(layers: Int) {
        context.dataStore.edit { it[Keys.N_GPU_LAYERS] = layers.coerceIn(0, 99) }
    }

    suspend fun setFlashAttn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FLASH_ATTN] = enabled }
    }

    suspend fun setUseMmap(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_MMAP] = enabled }
    }

    suspend fun setAutoRelease(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RELEASE] = enabled }
    }

    suspend fun setHardwareBackend(backend: HardwareBackend) {
        context.dataStore.edit { it[Keys.HARDWARE_BACKEND] = backend.name }
    }

    suspend fun setMaxModelSizeBytes(bytes: Long) {
        context.dataStore.edit { it[Keys.MAX_MODEL_SIZE_BYTES] = bytes }
    }

    suspend fun setGlobalCompletionParams(params: CompletionParams) {
        context.dataStore.edit { it[Keys.GLOBAL_COMPLETION_JSON] = json.encodeToString(params) }
    }

    suspend fun setHfToken(token: String) {
        context.dataStore.edit { it[Keys.HF_TOKEN] = token.trim() }
    }

    suspend fun setHfCloudConsented(consented: Boolean) {
        context.dataStore.edit { it[Keys.HF_CLOUD_CONSENTED] = consented }
    }

    suspend fun setHfCloudModels(chatModel: String, codeModel: String) {
        context.dataStore.edit {
            it[Keys.HF_CLOUD_CHAT_MODEL] = chatModel.trim()
            it[Keys.HF_CLOUD_CODE_MODEL] = codeModel.trim()
        }
    }

    suspend fun setSearchConsented(consented: Boolean) {
        context.dataStore.edit { it[Keys.SEARCH_CONSENTED] = consented }
    }

    suspend fun setSearchProvider(provider: SearchProviderId) {
        context.dataStore.edit { it[Keys.SEARCH_PROVIDER] = provider.name }
    }

    suspend fun setWikipediaLanguage(lang: String) {
        context.dataStore.edit { it[Keys.WIKIPEDIA_LANG] = lang.trim().lowercase().ifEmpty { "en" } }
    }

    suspend fun setSearxngUrl(url: String) {
        context.dataStore.edit { it[Keys.SEARXNG_URL] = url.trim() }
    }

    suspend fun setSearchResultCount(count: Int) {
        context.dataStore.edit { it[Keys.SEARCH_RESULT_COUNT] = count.coerceIn(1, 15) }
    }

    suspend fun setSearchApiKey(key: String) {
        context.dataStore.edit { it[Keys.SEARCH_API_KEY] = key.trim() }
    }

    suspend fun setTtsAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TTS_AUTO_SPEAK] = enabled }
    }

    suspend fun setTtsVoiceName(name: String) {
        context.dataStore.edit { it[Keys.TTS_VOICE_NAME] = name }
    }

    suspend fun setPrivacyNoticeAcknowledged(ack: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_NOTICE_ACK] = ack }
    }
}
