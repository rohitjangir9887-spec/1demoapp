package com.example.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

private const val TAG = "ScreenCaptureService"
private const val CHANNEL_ID = "aura_screen_share_channel"
private const val NOTIFICATION_ID = 4102

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var capturePending = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopCaptureResources()
            ScreenCaptureStore.setSharingActive(false, "Screen share session ended.")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    ScreenCaptureStore.setSharingActive(false, "Screen share permission was not granted.")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundSafe()
                startProjection(resultCode, resultData)
            }

            ACTION_CAPTURE_FRAME -> {
                if (mediaProjection != null) {
                    capturePending = true
                    captureSingleFrameNow()
                }
            }

            ACTION_STOP -> {
                stopCaptureResources()
                ScreenCaptureStore.setSharingActive(false, "Screen share stopped.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafe() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aura AI Live Screen Share")
            .setContentText("Screen vision session active for instant AI assistance")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        try {
            stopCaptureResources()
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data) ?: run {
                ScreenCaptureStore.setSharingActive(false, "Could not initialize MediaProjection.")
                stopSelf()
                return
            }
            mediaProjection = projection
            projection.registerCallback(projectionCallback, mainHandler)

            val metrics = resources.displayMetrics
            // Scale down slightly for fast AI multimodal payloads (< 20MB)
            val width = (metrics.widthPixels / 2).coerceAtLeast(360)
            val height = (metrics.heightPixels / 2).coerceAtLeast(640)
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "AuraScreenVision",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )

            ScreenCaptureStore.setSharingActive(
                true,
                "Live Screen Share active • Tap Capture Frame anytime"
            )

            // Capture an initial frame shortly after virtual display warms up
            capturePending = true
            mainHandler.postDelayed({ captureSingleFrameNow() }, 450L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen projection", e)
            ScreenCaptureStore.setSharingActive(false, "Screen share error: ${e.localizedMessage}")
            stopSelf()
        }
    }

    private fun captureSingleFrameNow() {
        val reader = imageReader ?: return
        try {
            val image = reader.acquireLatestImage()
            if (image == null) {
                if (capturePending) {
                    mainHandler.postDelayed({ captureSingleFrameNow() }, 250L)
                }
                return
            }
            image.use { img ->
                val plane = img.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * img.width

                val rawBitmap = Bitmap.createBitmap(
                    img.width + rowPadding / pixelStride,
                    img.height,
                    Bitmap.Config.ARGB_8888
                )
                rawBitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, img.width, img.height)
                if (cropped != rawBitmap) {
                    rawBitmap.recycle()
                }
                capturePending = false
                ScreenCaptureStore.updateCapturedFrame(cropped, "Live Screen Share")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen frame", e)
        }
    }

    private fun stopCaptureResources() {
        capturePending = false
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    override fun onDestroy() {
        stopCaptureResources()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura AI Screen Share",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while Aura AI is assisting with your shared screen."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.screen.ACTION_START"
        const val ACTION_CAPTURE_FRAME = "com.example.screen.ACTION_CAPTURE_FRAME"
        const val ACTION_STOP = "com.example.screen.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun captureFrame(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_CAPTURE_FRAME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
