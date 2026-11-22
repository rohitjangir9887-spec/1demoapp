package com.example.screen

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenCaptureState(
    val isSharingActive: Boolean = false,
    val latestBitmap: Bitmap? = null,
    val lastCapturedTimestampMs: Long = 0L,
    val captureSourceLabel: String = "Idle",
    val statusMessage: String = "Tap Start Screen Share or pick a screenshot to let Aura AI see your screen."
)

object ScreenCaptureStore {
    private val _state = MutableStateFlow(ScreenCaptureState())
    val state: StateFlow<ScreenCaptureState> = _state.asStateFlow()

    fun setSharingActive(active: Boolean, message: String) {
        _state.value = _state.value.copy(
            isSharingActive = active,
            captureSourceLabel = if (active) "Live Screen Share" else "Idle",
            statusMessage = message
        )
    }

    fun updateCapturedFrame(bitmap: Bitmap, sourceLabel: String) {
        _state.value = _state.value.copy(
            latestBitmap = bitmap,
            lastCapturedTimestampMs = System.currentTimeMillis(),
            captureSourceLabel = sourceLabel,
            statusMessage = "Frame captured (${bitmap.width}x${bitmap.height}) • Ready for Aura Vision"
        )
    }

    fun clearCapturedFrame() {
        _state.value = _state.value.copy(
            latestBitmap = null,
            lastCapturedTimestampMs = 0L,
            captureSourceLabel = if (_state.value.isSharingActive) "Live Screen Share" else "Idle",
            statusMessage = if (_state.value.isSharingActive) {
                "Live screen session active. Tap Capture Now anytime."
            } else {
                "Tap Start Screen Share or pick a screenshot to let Aura AI see your screen."
            }
        )
    }
}
