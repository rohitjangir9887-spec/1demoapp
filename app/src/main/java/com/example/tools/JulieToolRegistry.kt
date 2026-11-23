package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import com.example.data.security.CryptoVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.UUID

enum class JulieToolType(val label: String, val requiresConfirmation: Boolean) {
    LAUNCH_APP("Launch App", false),
    OPEN_URL("Open HTTPS URL", false),
    SEARCH_WEB("Google Web Search", false),
    SEARCH_YOUTUBE("YouTube Search", false),
    DIAL_PHONE("Open Phone Dialer", true),
    SET_ALARM("Set Alarm", true),
    CREATE_REMINDER("Create Reminder Task", false)
}

data class PendingToolAction(
    val idempotencyKey: String = UUID.randomUUID().toString().take(10),
    val type: JulieToolType,
    val title: String,
    val argument: String,
    val extraHour: Int = 7,
    val extraMinute: Int = 0
)

data class ToolActionLog(
    val id: String,
    val toolLabel: String,
    val argumentSummary: String,
    val succeeded: Boolean,
    val detail: String,
    val timestampMs: Long
)

/**
 * Validated, safe Android Tool Registry for Julie AI.
 * - Uses official Android intents only (never stealth calls, never lockscreen bypass, never accessibility abuse).
 * - Uses ACTION_DIAL (opens dialer for user confirmation) instead of silent ACTION_CALL.
 * - Records every real execution transparently in [actionLogs].
 */
object JulieToolRegistry {

    private val _actionLogs = MutableStateFlow<List<ToolActionLog>>(emptyList())
    val actionLogs: StateFlow<List<ToolActionLog>> = _actionLogs.asStateFlow()

    private val executedKeys = LinkedHashSet<String>()

    private val knownAppPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "spotify" to "com.spotify.music",
        "instagram" to "com.instagram.android",
        "telegram" to "org.telegram.messenger",
        "calendar" to "com.google.android.calendar",
        "calculator" to "com.google.android.calculator"
    )

    fun execute(context: Context, action: PendingToolAction): Result<String> {
        if (!executedKeys.add(action.idempotencyKey)) {
            return Result.success("Action already executed (${action.title}).")
        }
        val outcome = when (action.type) {
            JulieToolType.LAUNCH_APP -> launchApp(context, action.argument)
            JulieToolType.OPEN_URL -> openUrl(context, action.argument)
            JulieToolType.SEARCH_WEB -> searchWeb(context, action.argument)
            JulieToolType.SEARCH_YOUTUBE -> searchYouTube(context, action.argument)
            JulieToolType.DIAL_PHONE -> openDialer(context, action.argument)
            JulieToolType.SET_ALARM -> setAlarm(context, action.argument, action.extraHour, action.extraMinute)
            JulieToolType.CREATE_REMINDER -> Result.success("Queued reminder: ${action.argument}")
        }

        val log = ToolActionLog(
            id = action.idempotencyKey,
            toolLabel = action.type.label,
            argumentSummary = action.argument.take(64),
            succeeded = outcome.isSuccess,
            detail = outcome.getOrElse { it.localizedMessage ?: "Failed" },
            timestampMs = System.currentTimeMillis()
        )
        _actionLogs.value = (listOf(log) + _actionLogs.value).take(40)
        return outcome
    }

    fun clearLogs() {
        _actionLogs.value = emptyList()
    }

    private fun launchApp(context: Context, appQuery: String): Result<String> {
        val clean = appQuery.trim().lowercase()
        val pkg = knownAppPackages.entries.firstOrNull { clean.contains(it.key) }?.value
        if (pkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return runCatching {
                    context.startActivity(intent)
                    "Opened $appQuery"
                }
            }
        }
        // Fallback: open official web destination for YouTube/Maps/Gmail if app not installed
        return when {
            clean.contains("youtube") -> openUrl(context, "https://m.youtube.com")
            clean.contains("maps") -> openUrl(context, "https://maps.google.com")
            else -> Result.failure(IllegalStateException("App \"$appQuery\" is not installed on this device."))
        }
    }

    private fun openUrl(context: Context, rawUrl: String): Result<String> {
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        val validated = CryptoVault.validatePublicHttpsUrl(normalized)
            .getOrElse { return Result.failure(it) }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validated)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened $validated"
        }
    }

    private fun searchWeb(context: Context, query: String): Result<String> {
        val q = query.trim()
        if (q.isEmpty()) return Result.failure(IllegalArgumentException("Search query is empty."))
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = "https://www.google.com/search?q=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Searching Google for \"$q\""
        }
    }

    private fun searchYouTube(context: Context, query: String): Result<String> {
        val q = query.trim()
        val url = if (q.isEmpty()) {
            "https://www.youtube.com"
        } else {
            "https://www.youtube.com/results?search_query=${URLEncoder.encode(q, "UTF-8")}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            if (q.isEmpty()) "Opened YouTube" else "Opened YouTube search for \"$q\""
        }
    }

    private fun openDialer(context: Context, phoneOrQuery: String): Result<String> {
        val digits = phoneOrQuery.filter { it.isDigit() || it == '+' }
        val uri = if (digits.isNotBlank()) Uri.parse("tel:$digits") else Uri.parse("tel:")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            if (digits.isNotBlank()) "Opened phone dialer for $digits (confirm call manually)"
            else "Opened phone dialer"
        }
    }

    private fun setAlarm(context: Context, label: String, hour: Int, minute: Int): Result<String> {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, safeHour)
            putExtra(AlarmClock.EXTRA_MINUTES, safeMinute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Julie AI Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened Clock app to confirm alarm at %02d:%02d".format(safeHour, safeMinute)
        }
    }
}
