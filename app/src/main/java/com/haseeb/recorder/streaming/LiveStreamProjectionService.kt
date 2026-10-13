package com.haseeb.recorder.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.haseeb.recorder.R
import com.haseeb.recorder.camera.CameraOverlayService
import com.haseeb.recorder.ui.activity.CameraPermissionActivity
import com.haseeb.recorder.ui.activity.MainActivity
import com.pedro.common.ConnectChecker
import com.pedro.library.base.DisplayBase
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.library.srt.SrtDisplay
import com.pedro.srt.srt.packets.control.handshake.EncryptionType

/*
 * Foreground streaming service that captures the screen in 16:9 or 9:16 ratio
 * and streams to YouTube or custom media servers via RTMP, RTMPS, or SRT.
 * Robust error handling, encoder fallback, and safe Android 14+ FGS lifecycle.
 */
class LiveStreamProjectionService : Service(), ConnectChecker {

    companion object {
        const val TAG = "LiveStreamService"
        const val CHANNEL_ID = "live_stream_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_STREAM = "com.haseeb.recorder.ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "com.haseeb.recorder.ACTION_STOP_STREAM"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
    }

    private lateinit var configManager: LiveStreamConfigManager
    private var displayStream: DisplayBase? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statsRunnable: Runnable? = null
    private var isFgsActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        configManager = LiveStreamConfigManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Immediate startForeground call on API 26+ to satisfy startForegroundService contract
        startInitialForeground()

        when (intent?.action) {
            ACTION_START_STREAM -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (resultCode != -1 && data != null) {
                    startStreamingSession(resultCode, data)
                } else {
                    stopStreamingSession()
                }
            }
            ACTION_STOP_STREAM -> {
                stopStreamingSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInitialForeground() {
        if (isFgsActive) return
        val hasAudioPerm = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (hasAudioPerm && configManager.isAudioMicEnabled) {
                fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, buildNotification("Initializing live stream session…"), fgsType)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Initializing live stream session…"))
        }
        isFgsActive = true
    }

