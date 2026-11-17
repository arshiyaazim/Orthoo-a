package com.orthoo.ai.services.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class SpeechListeningState {
    IDLE,
    LISTENING,
    ERROR
}

class SpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _listeningState = MutableStateFlow(SpeechListeningState.IDLE)
    val listeningState: StateFlow<SpeechListeningState> = _listeningState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError: StateFlow<String?> = _speechError.asStateFlow()

    fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.getDefault()
                _availableVoices.value = runCatching {
                    tts?.voices?.sortedBy { it.name } ?: emptyList()
                }.getOrDefault(emptyList())
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _speakingMessageId.value = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _speakingMessageId.value = null
                    }
                })
            }
        }
    }

    fun speak(text: String, messageId: String? = null, preferredVoiceName: String = "") {
        initTts()
        if (!ttsReady || text.isBlank()) return
        if (preferredVoiceName.isNotBlank()) {
            _availableVoices.value.find { it.name == preferredVoiceName }?.let { voice ->
                tts?.voice = voice
            }
        }
        val cleanText = stripMarkdownForSpeech(text)
        _speakingMessageId.value = messageId
        _isSpeaking.value = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId ?: UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        _speakingMessageId.value = null
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _speechError.value = "Speech recognition service is not available on this device."
            _listeningState.value = SpeechListeningState.ERROR
            return
        }
        _speechError.value = null
        _partialTranscript.value = ""
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _listeningState.value = SpeechListeningState.LISTENING
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    _listeningState.value = SpeechListeningState.IDLE
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        _speechError.value = "Voice recognition error ($error)"
                    }
                }
                override fun onResults(results: Bundle?) {
                    _listeningState.value = SpeechListeningState.IDLE
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty().trim()
                    if (text.isNotEmpty()) {
                        onFinalResult(text)
                    }
                    _partialTranscript.value = ""
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    _partialTranscript.value = matches?.firstOrNull().orEmpty()
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        _listeningState.value = SpeechListeningState.LISTENING
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _listeningState.value = SpeechListeningState.IDLE
    }

    fun clearError() {
        _speechError.value = null
        if (_listeningState.value == SpeechListeningState.ERROR) {
            _listeningState.value = SpeechListeningState.IDLE
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }

    private fun stripMarkdownForSpeech(raw: String): String = raw
        .replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
        .replace(Regex("[#*_`~>]"), "")
        .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}
