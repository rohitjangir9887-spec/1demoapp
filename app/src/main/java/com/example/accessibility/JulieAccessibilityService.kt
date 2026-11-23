package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.overlay.JulieFloatingBubbleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GestureStepLog(
    val id: String = "step_${System.nanoTime()}",
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val action: String,
    val detail: String,
    val succeeded: Boolean
)

data class AccessibilityUiNode(
    val label: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean,
    val centerX: Float,
    val centerY: Float
)

data class JulieAccessibilityState(
    val isConnected: Boolean = false,
    val isEmergencyStopped: Boolean = false,
    val activePackageName: String = "",
    val lastUiSummary: String = "Accessibility Service is off. Enable it optionally in Android Settings for UI reading and assisted gestures.",
    val visibleNodeCount: Int = 0,
    val stepLogs: List<GestureStepLog> = emptyList()
)

/**
 * Pure, unit-testable safety guard ensuring Julie never bypasses Android lockscreen
 * or reads/exposes sensitive password fields.
 */
object JulieAccessibilitySafety {
    private val sensitivePackageHints = setOf(
        "com.android.systemui",
        "com.google.android.gms.auth",
        "com.android.settings.password"
    )

    fun evaluateGestureSafety(
        isKeyguardLocked: Boolean,
        isEmergencyStopped: Boolean,
        targetPackage: String
    ): Result<Unit> {
        if (isEmergencyStopped) {
            return Result.failure(IllegalStateException("Blocked: Emergency STOP is active. Reset STOP to resume."))
        }
        if (isKeyguardLocked) {
            return Result.failure(SecurityException("Blocked: Device lockscreen is active. Julie never bypasses PIN, pattern, or biometric locks."))
        }
        val lower = targetPackage.lowercase(Locale.US)
        if (sensitivePackageHints.any { lower.startsWith(it) && lower.contains("password") }) {
            return Result.failure(SecurityException("Blocked: Protected credential screen cannot be automated."))
        }
        return Result.success(Unit)
    }

    fun sanitizeNodeText(
        rawText: CharSequence?,
        rawContentDesc: CharSequence?,
        isPassword: Boolean
    ): String? {
        if (isPassword) return null
        val candidate = (rawText?.toString()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: rawContentDesc?.toString()?.trim().takeUnless { it.isNullOrEmpty() })
            ?: return null
        val lower = candidate.lowercase(Locale.US)
        if (lower.contains("nvapi-") || lower.contains("private_")) return "[REDACTED]"
        return candidate.take(80)
    }
}

/**
 * Singleton bridge between Jetpack Compose / Floating Bubble / ViewModel and the live
 * Android [JulieAccessibilityService].
 */
object JulieAccessibilityController {
    private val _state = MutableStateFlow(JulieAccessibilityState())
    val state: StateFlow<JulieAccessibilityState> = _state.asStateFlow()

    @Volatile
    private var serviceRef: WeakReference<JulieAccessibilityService>? = null

    internal fun attach(service: JulieAccessibilityService) {
        serviceRef = WeakReference(service)
        _state.value = _state.value.copy(
            isConnected = true,
            isEmergencyStopped = false,
            lastUiSummary = "Julie Accessibility connected. Ready to inspect non-sensitive UI or run user-approved gestures."
        )
        appendLog("SERVICE_CONNECTED", "AccessibilityService active (lockscreen & password protection enforced)", true)
    }

    internal fun detach() {
        serviceRef = null
        _state.value = _state.value.copy(
            isConnected = false,
            lastUiSummary = "Julie Accessibility disconnected."
        )
        appendLog("SERVICE_DISCONNECTED", "AccessibilityService turned off", true)
    }

    internal fun updateActivePackage(pkg: String) {
        if (pkg.isNotBlank() && pkg != _state.value.activePackageName) {
            _state.value = _state.value.copy(activePackageName = pkg)
        }
    }

    fun emergencyStop() {
        _state.value = _state.value.copy(isEmergencyStopped = true)
        appendLog("EMERGENCY_STOP", "Instant STOP triggered — all assisted gestures halted.", true)
    }

    fun resumeFromStop() {
        _state.value = _state.value.copy(isEmergencyStopped = false)
        appendLog("RESUMED", "Ready for next user-initiated command.", true)
    }

    fun inspectVisibleUi(): String {
        val service = serviceRef?.get()
            ?: return "Accessibility Service is not enabled. Open Android Accessibility Settings to enable Julie AI."
        return service.captureVisibleUiSummary()
    }