    private fun startStreamingSession(resultCode: Int, data: Intent) {
        if (LiveStreamStateManager.isStreaming || LiveStreamStateManager.isConnecting) return

        LiveStreamStateManager.onConnecting()
        acquireWakeLock()

        val endpointUrl = configManager.getFullStreamEndpoint()
        if (endpointUrl.isBlank()) {
            mainHandler.post {
                Toast.makeText(this, "Stream URL or key is missing", Toast.LENGTH_SHORT).show()
            }
            LiveStreamStateManager.onStreamStopped("Stream URL or key is missing")
            stopStreamingSession()
            return
        }

        updateNotification("Connecting to stream server…")

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val (streamWidth, streamHeight) = configManager.getStreamResolution()
        val streamFps = configManager.fps
        val streamBitrate = configManager.bitrate
        val rotation = 0
        val hasAudioPerm = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        try {
            // Instantiate protocol-specific engine
            val isSrt = configManager.protocol == LiveStreamConfigManager.PROTOCOL_SRT
            val display: DisplayBase = if (isSrt) {
                val srtDisplay = SrtDisplay(this, true, this)
                val pass = configManager.srtPassphrase.trim()
                if (pass.isNotEmpty()) {
                    try {
                        srtDisplay.getStreamClient().setPassphrase(pass, EncryptionType.AES128)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed setting SRT passphrase: ${e.message}")
                    }
                }
                srtDisplay
            } else {
                RtmpDisplay(this, true, this)
            }
            displayStream = display

            // Provide MediaProjection intent result before preparing media
            display.setIntentResult(resultCode, data)

            // Try primary video encoder preparation
            var videoPrepared = try {
                display.prepareVideo(
                    streamWidth, streamHeight, streamFps, streamBitrate, rotation, metrics.densityDpi
                )
            } catch (e: Exception) {
                Log.w(TAG, "prepareVideo exception on primary resolution ${streamWidth}x${streamHeight}", e)
                false
            }

            // Fallback resolution (720p or standard 1280x720) if hardware encoder refused higher resolution
            if (!videoPrepared && (streamWidth > 1280 || streamHeight > 1280)) {
                val (fbW, fbH) = if (configManager.isShortsLive) Pair(720, 1280) else Pair(1280, 720)
                Log.i(TAG, "Falling back to ${fbW}x${fbH} encoder resolution")
                videoPrepared = try {
                    display.prepareVideo(
                        fbW, fbH, 30, (streamBitrate * 0.75).toInt().coerceAtLeast(1_500_000), rotation, metrics.densityDpi
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback prepareVideo exception", e)
                    false
                }
            }

            // If still not prepared, try standard 720p 30fps
            if (!videoPrepared) {
                Log.i(TAG, "Trying safe standard 720p video preparation")
                videoPrepared = try {
                    display.prepareVideo(1280, 720, 30, 2_000_000, 0, metrics.densityDpi)
                } catch (e: Exception) {
                    false
                }
            }

            if (!videoPrepared) {
                Log.e(TAG, "Video encoder preparation failed")
                mainHandler.post {
                    Toast.makeText(this, "Hardware encoder failed for ${streamWidth}x${streamHeight}", Toast.LENGTH_LONG).show()
                }
                LiveStreamStateManager.onStreamStopped("Video encoder failed for ${streamWidth}x${streamHeight}")
                stopStreamingSession()
                return
            }

            // Audio preparation with graceful error containment (Microphone)
            if (hasAudioPerm && configManager.isAudioMicEnabled) {
                try {
                    val audioPrepared = display.prepareAudio(128_000, 48_000, true, true, true)
                    if (!audioPrepared) {
                        Log.w(TAG, "Audio preparation stereo failed, trying mono 44100Hz")
                        display.prepareAudio(96_000, 44_100, false, false, false)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not initialize audio encoder, proceeding with video only", e)
                }
            }

            display.startStream(endpointUrl)
            startStatsUpdater()

            if (configManager.isFacecamEnabled && !CameraOverlayService.isCameraRunning) {
                mainHandler.post {
                    CameraPermissionActivity.launch(this)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting live stream session", e)
            val errMessage = e.message ?: "Failed to start stream"
            mainHandler.post {
                Toast.makeText(this, "Live stream start error: $errMessage", Toast.LENGTH_LONG).show()
            }
            LiveStreamStateManager.onStreamStopped(errMessage)
            stopStreamingSession()
        }
    }


    private fun stopStreamingSession() {
        mainHandler.removeCallbacksAndMessages(null)
        stopStatsUpdater()
        if (CameraOverlayService.isCameraRunning) {
            try {
                stopService(Intent(this, CameraOverlayService::class.java))
            } catch (_: Exception) {}
        }
        try {
            if (displayStream?.isStreaming == true) {
                displayStream?.stopStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping stream", e)
        }
        cleanup()
        LiveStreamStateManager.onStreamStopped()
        if (isFgsActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isFgsActive = false
        }
        stopSelf()
    }

    private fun startStatsUpdater() {
        stopStatsUpdater()
        statsRunnable = object : Runnable {
            override fun run() {
                if (LiveStreamStateManager.isStreaming) {
                    val bitrate = displayStream?.bitrate?.toLong() ?: 0L
                    LiveStreamStateManager.onBitrateUpdated(bitrate)
                    val formattedBitrate = "${bitrate / 1000} kbps"
                    val (w, h) = configManager.getStreamResolution()
                    val durationSec = LiveStreamStateManager.getDurationMs() / 1000
                    val durStr = String.format("%02d:%02d", durationSec / 60, durationSec % 60)
                    updateNotification("🔴 LIVE • ${w}x${h} • $formattedBitrate • $durStr")
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
        mainHandler.postDelayed(statsRunnable!!, 1000)
    }

    private fun stopStatsUpdater() {
        statsRunnable?.let { mainHandler.removeCallbacks(it) }
        statsRunnable = null
    }

    // ConnectChecker Callbacks
    override fun onConnectionStarted(url: String) {
        Log.i(TAG, "Connection started to: $url")
        mainHandler.post {
            updateNotification("Connecting to $url…")
        }
    }

    override fun onConnectionSuccess() {
        Log.i(TAG, "Connection successful!")
        LiveStreamStateManager.onStreamStarted()
        mainHandler.post {
            val (w, h) = configManager.getStreamResolution()
            updateNotification("🔴 LIVE • ${w}x${h} (${configManager.protocol.uppercase()})")
            Toast.makeText(this@LiveStreamProjectionService, getString(R.string.live_stream_toast_started), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Connection failed: $reason")
        // If unencrypted RTMP failed (likely ISP or port 1935 blocked), suggest or switch to RTMPS
        val helpTip = if (configManager.protocol == LiveStreamConfigManager.PROTOCOL_RTMP) {
            " (Tip: YouTube RTMPS on port 443 is recommended if port 1935 is blocked)"
        } else {
            ""
        }
        val fullReason = "$reason$helpTip"
        LiveStreamStateManager.onStreamStopped(fullReason)
        mainHandler.post {
            Toast.makeText(this@LiveStreamProjectionService, "Live stream connection failed: $fullReason", Toast.LENGTH_LONG).show()
            stopStreamingSession()
        }
    }


    override fun onNewBitrate(bitrate: Long) {
        LiveStreamStateManager.onBitrateUpdated(bitrate)
    }

    override fun onDisconnect() {
        Log.i(TAG, "Disconnected from stream")
        mainHandler.post {
            if (LiveStreamStateManager.isStreaming) {
                stopStreamingSession()
            }
        }
    }

    override fun onAuthError() {
        Log.e(TAG, "Auth error on stream")
        mainHandler.post {
            Toast.makeText(this@LiveStreamProjectionService, "Stream key authentication failed. Check your stream key.", Toast.LENGTH_LONG).show()
            stopStreamingSession()
        }
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "Auth success")
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenRecorder:LiveStream").apply {
                acquire(4 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock acquisition failed", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun cleanup() {
        releaseWakeLock()
        displayStream = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.live_stream_notif_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "YouTube Live stream status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveStreamProjectionService::class.java).apply {
            action = ACTION_STOP_STREAM
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.live_stream_notif_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_live_stream)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_stop, getString(R.string.live_stream_notif_btn_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String) {
        if (!isFgsActive) return
        val notification = buildNotification(statusText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopStreamingSession()
        super.onDestroy()
    }
}
