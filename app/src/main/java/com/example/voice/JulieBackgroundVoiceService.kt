package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.accessibility.JulieAccessibilityController
import com.example.data.remote.GoogleGeminiClient
import com.example.overlay.JulieFloatingBubbleService
import com.example.overlay.JulieOverlayCommandRouter
import com.example.tools.JulieToolRegistry
import com.example.tools.PendingToolAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private const val TAG = "JulieVoiceService"
private const val CHANNEL_ID = "julie_voice_wake_channel"
private const val NOTIFICATION_ID = 5105

/**
 * 24/7 Continuous Background Voice Service for Julie AI.
 * - Listens for wake words ("Hey Julie", "Hi Julie", "हाय जुली", "जुली") in continuous loop.
 * - Executes automated phone tasks (YouTube, calls, alarms, web searches, scroll/tap).
 * - Answers questions using Google Gemini Free AI and speaks back via Text-to-Speech.
 * - Holds a partial WakeLock to stay alive 24h in background.
 */
class JulieBackgroundVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ttsEngine: TextToSpeech? = null
    private var isTtsReady = false

    @Volatile
    private var isRestarting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isVoiceServiceRunning.value = true

        // Acquire WakeLock to keep listening in background
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "JulieAI:24hVoiceWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // Up to 24h
        }

        // Initialize Text-to-Speech
        initTts()

        // Start SpeechRecognizer in continuous loop
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_VOICE -> {
                // Force instant listening
                restartListeningSafely(100L)
            }
        }

        startForegroundSafe()
        restartListeningSafely(300L)
        return START_STICKY
    }

    private fun startForegroundSafe() {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            this,
            201,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            202,
            Intent(this, JulieBackgroundVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bubbleIntent = PendingIntent.getService(
            this,
            203,
            Intent(this, JulieFloatingBubbleService::class.java).apply { action = JulieFloatingBubbleService.ACTION_START },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Julie AI • 24h Background Voice Active")
            .setContentText("Say 'Hey Julie' / 'हाय जुली' to control phone or ask anything")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_dialog_info, "Open Chat", openAppIntent)
            .addAction(android.R.drawable.ic_menu_crop, "Floating Bubble", bubbleIntent)
            .addAction(android.R.drawable.ic_media_pause, "Turn OFF", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Julie AI 24h Background Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Julie microphone listening for Hey Julie wake-word 24/7."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun initTts() {
        ttsEngine = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                ttsEngine?.language = Locale.getDefault()
                ttsEngine?.setSpeechRate(1.05f)
                ttsEngine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isListeningFlow.value = false
                    }
                    override fun onDone(utteranceId: String?) {
                        // Resume listening after Julie finishes speaking
                        restartListeningSafely(500L)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        restartListeningSafely(500L)
                    }
                })
            }
        }
    }

    private fun speakText(text: String) {
        if (!isTtsReady || text.isBlank()) return
        // Pause microphone while Julie is speaking to prevent listening to self
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        _isListeningFlow.value = false
        val cleanSpeech = JulieSpeechSynthesizer.sanitizeForSpeech(text).take(280)
        ttsEngine?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "julie_bg_speech_${System.currentTimeMillis()}")
    }

    private fun recreateSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
    }

    private fun initSpeechRecognizer() {
        recreateSpeechRecognizer()
    }

    private var consecutiveErrors = 0

    private fun restartListeningSafely(delayMs: Long = 400L) {
        if (isRestarting) return
        isRestarting = true
        mainHandler.postDelayed({
            isRestarting = false
            try {
                if (speechRecognizer == null || consecutiveErrors >= 2) {
                    consecutiveErrors = 0
                    recreateSpeechRecognizer()
                } else {
                    speechRecognizer?.cancel()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                speechRecognizer?.startListening(intent)
                _isListeningFlow.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start background listening: ${e.message}")
                recreateSpeechRecognizer()
            }
        }, delayMs)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListeningFlow.value = true
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _isListeningFlow.value = false
        }

        override fun onError(error: Int) {
            _isListeningFlow.value = false
            consecutiveErrors++
            val isBusy = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_AUDIO
            if (isBusy || consecutiveErrors >= 2) {
                recreateSpeechRecognizer()
                restartListeningSafely(900L)
            } else {
                restartListeningSafely(500L)
            }
        }

        override fun onResults(results: Bundle?) {
            _isListeningFlow.value = false
            consecutiveErrors = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcript = matches?.firstOrNull()?.trim().orEmpty()

            if (transcript.isNotBlank()) {
                handleRecognizedSpeech(transcript)
            } else {
                restartListeningSafely(600L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun handleRecognizedSpeech(transcript: String) {
        Log.i(TAG, "Julie Background Voice heard: '$transcript'")
        _lastHeardCommand.value = transcript

        val lower = transcript.lowercase(Locale.ROOT)
        val hasWakeWord = lower.contains("hey julie") || lower.contains("hi julie") ||
            lower.contains("julie") || lower.contains("जुली") || lower.contains("हाय जुली")

        // Parse voice command
        val parsed = VoiceInputController.parseVoiceCommand(transcript)

        when (parsed) {
            is VoiceCommandResult.ToolIntent -> {
                speakText("Opening ${parsed.action.title}")
                JulieToolRegistry.execute(applicationContext, parsed.action)
            }
            is VoiceCommandResult.TaskInitiation -> {
                speakText("Task scheduled: ${parsed.title}")
            }
            is VoiceCommandResult.ChatPrompt -> {
                // If it's an overlay device gesture (scroll, tap, click)
                val overlayCmd = JulieOverlayCommandRouter.parse(parsed.text)
                if (overlayCmd != null && JulieAccessibilityController.state.value.isConnected) {
                    when (overlayCmd) {
                        is com.example.overlay.JulieOverlayCommand.ScrollDown -> {
                            JulieAccessibilityController.scrollScreen(applicationContext, true)
                            speakText("Scrolling down")
                        }
                        is com.example.overlay.JulieOverlayCommand.ScrollUp -> {
                            JulieAccessibilityController.scrollScreen(applicationContext, false)
                            speakText("Scrolling up")
                        }
                        is com.example.overlay.JulieOverlayCommand.Back -> {
                            JulieAccessibilityController.pressGlobalBack()
                            speakText("Going back")
                        }
                        is com.example.overlay.JulieOverlayCommand.OpenApp -> {
                            speakText("Opening ${overlayCmd.name}")
                            JulieToolRegistry.execute(
                                applicationContext,
                                PendingToolAction(
                                    type = com.example.tools.JulieToolType.LAUNCH_APP,
                                    title = "Open ${overlayCmd.name}",
                                    argument = overlayCmd.name
                                )
                            )
                        }
                        else -> Unit
                    }
                    return
                }

                // If wake word was detected or direct query
                if (hasWakeWord) {
                    serviceScope.launch {
                        val reply = GoogleGeminiClient.generateContent(
                            context = applicationContext,
                            prompt = parsed.text
                        )
                        speakText(reply)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        _isVoiceServiceRunning.value = false
        _isListeningFlow.value = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        } catch (_: Exception) {}
        ttsEngine = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.voice.ACTION_START"
        const val ACTION_STOP = "com.example.voice.ACTION_STOP"
        const val ACTION_TRIGGER_VOICE = "com.example.voice.ACTION_TRIGGER_VOICE"

        private val _isVoiceServiceRunning = MutableStateFlow(false)
        val isVoiceServiceRunning: StateFlow<Boolean> = _isVoiceServiceRunning.asStateFlow()

        private val _isListeningFlow = MutableStateFlow(false)
        val isListeningFlow: StateFlow<Boolean> = _isListeningFlow.asStateFlow()

        private val _lastHeardCommand = MutableStateFlow("")
        val lastHeardCommand: StateFlow<String> = _lastHeardCommand.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, JulieBackgroundVoiceService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JulieBackgroundVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
