package com.orthoo.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthoo.ai.data.db.BenchmarkResultEntity
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.model.BenchmarkConfig
import com.orthoo.ai.data.model.DeviceInfo
import com.orthoo.ai.data.prefs.AppSettings
import com.orthoo.ai.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BenchmarkViewModel(private val container: AppContainer) : ViewModel() {

    val results: StateFlow<List<BenchmarkResultEntity>> = container.benchmarkRepository.results
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val models: StateFlow<List<ModelEntity>> = container.modelRepository.models
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = container.preferences.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _progressMessage = MutableStateFlow("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var runJob: Job? = null

    init {
        refreshDeviceInfo()
    }

    fun refreshDeviceInfo() {
        viewModelScope.launch {
            _deviceInfo.value = container.hardwareInfoProvider.collectDeviceInfo()
        }
    }

    fun runBenchmark(model: ModelEntity?, config: BenchmarkConfig) {
        if (_isRunning.value) return
        runJob = viewModelScope.launch {
            _isRunning.value = true
            _errorMessage.value = null
            _progressMessage.value = "Preparing benchmark…"

            val currentSettings = settings.value
            val result = container.benchmarkEngine.runBenchmark(
                model = model,
                config = config,
                backendLabel = currentSettings.hardwareBackend.label,
                settings = currentSettings,
                onProgress = { msg -> _progressMessage.value = msg }
            )
            result.onSuccess { entity ->
                container.benchmarkRepository.addResult(entity)
                refreshDeviceInfo()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Benchmark failed"
            }

            _isRunning.value = false
            _progressMessage.value = ""
        }
    }

    fun cancelBenchmark() {
        runJob?.cancel()
        _isRunning.value = false
        _progressMessage.value = ""
    }

    fun deleteResult(id: String) {
        viewModelScope.launch {
            container.benchmarkRepository.removeResult(id)
        }
    }

    fun clearAllResults() {
        viewModelScope.launch {
            container.benchmarkRepository.clearAll()
        }
    }
}