    fun tapAt(context: Context, x: Float, y: Float, label: String = "AI Tap"): Boolean {
        val service = serviceRef?.get()
        if (service == null) {
            appendLog("TAP_SKIPPED", "Accessibility Service not enabled.", false)
            return false
        }
        return service.executeSafeTap(context, x, y, label)
    }

    fun findAndClick(context: Context, query: String): Boolean {
        val service = serviceRef?.get()
        if (service == null) {
            appendLog("CLICK_SKIPPED", "Accessibility Service not enabled.", false)
            return false
        }
        return service.executeFindAndClick(context, query)
    }

    fun scrollScreen(context: Context, scrollDown: Boolean): Boolean {
        val service = serviceRef?.get()
        if (service == null) {
            appendLog("SCROLL_SKIPPED", "Accessibility Service not enabled.", false)
            return false
        }
        return service.executeSafeScroll(context, scrollDown)
    }

    fun inputTextIntoFocusedField(text: String): Boolean {
        val service = serviceRef?.get()
        if (service == null) {
            appendLog("INPUT_SKIPPED", "Accessibility Service not enabled.", false)
            return false
        }
        return service.executeSafeTextInput(text)
    }

    fun pressGlobalBack(): Boolean {
        val service = serviceRef?.get()
        if (service == null) {
            appendLog("BACK_SKIPPED", "Accessibility Service not enabled.", false)
            return false
        }
        return service.executeSafeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK, "Global Back")
    }

    internal fun setUiSnapshot(summary: String, nodeCount: Int) {
        _state.value = _state.value.copy(
            lastUiSummary = summary,
            visibleNodeCount = nodeCount
        )
    }

    internal fun appendLog(action: String, detail: String, succeeded: Boolean) {
        val entry = GestureStepLog(action = action, detail = detail, succeeded = succeeded)
        _state.value = _state.value.copy(
            stepLogs = (listOf(entry) + _state.value.stepLogs).take(30)
        )
    }
}

/**
 * Separately enabled Android [AccessibilityService] for Julie AI.
 * Strictly respects device lockscreen, skips all password fields, displays an animated
 * AI pointer via [JulieFloatingBubbleService.showAnimatedPointer], and supports instant STOP.
 */
class JulieAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        JulieAccessibilityController.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            JulieAccessibilityController.updateActivePackage(pkg)
        }
    }

    override fun onInterrupt() {
        JulieAccessibilityController.emergencyStop()
    }

    override fun onDestroy() {
        JulieAccessibilityController.detach()
        super.onDestroy()
    }

    private fun isDeviceLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private fun checkSafetyOrLog(actionName: String): Boolean {
        val current = JulieAccessibilityController.state.value
        val result = JulieAccessibilitySafety.evaluateGestureSafety(
            isKeyguardLocked = isDeviceLocked(),
            isEmergencyStopped = current.isEmergencyStopped,
            targetPackage = current.activePackageName
        )
        result.onFailure { err ->
            JulieAccessibilityController.appendLog(actionName, err.message ?: "Blocked by safety guard", false)
        }
        return result.isSuccess
    }

    fun captureVisibleUiSummary(): String {
        if (!checkSafetyOrLog("INSPECT_UI")) {
            return "Blocked: Unlock phone or clear Emergency STOP before inspecting UI."
        }
        val root = rootInActiveWindow ?: return "No active window hierarchy available right now."
        val nodes = mutableListOf<AccessibilityUiNode>()
        try {
            collectSafeNodes(root, nodes, maxNodes = 45)
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
        val pkg = JulieAccessibilityController.state.value.activePackageName.ifBlank { "current app" }
        val summary = if (nodes.isEmpty()) {
            "Active app ($pkg): No readable public UI labels found."
        } else {
            val preview = nodes.take(14).joinToString(" • ") { node ->
                val tag = when {
                    node.editable -> "[Input]"
                    node.clickable -> "[Tap]"
                    else -> ""
                }
                "$tag${node.label}"
            }
            "Active app ($pkg) • ${nodes.size} safe UI elements: $preview"
        }
        JulieAccessibilityController.setUiSnapshot(summary, nodes.size)
        JulieAccessibilityController.appendLog("INSPECT_UI", "Read ${nodes.size} safe UI elements in $pkg", true)
        return summary
    }

    fun executeSafeTap(context: Context, x: Float, y: Float, label: String): Boolean {
        if (!checkSafetyOrLog("TAP")) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            JulieAccessibilityController.appendLog("TAP", "Gesture dispatch requires Android 7.0+", false)
            return false
        }
        JulieFloatingBubbleService.showAnimatedPointer(context, x, y, "Tapping: $label")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 90L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                JulieAccessibilityController.appendLog(
                    "TAP_VERIFIED",
                    "Completed tap ($label) at (${x.toInt()}, ${y.toInt()})",
                    true
                )
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                JulieAccessibilityController.appendLog("TAP_CANCELLED", "Tap cancelled ($label)", false)
            }
        }, null)
        if (!dispatched) {
            JulieAccessibilityController.appendLog("TAP_FAILED", "System rejected gesture dispatch", false)
        }
        return dispatched
    }

    fun executeFindAndClick(context: Context, query: String): Boolean {
        if (!checkSafetyOrLog("FIND_CLICK")) return false
        val root = rootInActiveWindow ?: return false
        try {
            val matches = root.findAccessibilityNodeInfosByText(query)
            val target = matches?.firstOrNull { !it.isPassword }
            if (target == null) {
                JulieAccessibilityController.appendLog("FIND_CLICK", "Element '$query' not found on screen", false)
                return false
            }
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            JulieFloatingBubbleService.showAnimatedPointer(context, cx, cy, "Click: $query")

            var clickableNode: AccessibilityNodeInfo? = target
            while (clickableNode != null && !clickableNode.isClickable) {
                clickableNode = clickableNode.parent
            }
            val clicked = clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (clicked) {
                JulieAccessibilityController.appendLog("CLICK_VERIFIED", "Clicked '$query' at (${cx.toInt()}, ${cy.toInt()})", true)
                return true
            }
            return executeSafeTap(context, cx, cy, query)
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
    }

    fun executeSafeScroll(context: Context, scrollDown: Boolean): Boolean {
        if (!checkSafetyOrLog("SCROLL")) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val dm = resources.displayMetrics
        val cx = dm.widthPixels * 0.5f
        val startY = if (scrollDown) dm.heightPixels * 0.72f else dm.heightPixels * 0.30f
        val endY = if (scrollDown) dm.heightPixels * 0.28f else dm.heightPixels * 0.74f
        val dirLabel = if (scrollDown) "Scroll Down" else "Scroll Up"
        JulieFloatingBubbleService.showAnimatedPointer(context, cx, startY, dirLabel)

        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 320L))
            .build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                JulieAccessibilityController.appendLog("SCROLL_VERIFIED", "$dirLabel completed", true)
            }
        }, null)
        return ok
    }

    fun executeSafeTextInput(text: String): Boolean {
        if (!checkSafetyOrLog("INPUT_TEXT")) return false
        val root = rootInActiveWindow ?: return false
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) {
                JulieAccessibilityController.appendLog("INPUT_TEXT", "No focused text field on screen", false)
                return false
            }
            if (focused.isPassword) {
                JulieAccessibilityController.appendLog("INPUT_BLOCKED", "Refused to enter text into a password field", false)
                return false
            }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            JulieAccessibilityController.appendLog(
                if (ok) "INPUT_VERIFIED" else "INPUT_FAILED",
                if (ok) "Entered ${text.length} chars into focused field" else "Focused field rejected ACTION_SET_TEXT",
                ok
            )
            return ok
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
    }

    fun executeSafeGlobalAction(globalAction: Int, label: String): Boolean {
        if (!checkSafetyOrLog("GLOBAL_ACTION")) return false
        val ok = performGlobalAction(globalAction)
        JulieAccessibilityController.appendLog(
            if (ok) "GLOBAL_VERIFIED" else "GLOBAL_FAILED",
            "$label (${if (ok) "ok" else "failed"})",
            ok
        )
        return ok
    }

    private fun collectSafeNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityUiNode>,
        maxNodes: Int
    ) {
        if (out.size >= maxNodes) return
        if (node.isPassword) return // Never inspect or leak password nodes

        val safeLabel = JulieAccessibilitySafety.sanitizeNodeText(
            rawText = node.text,
            rawContentDesc = node.contentDescription,
            isPassword = node.isPassword
        )
        if (safeLabel != null && node.isVisibleToUser) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                out.add(
                    AccessibilityUiNode(
                        label = safeLabel,
                        className = node.className?.toString()?.substringAfterLast('.') ?: "View",
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        centerX = rect.exactCenterX(),
                        centerY = rect.exactCenterY()
                    )
                )
            }
        }

        val count = node.childCount
        for (i in 0 until count) {
            if (out.size >= maxNodes) break
            val child = node.getChild(i) ?: continue
            collectSafeNodes(child, out, maxNodes)
        }
    }
}
