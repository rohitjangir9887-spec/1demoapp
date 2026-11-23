package com.example.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.LocalAuraStore
import com.example.data.model.ScheduleMode

private const val TAG = "JulieBootReceiver"

/**
 * Boot receiver that automatically resumes 24h continuous tasks
 * after the Android device reboots.
 */
class JulieBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted. Checking for active 24h continuous tasks...")
            runCatching {
                val store = LocalAuraStore(context.applicationContext)
                val tasks = store.loadTasks()
                val hasContinuousTasks = tasks.any { it.scheduleEnum() == ScheduleMode.CONTINUOUS_24H }
                if (hasContinuousTasks) {
                    Log.d(TAG, "Resuming 24h continuous tasks in foreground...")
                    JulieTaskExecutionService.start(context, "Resumed 24h Autonomous Tasks")
                }
            }
        }
    }
}
