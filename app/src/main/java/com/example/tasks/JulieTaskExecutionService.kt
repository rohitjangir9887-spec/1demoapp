package com.example.tasks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ai.AuraSwarmEngine
import com.example.data.local.LocalAuraStore
import com.example.data.model.AssistantTask
import com.example.data.model.ScheduleMode
import com.example.data.model.TaskCategory
import com.example.data.model.TaskStatus
import com.example.data.remote.NvidiaNimClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG = "JulieTaskService"
private const val CHANNEL_ID = "julie_tasks_channel"
private const val NOTIFICATION_ID = 4105

data class TaskRunnerState(
    val isRunning: Boolean = false,
    val activeTaskCount: Int = 0,
    val currentTaskTitle: String = "",
    val progressPercent: Int = 0,
    val is24hWatchdogActive: Boolean = false
)

/**
 * Android Foreground Service for durable Julie AI autonomous tasks.
 * Ensures long-running 24h tasks and AI swarm operations survive app minimization,
 * screen locks, and process suspension by holding a CPU WakeLock and foreground notification.
 */
class JulieTaskExecutionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NvidiaNimClient.initialize(applicationContext)
        acquireWakeLock()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                JulieTaskRunner.stopAll(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val taskTitle = intent?.getStringExtra(EXTRA_TASK_TITLE) ?: "Autonomous Task Runner Active"
                startForeground(NOTIFICATION_ID, buildForegroundNotification(taskTitle, 0))
                JulieTaskRunner.startRunnerLoop(applicationContext)
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JulieAI:TaskExecutionWakeLock")?.apply {
                acquire(10 * 60 * 1000L) // 10 minutes max safety limit per acquisition
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Julie AI Autonomous Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active Julie AI 24h background tasks and autonomous multi-agent work."
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, progress: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JulieTaskExecutionService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Julie AI • $title")
            .setContentText("Autonomous AI worker active in background ($progress%)")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpen)
            .addAction(R.mipmap.ic_launcher, "Stop Tasks", pendingStop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.example.tasks.ACTION_START"
        const val ACTION_STOP = "com.example.tasks.ACTION_STOP"
        const val EXTRA_TASK_TITLE = "extra_task_title"

        fun start(context: Context, taskTitle: String = "24h Task Execution") {
            val intent = Intent(context, JulieTaskExecutionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JulieTaskExecutionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

/**
 * Singleton autonomous task runner that lives at the application level.
 * Survives UI Activity closures and ensures queued 24h continuous tasks
 * execute reliably without being terminated by Android OS memory reclaim.
 */
object JulieTaskRunner {

    private val runnerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parallelSemaphore = Semaphore(3)

    private val _state = MutableStateFlow(TaskRunnerState())
    val state: StateFlow<TaskRunnerState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var swarmEngine: AuraSwarmEngine? = null

    fun startRunnerLoop(context: Context) {
        if (loopJob?.isActive == true) return
        val app = context.applicationContext

        if (swarmEngine == null) {
            swarmEngine = AuraSwarmEngine(app)
        }

        loopJob = runnerScope.launch {
            _state.value = _state.value.copy(isRunning = true, is24hWatchdogActive = true)
            val store = LocalAuraStore(app)

            while (isActive && _state.value.is24hWatchdogActive) {
                try {
                    val tasks = store.loadTasks()
                    val queued = tasks.filter {
                        it.statusEnum() == TaskStatus.QUEUED &&
                                (it.scheduleEnum() == ScheduleMode.CONTINUOUS_24H || it.scheduleEnum() == ScheduleMode.ONCE)
                    }

                    if (queued.isNotEmpty()) {
                        val batch = queued.take(2)
                        _state.value = _state.value.copy(
                            activeTaskCount = batch.size,
                            currentTaskTitle = batch.firstOrNull()?.title.orEmpty()
                        )

                        batch.forEach { task ->
                            launch {
                                parallelSemaphore.withPermit {
                                    executeTaskSafely(app, store, task)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in autonomous task runner loop", e)
                }

                delay(12_000L) // Polling interval for continuous execution
            }
        }
    }

    private suspend fun executeTaskSafely(
        context: Context,
        store: LocalAuraStore,
        task: AssistantTask
    ) {
        val steps = mutableListOf("Julie Autonomous Worker activated for \"${task.title}\"")
        store.updateTaskExecution(
            taskId = task.id,
            status = TaskStatus.RUNNING,
            assignedModels = task.assignedModels.ifEmpty { listOf("Julie Nemotron") },
            resultText = null,
            executionSteps = steps,
            progressPercent = 20
        )

        try {
            val engine = swarmEngine ?: AuraSwarmEngine(context)
            val prompt = "Autonomous Task: ${task.title}\nCategory: ${task.category}\nInstructions: ${task.prompt}"

            val result = engine.executeQuery(
                userPrompt = prompt,
                conversationContext = "Autonomous Background Worker Execution",
                preferredModelId = "nvidia/nemotron-3-super-120b-a12b",
                swarmEnabled = true,
                maxPowerEnabled = false,
                useGoogleSearch = task.categoryEnum() == TaskCategory.RESEARCH,
                useSingleFlightLock = false
            )

            steps.add("Completed analysis via ${result.primaryEngineLabel}")
            steps.add("Final result generated successfully")

            store.updateTaskExecution(
                taskId = task.id,
                status = TaskStatus.COMPLETED,
                assignedModels = result.collaboratingModels,
                resultText = result.finalText,
                executionSteps = steps,
                progressPercent = 100
            )

            postTaskCompletionNotification(context, task.title, result.finalText.take(120))
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing autonomous task ${task.id}", e)
            steps.add("Error: ${e.localizedMessage ?: "Execution failed"}")
            store.updateTaskExecution(
                taskId = task.id,
                status = TaskStatus.FAILED,
                assignedModels = task.assignedModels,
                resultText = "Task failed: ${e.localizedMessage}",
                executionSteps = steps,
                progressPercent = 100
            )
        }
    }

    private fun postTaskCompletionNotification(context: Context, title: String, preview: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            title.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Julie Task Completed: $title")
            .setContentText(preview)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()

        nm.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    fun stopAll(context: Context) {
        loopJob?.cancel()
        loopJob = null
        _state.value = TaskRunnerState(isRunning = false, is24hWatchdogActive = false)
        JulieTaskExecutionService.stop(context)
    }
}
