package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.tools.JulieToolType
import com.example.tools.PendingToolAction
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
    data class ToolIntent(val action: PendingToolAction) : VoiceCommandResult
}

/**
 * Wraps Android's on-device SpeechRecognizer API for real-time voice-to-text
 * dictation with live audio RMS feedback and Hindi/Hinglish/English Julie command detection.
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

    fun startListening(languageTag: String = Locale.getDefault().toLanguageTag()) {
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
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
        private val WAKE_PREFIXES = listOf(
            "hey julie,", "hey julie ", "hi julie,", "hi julie ", "ok julie,", "ok julie ",
            "julie,", "julie ", "हाय जुली ", "हेलो जुली ", "ओके जुली ", "जुली "
        )

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
            "ak task banao ",
            "याद दिलाना ",
            "remind me to "
        )

        /**
         * Strips optional "Hey Julie / हाय जुली" wake phrase and detects real Android tool intents,
         * hands-free task creation, or standard conversational prompts.
         */
        fun parseVoiceCommand(transcript: String): VoiceCommandResult {
            var clean = transcript.trim()
            val initialLower = clean.lowercase(Locale.ROOT)
            val wake = WAKE_PREFIXES.firstOrNull { initialLower.startsWith(it) }
            if (wake != null) {
                clean = clean.substring(wake.length).trim()
            }
            if (clean.isEmpty()) return VoiceCommandResult.ChatPrompt(transcript.trim())

            val lower = clean.lowercase(Locale.ROOT)

            // 1. Task / Reminder Creation
            val matchedTaskPrefix = TASK_PREFIXES.firstOrNull { lower.startsWith(it) }
            if (matchedTaskPrefix != null) {
                val body = clean.substring(matchedTaskPrefix.length).trim()
                if (body.isNotBlank()) {
                    val title = body.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                    }.take(72)
                    return VoiceCommandResult.TaskInitiation(title = title, prompt = body)
                }
            }

            // 2. YouTube Search / Open (English, Hindi, Hinglish)
            if (lower.startsWith("search youtube for ") || lower.startsWith("youtube search ") ||
                lower.startsWith("play ") && lower.contains("youtube") ||
                lower.contains("यूट्यूब पर खोजो") || lower.contains("youtube par khojo") ||
                lower.contains("गाना चलाओ") || lower.contains("gaana chalao")
            ) {
                val query = clean
                    .replace(Regex("(?i)(search youtube for|youtube search|on youtube|youtube par khojo|gaana chalao|play)"), "")
                    .replace("यूट्यूब पर खोजो", "")
                    .replace("गाना चलाओ", "")
                    .trim()
                return VoiceCommandResult.ToolIntent(
                    PendingToolAction(
                        type = JulieToolType.SEARCH_YOUTUBE,
                        title = if (query.isBlank()) "Open YouTube" else "YouTube: $query",
                        argument = query
                    )
                )
            }

            // 3. Google Web Search (English, Hindi, Hinglish)
            if (lower.startsWith("search google for ") || lower.startsWith("google search ") ||
                lower.startsWith("google par search ") || lower.contains("गूगल पर खोजो")
            ) {
                val query = clean
                    .replace(Regex("(?i)(search google for|google search|google par search)"), "")
                    .replace("गूगल पर खोजो", "")
                    .trim()
                if (query.isNotBlank()) {
                    return VoiceCommandResult.ToolIntent(
                        PendingToolAction(
                            type = JulieToolType.SEARCH_WEB,
                            title = "Google Search: $query",
                            argument = query
                        )
                    )
                }
            }

            // 4. Open App (YouTube खोलो / Open WhatsApp / Chrome / Maps / Spotify)
            if (lower.startsWith("open ") || lower.endsWith(" kholo") || lower.endsWith(" खोलो")) {
                val appName = clean
                    .replace(Regex("(?i)^open\\s+"), "")
                    .replace(Regex("(?i)\\s+kholo$"), "")
                    .replace("खोलो", "")
                    .trim()
                if (appName.isNotBlank() && appName.length <= 32) {
                    return VoiceCommandResult.ToolIntent(
                        PendingToolAction(
                            type = JulieToolType.LAUNCH_APP,
                            title = "Open $appName",
                            argument = appName
                        )
                    )
                }
            }

            // 5. Phone Dialer (Safe ACTION_DIAL with explicit confirmation)
            if (lower.startsWith("call ") || lower.startsWith("dial ") ||
                lower.contains("कॉल लगाओ") || lower.contains("call lagao")
            ) {
                val target = clean
                    .replace(Regex("(?i)(^call\\s+|^dial\\s+|call lagao)"), "")
                    .replace("कॉल लगाओ", "")
                    .trim()
                return VoiceCommandResult.ToolIntent(
                    PendingToolAction(
                        type = JulieToolType.DIAL_PHONE,
                        title = "Open Dialer ($target)",
                        argument = target
                    )
                )
            }

            // 6. Set Alarm (explicit confirmation via Clock app)
            if (lower.startsWith("set alarm") || lower.contains("अलार्म लगाओ") || lower.contains("alarm lagao")) {
                val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?").find(clean)
                val hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                return VoiceCommandResult.ToolIntent(
                    PendingToolAction(
                        type = JulieToolType.SET_ALARM,
                        title = "Set Alarm %02d:%02d".format(hour, minute),
                        argument = clean,
                        extraHour = hour,
                        extraMinute = minute
                    )
                )
            }

            return VoiceCommandResult.ChatPrompt(clean)
        }
    }
}
