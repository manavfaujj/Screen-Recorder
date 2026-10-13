package com.haseeb.recorder.ui.activity

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.haseeb.recorder.R
import com.haseeb.recorder.camera.CameraOverlayService
import com.haseeb.recorder.data.ConfigManager
import com.haseeb.recorder.databinding.ActivityYoutubeLiveBinding
import com.haseeb.recorder.recording.RecordingStateManager
import com.haseeb.recorder.streaming.LiveStreamConfigManager
import com.haseeb.recorder.streaming.LiveStreamProjectionService
import com.haseeb.recorder.streaming.LiveStreamStateManager
import com.haseeb.recorder.util.applyBottomInsets
import com.haseeb.recorder.util.applySystemBarInsets
import com.haseeb.recorder.util.applyTopInsets

/*
 * Redesigned YouTube Live Stream Activity.
 * Direct Stream Key input + 16:9 Landscape screencasting.
 * Handles MediaProjection authorization directly in the foreground activity
 * to eliminate background FGS launch rejections on Android 14+.
 */
class YouTubeLiveActivity : AppCompatActivity(), LiveStreamStateManager.Listener {

    private lateinit var binding: ActivityYoutubeLiveBinding
    private lateinit var configManager: LiveStreamConfigManager
    private lateinit var appConfigManager: ConfigManager

