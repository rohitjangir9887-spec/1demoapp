package com.example.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

private const val TAG = "ScreenCaptureService"
private const val CHANNEL_ID = "julie_screen_share_channel"
private const val NOTIFICATION_ID = 4102

/**
 * Continuous adaptive MediaProjection screen-streaming service (1–3 FPS configurable).
 * Fixes the frozen single-snapshot bug by attaching an ImageReader.OnImageAvailableListener
 * on a dedicated HandlerThread, deduplicating static frames with a fast pixel-sample hash,
 * and supporting Android 14+ content resize & immediate STOP from notification or UI.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastEmitMs: Long = 0L
    @Volatile
    private var lastFrameHash: Long = 0L
    @Volatile
    private var forceNextFrame: Boolean = false
    @Volatile
    private var currentWidth: Int = 540
    @Volatile
    private var currentHeight: Int = 960
    @Volatile
    private var currentDensity: Int = 320

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            stopCaptureResources()
            ScreenCaptureStore.setSharingActive(false, "Live screen share ended by system.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            super.onCapturedContentResize(width, height)
            if (width > 120 && height > 120) {
                val scaledW = (width / 2).coerceIn(320, 720)
                val scaledH = (height / 2).coerceIn(480, 1280)
                captureHandler?.post {
                    rebuildSurfaceIfNeeded(scaledW, scaledH)
                }
            }
        }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

            val now = System.currentTimeMillis()
            val targetFps = ScreenCaptureStore.state.value.targetFps.coerceIn(1, 3)
            val minIntervalMs = (1000L / targetFps).coerceAtLeast(300L)

            if (!forceNextFrame && (now - lastEmitMs) < minIntervalMs) {
                return@OnImageAvailableListener
            }

            val bitmap = imageToBitmap(image) ?: return@OnImageAvailableListener
            val frameHash = computeFastFrameHash(bitmap)

            // Skip identical frames unless forced or > 4s heartbeat elapsed
            if (!forceNextFrame && frameHash == lastFrameHash && (now - lastEmitMs) < 4000L) {
                bitmap.recycle()
                ScreenCaptureStore.recordDuplicateFrameSkipped()
                return@OnImageAvailableListener
            }

            forceNextFrame = false
            lastFrameHash = frameHash
            lastEmitMs = now
            ScreenCaptureStore.updateCapturedFrame(
                bitmap = bitmap,
                sourceLabel = "LIVE Stream (${targetFps} FPS)",
                isLiveStreamFrame = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing live screen frame", e)
        } finally {
            try {
                image?.close()
            } catch (_: Exception) {}
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
                    forceNextFrame = true
                    captureHandler?.post {
                        val reader = imageReader ?: return@post
                        imageAvailableListener.onImageAvailable(reader)
                    }
                }
            }

            ACTION_SET_FPS -> {
                val fps = intent.getIntExtra(EXTRA_TARGET_FPS, 2)
                ScreenCaptureStore.setTargetFps(fps)
            }

            ACTION_STOP -> {
                stopCaptureResources()
                ScreenCaptureStore.setSharingActive(false, "Live screen share stopped.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafe() {
        createNotificationChannel()
        val openAppIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Julie AI • Live Screen Share Active")
            .setContentText("Continuous vision stream running • Tap Stop anytime")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopIntent)
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

            val thread = HandlerThread("JulieScreenCapture").apply { start() }
            captureThread = thread
            val handler = Handler(thread.looper)
            captureHandler = handler

            val metrics = resources.displayMetrics
            // Balanced resolution for smooth 1-3 FPS streaming + fast NIM Vision payloads
            currentWidth = (metrics.widthPixels / 2).coerceIn(360, 720)
            currentHeight = (metrics.heightPixels / 2).coerceIn(640, 1280)
            currentDensity = metrics.densityDpi

            lastEmitMs = 0L
            lastFrameHash = 0L
            forceNextFrame = true

            val reader = ImageReader.newInstance(currentWidth, currentHeight, PixelFormat.RGBA_8888, 3)
            reader.setOnImageAvailableListener(imageAvailableListener, handler)
            imageReader = reader

            virtualDisplay = projection.createVirtualDisplay(
                "JulieLiveScreenVision",
                currentWidth,
                currentHeight,
                currentDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            ScreenCaptureStore.setSharingActive(
                true,
                "LIVE Stream started (${ScreenCaptureStore.state.value.targetFps} FPS) • Capturing real-time frames"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen projection", e)
            ScreenCaptureStore.setSharingActive(false, "Screen share error: ${e.localizedMessage}")
            stopSelf()
        }
    }

    private fun rebuildSurfaceIfNeeded(newWidth: Int, newHeight: Int) {
        if (newWidth == currentWidth && newHeight == currentHeight) return
        val display = virtualDisplay ?: return
        val handler = captureHandler ?: return
        try {
            currentWidth = newWidth
            currentHeight = newHeight
            val oldReader = imageReader
            val newReader = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, 3)
            newReader.setOnImageAvailableListener(imageAvailableListener, handler)
            imageReader = newReader
            display.resize(newWidth, newHeight, currentDensity)
            display.surface = newReader.surface
            oldReader?.setOnImageAvailableListener(null, null)
            oldReader?.close()
            forceNextFrame = true
        } catch (e: Exception) {
            Log.w(TAG, "Surface resize failed: ${e.message}")
        }
    }

    private fun imageToBitmap(img: Image): Bitmap? {
        val plane = img.planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0) return null
        val rowPadding = rowStride - pixelStride * img.width
        val paddedWidth = img.width + rowPadding / pixelStride
        if (paddedWidth <= 0 || img.height <= 0) return null

        val rawBitmap = Bitmap.createBitmap(paddedWidth, img.height, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth != img.width) {
            val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, img.width, img.height)
            rawBitmap.recycle()
            cropped
        } else {
            rawBitmap
        }
    }

    private fun computeFastFrameHash(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0L
        var hash = 1125899906842597L
        val stepX = (w / 10).coerceAtLeast(1)
        val stepY = (h / 10).coerceAtLeast(1)
        var y = stepY / 2
        while (y < h) {
            var x = stepX / 2
            while (x < w) {
                hash = 31L * hash + bitmap.getPixel(x, y).toLong()
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    private fun stopCaptureResources() {
        forceNextFrame = false
        try {
            imageReader?.setOnImageAvailableListener(null, null)
        } catch (_: Exception) {}
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

        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {}
        captureThread = null
        captureHandler = null
    }

    override fun onDestroy() {
        stopCaptureResources()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Julie AI Live Screen Share",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while Julie AI is streaming your shared screen."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.example.screen.ACTION_START"
        const val ACTION_CAPTURE_FRAME = "com.example.screen.ACTION_CAPTURE_FRAME"
        const val ACTION_SET_FPS = "com.example.screen.ACTION_SET_FPS"
        const val ACTION_STOP = "com.example.screen.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_TARGET_FPS = "extra_target_fps"

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

        fun setTargetFps(context: Context, fps: Int) {
            ScreenCaptureStore.setTargetFps(fps)
            if (ScreenCaptureStore.state.value.isSharingActive) {
                val intent = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ACTION_SET_FPS
                    putExtra(EXTRA_TARGET_FPS, fps)
                }
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
