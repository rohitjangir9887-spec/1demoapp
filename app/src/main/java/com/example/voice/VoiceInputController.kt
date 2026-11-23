package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "VoiceInputController"

data class VoiceInputState(
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val audioRmsDb: Float = 0f,
    val errorMessage: String? = null
)

sealed interface VoiceCommandResult {
    data class ChatPrompt(val text: String) : VoiceCommandResult
    data class TaskInitiation(val title: String, val prompt: String) : VoiceCommandResult
}

/**
 * Wraps Android's on-device SpeechRecognizer API for real-time voice-to-text
 * dictation with live audio RMS feedback and hands-free task command detection.
 */
class VoiceInputController(
    private val context: Context,
    private val onFinalTranscript: (String) -> Unit,
    private val onFallbackToActivityRecognizer: () -> Unit
) {
    private val _state = MutableStateFlow(VoiceInputState())
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        clearError()
        if (!isAvailable()) {
            Log.w(TAG, "SpeechRecognizer service unavailable; falling back to RecognizerIntent.")
            onFallbackToActivityRecognizer()
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            _state.value = VoiceInputState(
                isListening = true,
                partialTranscript = "",
                audioRmsDb = 0f,
                errorMessage = null
            )
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _state.value = VoiceInputState(
                isListening = false,
                errorMessage = e.localizedMessage ?: "Unable to start voice recognition."
            )
            onFallbackToActivityRecognizer()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        }
        _state.value = _state.value.copy(isListening = false, audioRmsDb = 0f)
    }

    fun cancelListening() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling SpeechRecognizer: ${e.message}")
        }
        _state.value = VoiceInputState(isListening = false)
    }

    fun clearError() {
        if (_state.value.errorMessage != null) {
            _state.value = _state.value.copy(errorMessage = null)
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(isListening = true, errorMessage = null)
        }

        override fun onBeginningOfSpeech() {
            _state.value = _state.value.copy(isListening = true)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize typical speech RMS dB (-2..10) into 0f..1f range for waveform animation
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.value = _state.value.copy(audioRmsDb = normalized)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(isListening = false, audioRmsDb = 0f)
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check your microphone."
                SpeechRecognizer.ERROR_CLIENT -> null
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during voice recognition."
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the mic and speak clearly."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Try again in a moment."
                SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error. Please retry."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic to try again."
                else -> "Voice recognition failed (code $error)."
            }
            _state.value = _state.value.copy(
                isListening = false,
                audioRmsDb = 0f,
                errorMessage = message
            )
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull()?.trim().orEmpty()
            _state.value = VoiceInputState(isListening = false)
            if (transcript.isNotBlank()) {
                onFinalTranscript(transcript)
            } else {
                _state.value = _state.value.copy(
                    errorMessage = "Didn't catch any words. Tap the mic to try again."
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotBlank()) {
                _state.value = _state.value.copy(partialTranscript = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        private val TASK_PREFIXES = listOf(
            "create task ",
            "create a task ",
            "add task ",
            "add a task ",
            "start task ",
            "start a task ",
            "run task ",
            "schedule task ",
            "new task ",
            "task banao ",
            "ek task banao ",
            "ak task banao "
        )

        /**
         * Parses spoken transcript to detect hands-free task initiation intents
         * vs. standard conversational AI prompts.
         */
        fun parseVoiceCommand(transcript: String): VoiceCommandResult {
            val clean = transcript.trim()
            val lower = clean.lowercase(Locale.getDefault())
            val matchedPrefix = TASK_PREFIXES.firstOrNull { lower.startsWith(it) }
            if (matchedPrefix != null) {
                val body = clean.substring(matchedPrefix.length).trim()
                if (body.isNotBlank()) {
                    val title = body.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }.take(72)
                    return VoiceCommandResult.TaskInitiation(title = title, prompt = body)
                }
            }
            return VoiceCommandResult.ChatPrompt(clean)
        }
    }
}
