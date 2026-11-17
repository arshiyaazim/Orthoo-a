package com.orthoo.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _pendingDeepLinkRepo = MutableStateFlow<String?>(null)
    val pendingDeepLinkRepo: StateFlow<String?> = _pendingDeepLinkRepo.asStateFlow()

    fun handleDeepLinkRepo(repoId: String?) {
        if (!repoId.isNullOrBlank()) {
            _pendingDeepLinkRepo.value = repoId
        }
    }

    fun consumeDeepLinkRepo() {
        _pendingDeepLinkRepo.value = null
    }

    fun completeOnboarding(topic: String?) {
        viewModelScope.launch {
            container.preferences.setOnboardingCompleted(true, topic)
        }
    }

    fun replayOnboarding() {
        viewModelScope.launch {
            container.preferences.setOnboardingCompleted(false)
        }
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(container) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(container) as T
            modelClass.isAssignableFrom(ModelsViewModel::class.java) -> ModelsViewModel(container) as T
            modelClass.isAssignableFrom(PalsViewModel::class.java) -> PalsViewModel(container) as T
            modelClass.isAssignableFrom(BenchmarkViewModel::class.java) -> BenchmarkViewModel(container) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