    // Permission launcher for Record Audio & Post Notifications
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (!audioGranted && configManager.isAudioMicEnabled) {
            Toast.makeText(this, "Microphone permission is required to stream audio", Toast.LENGTH_SHORT).show()
        }
        // Proceed directly to system Screen Capture consent
        launchScreenCaptureRequest()
    }

    // Modern ActivityResult launcher for MediaProjection
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startStreamingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, getString(R.string.live_stream_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val recordingStateListener = object : RecordingStateManager.StateListener {
        override fun onStateChanged() {
            runOnUiThread {
                updateFacecamUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYoutubeLiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.applyTopInsets()
        binding.appBarLayout.applySystemBarInsets()
        binding.nestedScrollView.applyBottomInsets()

        configManager = LiveStreamConfigManager(this)
        appConfigManager = ConfigManager(this)

        // Always ensure YouTube RTMPS ingest on port 443 and 16:9 landscape aspect ratio
        configManager.serverUrl = LiveStreamConfigManager.URL_YOUTUBE_RTMPS
        configManager.protocol = LiveStreamConfigManager.PROTOCOL_RTMPS
        configManager.preset = LiveStreamConfigManager.PRESET_YOUTUBE_RTMPS
        configManager.isShortsLive = false

        setupToolbar()
        setupStreamKeyUI()
        setupQualityUI()
        setupAudioUI()
        setupFacecamUI()
        setupLiveActionUI()

        LiveStreamStateManager.addListener(this)
        RecordingStateManager.addListener(recordingStateListener)
        syncState(LiveStreamStateManager.isStreaming, LiveStreamStateManager.isConnecting)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupStreamKeyUI() {
        binding.etStreamKey.setText(configManager.streamKey)
        binding.etServerUrl.setText(configManager.serverUrl)

        binding.etStreamKey.addTextChangedListener {
            configManager.streamKey = it?.toString()?.trim() ?: ""
        }

        binding.etServerUrl.addTextChangedListener {
            val text = it?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                configManager.serverUrl = text
            }
        }

        binding.btnPasteKey.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if (pasteText.isNotEmpty()) {
                    val sanitized = pasteText.replace("\"", "").replace("'", "").trim()
                    binding.etStreamKey.setText(sanitized)
                    configManager.streamKey = sanitized
                    Toast.makeText(this, "Stream key pasted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenYouTubeStudio.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/channel/live"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser for YouTube Studio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupQualityUI() {
        // Enforce 16:9 widescreen
        binding.buttonGroupRatio.check(R.id.btnRatio16x9)
        configManager.isShortsLive = false
        binding.buttonGroupRatio.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                configManager.isShortsLive = (checkedId == R.id.btnRatio9x16)
                updateResolutionSummary()
            }
        }

        // Resolution selection (1080p / 720p / 480p)
        when (configManager.resolution) {
            LiveStreamConfigManager.RESOLUTION_1080P -> binding.buttonGroupResolution.check(R.id.btnRes1080)
            LiveStreamConfigManager.RESOLUTION_720P -> binding.buttonGroupResolution.check(R.id.btnRes720)
            LiveStreamConfigManager.RESOLUTION_480P -> binding.buttonGroupResolution.check(R.id.btnRes480)
            else -> binding.buttonGroupResolution.check(R.id.btnRes1080)
        }

        binding.buttonGroupResolution.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnRes1080 -> configManager.resolution = LiveStreamConfigManager.RESOLUTION_1080P
                    R.id.btnRes720 -> configManager.resolution = LiveStreamConfigManager.RESOLUTION_720P
                    R.id.btnRes480 -> configManager.resolution = LiveStreamConfigManager.RESOLUTION_480P
                }
                updateResolutionSummary()
            }
        }

        // Frame rate selection (60 FPS / 30 FPS)
        if (configManager.fps >= 60) {
            binding.buttonGroupFps.check(R.id.btnFps60)
        } else {
            binding.buttonGroupFps.check(R.id.btnFps30)
        }

        binding.buttonGroupFps.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                configManager.fps = if (checkedId == R.id.btnFps60) 60 else 30
            }
        }

        updateResolutionSummary()
    }

    private fun updateResolutionSummary() {
        val (w, h) = configManager.getStreamResolution()
        val ratioStr = if (configManager.isShortsLive) "9:16 Shorts" else "16:9 Landscape"
        binding.tvStreamResolution.text = "${w}x${h} ($ratioStr)"
    }

    private fun setupAudioUI() {
        binding.switchAudioMic.isChecked = configManager.isAudioMicEnabled
        binding.switchAudioMic.setOnCheckedChangeListener { _, isChecked ->
            configManager.isAudioMicEnabled = isChecked
        }
    }

    private fun setupFacecamUI() {
        binding.switchFacecam.isChecked = configManager.isFacecamEnabled
        binding.switchFacecam.setOnCheckedChangeListener { _, isChecked ->
            configManager.isFacecamEnabled = isChecked
            binding.layoutFacecamActions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.layoutFacecamActions.visibility = if (configManager.isFacecamEnabled) View.VISIBLE else View.GONE

        binding.btnToggleFacecamPreview.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            RecordingStateManager.toggleCamera(this)
        }

        binding.btnSwitchCamera.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (CameraOverlayService.isCameraRunning) {
                CameraOverlayService.switchCamera(this)
                Toast.makeText(this, "Camera flipped", Toast.LENGTH_SHORT).show()
            } else {
                appConfigManager.useFrontCamera = !appConfigManager.useFrontCamera
                val cam = if (appConfigManager.useFrontCamera) "Front" else "Back"
                Toast.makeText(this, "Facecam set to $cam camera", Toast.LENGTH_SHORT).show()
            }
        }

        updateFacecamUI()
    }

    private fun updateFacecamUI() {
        val isCameraRunning = CameraOverlayService.isCameraRunning
        if (isCameraRunning) {
            binding.btnToggleFacecamPreview.text = getString(R.string.yt_live_facecam_hide_btn)
            binding.btnToggleFacecamPreview.setIconResource(R.drawable.ic_close)
        } else {
            binding.btnToggleFacecamPreview.text = getString(R.string.yt_live_facecam_preview_btn)
            binding.btnToggleFacecamPreview.setIconResource(R.drawable.ic_camera)
        }
    }

    private fun setupLiveActionUI() {
        binding.btnGoLive.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            if (LiveStreamStateManager.isStreaming || LiveStreamStateManager.isConnecting) {
                // Stop broadcast
                val stopIntent = Intent(this, LiveStreamProjectionService::class.java).apply {
                    action = LiveStreamProjectionService.ACTION_STOP_STREAM
                }
                startService(stopIntent)
            } else {
                // Validate stream key
                val rawKey = binding.etStreamKey.text?.toString()?.trim() ?: ""
                val cleanKey = rawKey.replace("\"", "").replace("'", "").trim()
                if (cleanKey.isEmpty()) {
                    Toast.makeText(this, getString(R.string.yt_live_key_required_error), Toast.LENGTH_LONG).show()
                    binding.etStreamKey.requestFocus()
                    return@setOnClickListener
                }

                configManager.streamKey = cleanKey
                binding.etStreamKey.setText(cleanKey)

                // Start direct permission request flow
                checkPermissionsAndStart()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missingPermissions.isNotEmpty()) {
            runtimePermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            launchScreenCaptureRequest()
        }
    }

    private fun launchScreenCaptureRequest() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            val captureIntent = projectionManager.createScreenCaptureIntent()
            mediaProjectionLauncher.launch(captureIntent)
        } else {
            Toast.makeText(this, "Screen capture service not available on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun startStreamingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, LiveStreamProjectionService::class.java).apply {
            action = LiveStreamProjectionService.ACTION_START_STREAM
            putExtra(LiveStreamProjectionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(LiveStreamProjectionService.EXTRA_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start live stream: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun syncState(isStreaming: Boolean, isConnecting: Boolean) {
        if (isStreaming) {
            binding.tvLiveBadge.text = getString(R.string.live_stream_status_live)
            binding.tvLiveBadge.setTextColor(getColor(R.color.colorError))
            binding.layoutStreamStats.visibility = View.VISIBLE
            binding.tvStreamDuration.visibility = View.VISIBLE
            binding.btnGoLive.text = getString(R.string.yt_live_btn_stop_live)
            binding.btnGoLive.setIconResource(R.drawable.ic_stop)
            binding.btnGoLive.isEnabled = true
        } else if (isConnecting) {
            binding.tvLiveBadge.text = getString(R.string.live_stream_status_connecting)
            binding.tvLiveBadge.setTextColor(getColor(R.color.colorWarning))
            binding.layoutStreamStats.visibility = View.VISIBLE
            binding.tvStreamDuration.visibility = View.VISIBLE
            binding.tvStreamDuration.text = "Connecting…"
            binding.btnGoLive.text = "Connecting to YouTube…"
            binding.btnGoLive.isEnabled = false
        } else {
            binding.tvLiveBadge.text = getString(R.string.live_stream_status_offline)
            binding.tvLiveBadge.setTextColor(getColor(R.color.colorTextSecondary))
            binding.layoutStreamStats.visibility = View.GONE
            binding.tvStreamDuration.visibility = View.GONE
            binding.btnGoLive.text = getString(R.string.yt_live_btn_go_live)
            binding.btnGoLive.setIconResource(R.drawable.ic_live_stream)
            binding.btnGoLive.isEnabled = true
        }
        updateResolutionSummary()
    }

    override fun onResume() {
        super.onResume()
        updateFacecamUI()
        syncState(LiveStreamStateManager.isStreaming, LiveStreamStateManager.isConnecting)
    }

    override fun onStreamStateChanged(isStreaming: Boolean, isConnecting: Boolean) {
        runOnUiThread {
            syncState(isStreaming, isConnecting)
        }
    }

    override fun onStreamStatsUpdated(bitrateBps: Long, durationMs: Long) {
        runOnUiThread {
            val sec = durationMs / 1000
            binding.tvStreamDuration.text = String.format("%02d:%02d", sec / 60, sec % 60)
            binding.tvStreamBitrate.text = "${bitrateBps / 1000} kbps"
        }
    }

    override fun onStreamError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Live stream error: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        LiveStreamStateManager.removeListener(this)
        RecordingStateManager.removeListener(recordingStateListener)
        super.onDestroy()
    }
}
