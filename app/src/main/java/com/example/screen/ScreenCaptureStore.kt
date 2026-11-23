package com.example.screen

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenCaptureState(
    val isSharingActive: Boolean = false,
    val latestBitmap: Bitmap? = null,
    val lastCapturedTimestampMs: Long = 0L,
    val frameCount: Long = 0L,
    val currentFps: Float = 0f,
    val targetFps: Int = 2,
    val droppedDuplicateFrames: Long = 0L,
    val resolutionLabel: String = "",
    val captureSourceLabel: String = "Idle",
    val statusMessage: String = "Tap Start Live Share or pick a screenshot to let Julie AI see your screen."
)

object ScreenCaptureStore {
    private val _state = MutableStateFlow(ScreenCaptureState())
    val state: StateFlow<ScreenCaptureState> = _state.asStateFlow()

    @Volatile
    private var fpsWindowStartMs: Long = 0L
    @Volatile
    private var fpsWindowFrames: Int = 0

    fun setTargetFps(fps: Int) {
        val clamped = fps.coerceIn(1, 3)
        _state.value = _state.value.copy(targetFps = clamped)
    }

    fun setSharingActive(active: Boolean, message: String) {
        if (!active) {
            fpsWindowStartMs = 0L
            fpsWindowFrames = 0
        }
        _state.value = _state.value.copy(
            isSharingActive = active,
            currentFps = if (active) _state.value.currentFps else 0f,
            frameCount = if (active) _state.value.frameCount else 0L,
            droppedDuplicateFrames = if (active) _state.value.droppedDuplicateFrames else 0L,
            captureSourceLabel = if (active) "Live Stream" else "Idle",
            statusMessage = message
        )
    }

    fun updateCapturedFrame(bitmap: Bitmap, sourceLabel: String, isLiveStreamFrame: Boolean = false) {
        val now = System.currentTimeMillis()
        var computedFps = _state.value.currentFps
        if (isLiveStreamFrame) {
            if (fpsWindowStartMs == 0L) {
                fpsWindowStartMs = now
                fpsWindowFrames = 1
                computedFps = _state.value.targetFps.toFloat()
            } else {
                fpsWindowFrames++
                val elapsed = now - fpsWindowStartMs
                if (elapsed >= 1000L) {
                    computedFps = (fpsWindowFrames * 1000f) / elapsed.toFloat()
                    fpsWindowStartMs = now
                    fpsWindowFrames = 0
                }
            }
        } else {
            computedFps = 0f
        }

        val nextCount = _state.value.frameCount + 1L
        val res = "${bitmap.width}×${bitmap.height}"
        _state.value = _state.value.copy(
            latestBitmap = bitmap,
            lastCapturedTimestampMs = now,
            frameCount = nextCount,
            currentFps = computedFps,
            resolutionLabel = res,
            captureSourceLabel = sourceLabel,
            statusMessage = if (isLiveStreamFrame) {
                "LIVE • Frame #$nextCount • ${"%.1f".format(computedFps)} FPS • $res"
            } else {
                "Static Frame ($res) • Ready for Julie Vision"
            }
        )
    }

    fun recordDuplicateFrameSkipped() {
        val nextDropped = _state.value.droppedDuplicateFrames + 1L
        _state.value = _state.value.copy(
            droppedDuplicateFrames = nextDropped
        )
    }

    fun clearCapturedFrame() {
        _state.value = _state.value.copy(
            latestBitmap = null,
            lastCapturedTimestampMs = 0L,
            frameCount = 0L,
            currentFps = 0f,
            resolutionLabel = "",
            captureSourceLabel = if (_state.value.isSharingActive) "Live Stream" else "Idle",
            statusMessage = if (_state.value.isSharingActive) {
                "Live stream active • waiting for next changed frame..."
            } else {
                "Tap Start Live Share or pick a screenshot to let Julie AI see your screen."
            }
        )
    }
}
