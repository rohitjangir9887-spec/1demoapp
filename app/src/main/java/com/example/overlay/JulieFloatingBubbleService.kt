package com.example.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.MainActivity
import com.example.accessibility.JulieAccessibilityController
import com.example.data.ai.AuraSwarmEngine
import com.example.data.ai.OrchestrationMode
import com.example.data.ai.RoutingConfig
import com.example.data.ai.RoutingProfile
import com.example.screen.ScreenCaptureService
import com.example.screen.ScreenCaptureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating draggable Julie Orb + expandable Mini Chat & Live Control Panel + Animated AI Pointer.
 * Strictly requires explicit user-granted SYSTEM_ALERT_WINDOW permission.
 */
class JulieFloatingBubbleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var panelView: View? = null
    private var panelStatusText: TextView? = null
    private var panelOutputText: TextView? = null
    private var panelInput: EditText? = null
    private var isExpanded = false

    private var activePromptJob: Job? = null
    private var pointerView: View? = null
    private var pointerDismissRunnable: Runnable? = null

    private val swarmEngine by lazy { AuraSwarmEngine(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlayAndSelf()
                return START_NOT_STICKY
            }

            ACTION_SHOW_POINTER -> {
                val x = intent.getFloatExtra(EXTRA_POINTER_X, 300f)
                val y = intent.getFloatExtra(EXTRA_POINTER_Y, 600f)
                val label = intent.getStringExtra(EXTRA_POINTER_LABEL).orEmpty()
                if (canDrawOverlays(this)) {
                    showPointerOverlay(x, y, label)
                }
                return START_NOT_STICKY
            }

            else -> {
                if (!canDrawOverlays(this)) {
                    _isOverlayRunning.value = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureBubbleAttached()
                _isOverlayRunning.value = true
                startLiveTelemetryObserver()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        activePromptJob?.cancel()
        removePointerOverlay()
        removePanelView()
        removeBubbleView()
        _isOverlayRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun ensureBubbleAttached() {
        if (bubbleView != null) return
        val wm = windowManager ?: return
        val sizePx = dp(58)

        val orbBg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#76B900"), Color.parseColor("#00D4FF"), Color.parseColor("#7B5CFF"))
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), Color.parseColor("#E6FFFFFF"))
        }

        val label = TextView(this).apply {
            text = "J"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = "Julie AI floating bubble"
        }

        val container = FrameLayout(this).apply {
            background = orbBg
            elevation = dp(10).toFloat()
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(240)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleMiniPanel()
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        params.x = if (params.x + sizePx / 2 < screenWidth / 2) dp(12) else screenWidth - sizePx - dp(12)
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }

                else -> false
            }
        }

        bubbleParams = params
        bubbleView = container
        runCatching { wm.addView(container, params) }
    }

    private fun toggleMiniPanel() {
        if (isExpanded) {
            removePanelView()
        } else {
            showMiniPanel()
        }
    }

    private fun showMiniPanel() {
        if (panelView != null) return
        val wm = windowManager ?: return

        val panelBg = GradientDrawable().apply {
            setColor(Color.parseColor("#EE10141F"))
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.parseColor("#5500D4FF"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(14).toFloat()
        }

        // Top header row
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "जुली • Julie Mini Control"
            setTextColor(Color.parseColor("#F5F7FA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val collapseBtn = makePillButton("Hide", "#2A3248", "#FFFFFF") {
            removePanelView()
        }
        header.addView(title)
        header.addView(collapseBtn)
        root.addView(header)

        // Live telemetry line
        val statusTv = TextView(this).apply {
            text = buildLiveStatusLine()
            setTextColor(Color.parseColor("#00D4FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(6), 0, dp(8))
        }
        panelStatusText = statusTv
        root.addView(statusTv)

        // Output box
        val outputScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(118)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC181F30"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val outputTv = TextView(this).apply {
            text = "Ask Julie anything about your current screen, or tap Inspect UI / STOP."
            setTextColor(Color.parseColor("#E8EEF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        }
        panelOutputText = outputTv
        outputScroll.addView(outputTv)
        root.addView(outputScroll)

        // Prompt input row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(8))
        }
        val edit = EditText(this).apply {
            hint = "Ask Julie (Hindi / English)..."
            setHintTextColor(Color.parseColor("#8C96AA"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            imeOptions = EditorInfo.IME_ACTION_SEND
            isSingleLine = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF1A2234"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#4476B900"))
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        panelInput = edit
        val sendBtn = makePillButton("Ask", "#76B900", "#071400") {
            val q = edit.text?.toString()?.trim().orEmpty()
            if (q.isNotEmpty()) {
                edit.setText("")
                askJulieFromBubble(q)
            }
        }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.marginStart = dp(6)
        }
        inputRow.addView(edit)
        inputRow.addView(sendBtn)
        root.addView(inputRow)

        // Quick action controls row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val inspectBtn = makePillButton("Read UI", "#1D2A42", "#00D4FF") {
            val summary = JulieAccessibilityController.inspectVisibleUi()
            panelOutputText?.text = summary
        }
        val openAppBtn = makePillButton("Open App", "#1D2A42", "#FFFFFF") {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
            removePanelView()
        }
        val stopBtn = makePillButton("STOP", "#E53935", "#FFFFFF") {
            emergencyStopEverything()
        }

        val btnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        inspectBtn.layoutParams = btnLp
        openAppBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        stopBtn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        actionsRow.addView(inspectBtn)
        actionsRow.addView(openAppBtn)
        actionsRow.addView(stopBtn)
        root.addView(actionsRow)

        val panelWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost(dp(380))
        val panelParams = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(90)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        panelView = root
        isExpanded = true
        runCatching { wm.addView(root, panelParams) }
    }

    private fun makePillButton(label: String, bgHex: String, textHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor(textHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(38)
            minimumHeight = dp(38)
            minWidth = dp(52)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun askJulieFromBubble(prompt: String) {
        activePromptJob?.cancel()
        panelOutputText?.text = "Julie is thinking..."
        activePromptJob = serviceScope.launch {
            val latestFrame = ScreenCaptureStore.state.value.latestBitmap
            val uiCtx = if (JulieAccessibilityController.state.value.isConnected) {
                JulieAccessibilityController.inspectVisibleUi()
            } else ""
            val enrichedPrompt = if (uiCtx.isNotBlank()) {
                "$prompt\n\n[Visible UI Context]\n$uiCtx"
            } else prompt

            runCatching {
                swarmEngine.executeQuery(
                    userPrompt = enrichedPrompt,
                    attachedBitmap = latestFrame,
                    useGoogleSearch = false,
                    routingConfig = RoutingConfig(
                        profile = RoutingProfile.FAST,
                        orchestrationMode = OrchestrationMode.SINGLE_MODEL
                    ),
                    onStageUpdate = { stage ->
                        panelStatusText?.text = "${stage.activeModelName} • ${stage.stageLabel}"
                    },
                    onStreamToken = { partial ->
                        panelOutputText?.text = partial
                    }
                )
            }.onSuccess { result ->
                panelOutputText?.text = result.finalText
                panelStatusText?.text = buildLiveStatusLine()
            }.onFailure { err ->
                panelOutputText?.text = "Error: ${err.localizedMessage ?: "Check NVIDIA NIM API key in Models tab."}"
                panelStatusText?.text = buildLiveStatusLine()
            }
        }
    }

    private fun emergencyStopEverything() {
        activePromptJob?.cancel()
        JulieAccessibilityController.emergencyStop()
        ScreenCaptureService.stop(this)
        stopOverlayAndSelf()
    }

    private fun stopOverlayAndSelf() {
        removePointerOverlay()
        removePanelView()
        removeBubbleView()
        _isOverlayRunning.value = false
        stopSelf()
    }

    private fun startLiveTelemetryObserver() {
        serviceScope.launch {
            ScreenCaptureStore.state.collect {
                panelStatusText?.text = buildLiveStatusLine()
            }
        }
    }

    private fun buildLiveStatusLine(): String {
        val screen = ScreenCaptureStore.state.value
        val acc = JulieAccessibilityController.state.value
        val screenLabel = if (screen.isSharingActive) "LIVE #${screen.frameCount} (${screen.targetFps} FPS)" else "Screen Idle"
        val accLabel = if (acc.isConnected) "Accessibility ON" else "Accessibility OFF"
        return "$screenLabel • $accLabel"
    }

    private fun showPointerOverlay(x: Float, y: Float, label: String) {
        val wm = windowManager ?: return
        removePointerOverlay()

        val ringSize = dp(52)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val ring = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ringSize, ringSize)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3300D4FF"))
                setStroke(dp(3), Color.parseColor("#FF76B900"))
            }
        }
        container.addView(ring)

        if (label.isNotBlank()) {
            val badge = TextView(this).apply {
                text = label.take(28)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DD10141F"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#8800D4FF"))
                }
            }
            container.addView(badge)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - ringSize / 2f).toInt().coerceAtLeast(0)
            this.y = (y - ringSize / 2f).toInt().coerceAtLeast(0)
        }

        pointerView = container
        runCatching { wm.addView(container, params) }

        val pulseX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 0.7f, 1.35f, 1.0f)
        val pulseY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 0.7f, 1.35f, 1.0f)
        val fade = ObjectAnimator.ofFloat(container, View.ALPHA, 1f, 1f, 0f)
        AnimatorSet().apply {
            playTogether(pulseX, pulseY, fade)
            duration = 1100L
            interpolator = DecelerateInterpolator()
            start()
        }

        val dismiss = Runnable { removePointerOverlay() }
        pointerDismissRunnable = dismiss
        mainHandler.postDelayed(dismiss, 1150L)
    }

    private fun removePointerOverlay() {
        pointerDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        pointerDismissRunnable = null
        pointerView?.let { v -> runCatching { windowManager?.removeView(v) } }
        pointerView = null
    }

    private fun removePanelView() {
        panelView?.let { v -> runCatching { windowManager?.removeView(v) } }
        panelView = null
        panelStatusText = null
        panelOutputText = null
        panelInput = null
        isExpanded = false
    }

    private fun removeBubbleView() {
        bubbleView?.let { v -> runCatching { windowManager?.removeView(v) } }
        bubbleView = null
        bubbleParams = null
    }

    companion object {
        private const val ACTION_START = "com.example.overlay.START_BUBBLE"
        private const val ACTION_STOP = "com.example.overlay.STOP_BUBBLE"
        private const val ACTION_SHOW_POINTER = "com.example.overlay.SHOW_POINTER"
        private const val EXTRA_POINTER_X = "pointer_x"
        private const val EXTRA_POINTER_Y = "pointer_y"
        private const val EXTRA_POINTER_LABEL = "pointer_label"

        private val _isOverlayRunning = MutableStateFlow(false)
        val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (!canDrawOverlays(context)) return
            val intent = Intent(context, JulieFloatingBubbleService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JulieFloatingBubbleService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun showAnimatedPointer(context: Context, x: Float, y: Float, label: String = "Julie AI") {
            if (!canDrawOverlays(context)) return
            val intent = Intent(context, JulieFloatingBubbleService::class.java).apply {
                action = ACTION_SHOW_POINTER
                putExtra(EXTRA_POINTER_X, x)
                putExtra(EXTRA_POINTER_Y, y)
                putExtra(EXTRA_POINTER_LABEL, label)
            }
            runCatching { context.startService(intent) }
        }
    }
}
