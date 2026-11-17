package com.orthoo.ai.ui.viewmodel

import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthoo.ai.data.db.RemoteServerEntity
import com.orthoo.ai.data.model.HardwareBackend
import com.orthoo.ai.data.model.SearchProviderId
import com.orthoo.ai.data.model.ThemeMode
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val servers: StateFlow<List<RemoteServerEntity>> = container.modelRepository.servers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availableVoices: StateFlow<List<Voice>> = container.speechManager.availableVoices

    private val _tokenTestState = MutableStateFlow<String?>(null)
    val tokenTestState: StateFlow<String?> = _tokenTestState.asStateFlow()

    init {
        container.speechManager.initTts()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { container.preferences.setThemeMode(mode) }
    fun setDisplayMemoryUsage(enabled: Boolean) = viewModelScope.launch { container.preferences.setDisplayMemoryUsage(enabled) }
    fun setAutoNavigateToChat(enabled: Boolean) = viewModelScope.launch { container.preferences.setAutoNavigateToChat(enabled) }
    fun setContextSize(size: Int) = viewModelScope.launch { container.preferences.setContextSize(size) }
    fun setNThreads(threads: Int) = viewModelScope.launch { container.preferences.setNThreads(threads) }
    fun setNBatch(batch: Int) = viewModelScope.launch { container.preferences.setNBatch(batch) }
    fun setNGpuLayers(layers: Int) = viewModelScope.launch { container.preferences.setNGpuLayers(layers) }
    fun setFlashAttn(enabled: Boolean) = viewModelScope.launch { container.preferences.setFlashAttn(enabled) }
    fun setUseMmap(enabled: Boolean) = viewModelScope.launch { container.preferences.setUseMmap(enabled) }
    fun setAutoRelease(enabled: Boolean) = viewModelScope.launch { container.preferences.setAutoRelease(enabled) }
    fun setHardwareBackend(backend: HardwareBackend) = viewModelScope.launch { container.preferences.setHardwareBackend(backend) }
    fun setMaxModelSizeBytes(bytes: Long) = viewModelScope.launch { container.preferences.setMaxModelSizeBytes(bytes) }
    fun setHfToken(token: String) = viewModelScope.launch {
        _tokenTestState.value = null
        container.preferences.setHfToken(token)
    }

    fun testHfToken(token: String) {
        if (token.isBlank()) {
            _tokenTestState.value = "Enter a token first."
            return
        }
        viewModelScope.launch {
            _tokenTestState.value = "Verifying…"
            container.huggingFaceApi.verifyToken(token)
                .onSuccess { user -> _tokenTestState.value = "Verified as @$user" }
                .onFailure { err -> _tokenTestState.value = err.message ?: "Verification failed" }
        }
    }

    fun setSearchConsented(consented: Boolean) = viewModelScope.launch { container.preferences.setSearchConsented(consented) }
    fun setSearchProvider(provider: SearchProviderId) = viewModelScope.launch { container.preferences.setSearchProvider(provider) }
    fun setWikipediaLanguage(lang: String) = viewModelScope.launch { container.preferences.setWikipediaLanguage(lang) }
    fun setSearxngUrl(url: String) = viewModelScope.launch { container.preferences.setSearxngUrl(url) }
    fun setSearchResultCount(count: Int) = viewModelScope.launch { container.preferences.setSearchResultCount(count) }
    fun setSearchApiKey(key: String) = viewModelScope.launch { container.preferences.setSearchApiKey(key) }
    fun setTtsAutoSpeak(enabled: Boolean) = viewModelScope.launch { container.preferences.setTtsAutoSpeak(enabled) }
    fun setTtsVoiceName(name: String) = viewModelScope.launch { container.preferences.setTtsVoiceName(name) }

    fun previewVoice(voiceName: String) {
        container.speechManager.speak(
            text = "Hello! I am ready to read responses aloud on your device.",
            preferredVoiceName = voiceName
        )
    }

    fun removeServer(serverId: String) = viewModelScope.launch {
        container.modelRepository.removeServer(serverId)
    }

    fun replayOnboarding() = viewModelScope.launch {
        container.preferences.setOnboardingCompleted(false)
    }
}
