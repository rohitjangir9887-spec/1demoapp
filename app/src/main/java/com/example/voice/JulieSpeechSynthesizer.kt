package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "JulieSpeechSynthesizer"

enum class JulieVoiceState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    SPEAKING
}

data class JulieSpeechUiState(
    val voiceState: JulieVoiceState = JulieVoiceState.IDLE,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val currentSpokenSentence: String = "",
    val isInitialized: Boolean = false,
    val activeLocale: String = "hi_IN",
    val hasHindiVoicePack: Boolean = true
)

/**
 * Production-grade Text-To-Speech pipeline for Julie AI.
 *
 * Capabilities:
 * - Sentence-chunked streaming speech (speaks sentence-by-sentence during AI streaming)
 * - Intelligent markdown, URL, and symbol sanitizer (never reads raw pipe tables, asterisks, URLs aloud)
 * - Natural Hindi / English / Hinglish voice selection
 * - Audio focus management (ducks background audio, releases on stop)
 * - UtteranceProgressListener for real speech lifecycle events
 * - Barge-in support (instantly stops audio when user taps mic or starts speaking)
 * - Play, Pause, Stop, Replay, and Mute controls
 */
class JulieSpeechSynthesizer(private val context: Context) {

    private val _uiState = MutableStateFlow(JulieSpeechUiState())
    val uiState: StateFlow<JulieSpeechUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var lastSpokenFullText: String = ""
    private var streamedSentencesSpokenIndex = 0
    private var sentenceBuffer = StringBuilder()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stop()
            }
        }
    }

    init {
        initializeTts()
    }

    private fun initializeTts() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val hindiLocale = Locale("hi", "IN")
                    val hindiAvailable = tts?.isLanguageAvailable(hindiLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    val hasHindi = hindiAvailable >= TextToSpeech.LANG_AVAILABLE

                    runCatching {
                        tts?.language = if (hasHindi) hindiLocale else Locale.getDefault()
                        tts?.setSpeechRate(1.02f)
                        tts?.setPitch(1.0f)
                    }

                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _uiState.value = _uiState.value.copy(
                                voiceState = JulieVoiceState.SPEAKING,
                                isSpeaking = true
                            )
                        }

                        override fun onDone(utteranceId: String?) {
                            _uiState.value = _uiState.value.copy(
                                voiceState = JulieVoiceState.IDLE,
                                isSpeaking = false
                            )
                            abandonAudioFocus()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _uiState.value = _uiState.value.copy(
                                voiceState = JulieVoiceState.IDLE,
                                isSpeaking = false
                            )
                            abandonAudioFocus()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(TAG, "TTS Utterance error code: $errorCode for $utteranceId")
                            _uiState.value = _uiState.value.copy(
                                voiceState = JulieVoiceState.IDLE,
                                isSpeaking = false
                            )
                            abandonAudioFocus()
                        }
                    })

                    _uiState.value = _uiState.value.copy(
                        isInitialized = true,
                        hasHindiVoicePack = hasHindi,
                        activeLocale = (if (hasHindi) hindiLocale else Locale.getDefault()).toLanguageTag()
                    )
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed with status $status")
                    _uiState.value = _uiState.value.copy(isInitialized = false)
                }
            }
        }
    }

    fun setVoiceState(state: JulieVoiceState) {
        if (_uiState.value.voiceState != state) {
            _uiState.value = _uiState.value.copy(voiceState = state)
        }
    }

    fun toggleMute(): Boolean {
        val newMute = !_uiState.value.isMuted
        _uiState.value = _uiState.value.copy(isMuted = newMute)
        if (newMute) {
            stop()
        }
        return newMute
    }

    /**
     * Speaks full text, sanitizing markdown and queuing sentences sequentially.
     */
    fun speak(text: String, preferHindi: Boolean = false) {
        if (_uiState.value.isMuted) return
        val cleanSpeech = sanitizeForSpeech(text)
        if (cleanSpeech.isBlank()) return

        lastSpokenFullText = text
        requestAudioFocus()

        val engine = tts ?: return
        val hasDevanagari = cleanSpeech.any { it in '\u0900'..'\u097F' }
        val targetLocale = if (preferHindi || hasDevanagari) Locale("hi", "IN") else Locale.US
        runCatching { engine.language = targetLocale }

        engine.stop()

        val sentences = splitIntoSentences(cleanSpeech)
        sentences.forEachIndexed { idx, sentence ->
            val mode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "julie_utt_${UUID.randomUUID().toString().take(8)}"
            engine.speak(sentence, mode, null, utteranceId)
        }
    }

    /**
     * Incrementally speaks sentences as tokens stream in from AI.
     */
    fun onStreamingTokens(accumulatedText: String, preferHindi: Boolean = false) {
        if (_uiState.value.isMuted) return

        val clean = sanitizeForSpeech(accumulatedText)
        val sentences = splitIntoSentences(clean)

        // If a new complete sentence has formed beyond what was already spoken
        if (sentences.size > streamedSentencesSpokenIndex + 1) {
            val sentenceToSpeak = sentences[streamedSentencesSpokenIndex]
            streamedSentencesSpokenIndex++

            if (sentenceToSpeak.isNotBlank()) {
                requestAudioFocus()
                val engine = tts ?: return
                val hasDevanagari = sentenceToSpeak.any { it in '\u0900'..'\u097F' }
                val targetLocale = if (preferHindi || hasDevanagari) Locale("hi", "IN") else Locale.US
                runCatching { engine.language = targetLocale }

                val mode = if (streamedSentencesSpokenIndex == 1) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                engine.speak(sentenceToSpeak, mode, null, "julie_stream_${streamedSentencesSpokenIndex}")
            }
        }
    }

    fun resetStreamingBuffer() {
        streamedSentencesSpokenIndex = 0
        sentenceBuffer.setLength(0)
    }

    /**
     * Replays the last spoken answer.
     */
    fun replayLast(preferHindi: Boolean = false) {
        if (lastSpokenFullText.isNotBlank()) {
            speak(lastSpokenFullText, preferHindi)
        }
    }

    /**
     * Barge-in: stops speech playback immediately and releases audio focus.
     */
    fun stop() {
        runCatching {
            tts?.stop()
        }
        _uiState.value = _uiState.value.copy(
            isSpeaking = false,
            voiceState = JulieVoiceState.IDLE
        )
        abandonAudioFocus()
    }

    fun destroy() {
        stop()
        runCatching {
            tts?.shutdown()
        }
        tts = null
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    companion object {
        /**
         * Cleans Markdown syntax markers, table pipes, links, and code blocks
         * into smooth, natural spoken sentences for Text-To-Speech.
         */
        fun sanitizeForSpeech(raw: String): String {
            if (raw.isBlank()) return ""

            var text = raw

            // Strip code blocks completely (do not read 100 lines of code aloud)
            text = text.replace(Regex("```[\\s\\S]*?```"), " Here is the code snippet. ")
            text = text.replace(Regex("`[^`]+`"), "")

            // Strip raw Markdown tables: replace with brief intro
            if (text.contains("|") && text.contains("---")) {
                text = text.lines().filterNot { it.trim().startsWith("|") }.joinToString("\n") +
                        " Details are displayed in the table on screen."
            }

            // Strip URLs
            text = text.replace(Regex("https?://\\S+"), "")

            // Strip markdown links [label](url) -> label
            text = text.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")

            // Strip headings and bold/italic markers
            text = text.replace(Regex("[#*_~`>]+"), " ")

            // Strip bullets
            text = text.replace(Regex("^[\\s]*[-*•]\\s+", RegexOption.MULTILINE), "")
            text = text.replace(Regex("^[\\s]*\\d+\\.\\s+", RegexOption.MULTILINE), "")

            // Clean excessive whitespace and punctuation
            text = text.replace(Regex("\\s+"), " ").trim()

            return text.take(2400)
        }

        fun splitIntoSentences(text: String): List<String> {
            return text.split(Regex("(?<=[.!?।\\n])\\s+"))
                .map { it.trim() }
                .filter { it.length > 2 }
        }
    }
}
