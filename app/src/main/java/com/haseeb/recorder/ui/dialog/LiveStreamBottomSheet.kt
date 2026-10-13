package com.haseeb.recorder.ui.dialog

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.haseeb.recorder.R
import com.haseeb.recorder.databinding.SheetLiveStreamBinding
import com.haseeb.recorder.streaming.LiveStreamConfigManager
import com.haseeb.recorder.streaming.LiveStreamProjectionService
import com.haseeb.recorder.streaming.LiveStreamStateManager

/*
 * Redesigned Bottom Sheet dialog for YouTube Live Streaming:
 * Uses Stream Key input, 16:9 widescreen screen capture, and initiates
 * MediaProjection directly using modern ActivityResult contracts.
 */
class LiveStreamBottomSheet : BottomSheetDialogFragment(), LiveStreamStateManager.Listener {

    private var _binding: SheetLiveStreamBinding? = null
    private val binding get() = _binding!!
    private lateinit var configManager: LiveStreamConfigManager

    private lateinit var runtimePermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runtimePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
            if (!audioGranted && configManager.isAudioMicEnabled) {
                Toast.makeText(requireContext(), "Microphone permission is required to stream audio", Toast.LENGTH_SHORT).show()
            }
            requestScreenCapture()
        }

        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null) {
                startStreamingService(result.resultCode, result.data!!)
                dismissAllowingStateLoss()
            } else {
                Toast.makeText(requireContext(), getString(R.string.live_stream_permission_required), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetLiveStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configManager = LiveStreamConfigManager(requireContext())

        // Ensure 16:9 widescreen YouTube RTMPS defaults
        configManager.serverUrl = LiveStreamConfigManager.URL_YOUTUBE_RTMPS
        configManager.protocol = LiveStreamConfigManager.PROTOCOL_RTMPS
        configManager.preset = LiveStreamConfigManager.PRESET_YOUTUBE_RTMPS
        configManager.isShortsLive = false

        setupStreamKeyUI()
        setupVideoOptionsUI()
        setupActionButtons()

        LiveStreamStateManager.addListener(this)
        syncUiState(LiveStreamStateManager.isStreaming, LiveStreamStateManager.isConnecting)
    }

    private fun setupStreamKeyUI() {
        binding.etServerUrl.setText(configManager.serverUrl)
        binding.etStreamKey.setText(configManager.streamKey)

        binding.etStreamKey.addTextChangedListener {
            configManager.streamKey = it?.toString()?.trim() ?: ""
        }

        binding.btnPasteKey.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasteText = clipData.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if (pasteText.isNotEmpty()) {
                    val cleanKey = pasteText.replace("\"", "").replace("'", "").trim()
                    binding.etStreamKey.setText(cleanKey)
                    configManager.streamKey = cleanKey
                    Toast.makeText(requireContext(), "Stream key pasted!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        // Hide complex protocol presets and SRT settings to keep clean & focused on YouTube Live
        binding.cardProtocolSettings.visibility = View.VISIBLE
    }

    private fun setupVideoOptionsUI() {
        // Enforce 16:9 widescreen
        binding.buttonGroupRatio.check(R.id.btnRatio16x9)
        configManager.isShortsLive = false
        binding.buttonGroupRatio.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                configManager.isShortsLive = (checkedId == R.id.btnRatio9x16)
            }
        }

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
            }
        }

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

        binding.switchStreamMic.isChecked = configManager.isAudioMicEnabled
        binding.switchStreamMic.setOnCheckedChangeListener { _, isChecked ->
            configManager.isAudioMicEnabled = isChecked
        }

        binding.switchLiveFacecam.isChecked = configManager.isFacecamEnabled
        binding.switchLiveFacecam.setOnCheckedChangeListener { _, isChecked ->
            configManager.isFacecamEnabled = isChecked
        }
    }

    private fun setupActionButtons() {
        binding.btnGoLive.setOnClickListener {
            if (LiveStreamStateManager.isStreaming || LiveStreamStateManager.isConnecting) {
                val stopIntent = Intent(requireContext(), LiveStreamProjectionService::class.java).apply {
                    action = LiveStreamProjectionService.ACTION_STOP_STREAM
                }
                requireContext().startService(stopIntent)
            } else {
                val rawKey = binding.etStreamKey.text?.toString()?.trim() ?: ""
                val cleanKey = rawKey.replace("\"", "").replace("'", "").trim()
                if (cleanKey.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.yt_live_key_required_error), Toast.LENGTH_LONG).show()
                    binding.etStreamKey.requestFocus()
                    return@setOnClickListener
                }

                configManager.streamKey = cleanKey
                binding.etStreamKey.setText(cleanKey)

                checkPermissionsAndProceed()
            }
        }
    }

    private fun checkPermissionsAndProceed() {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missing.isNotEmpty()) {
            runtimePermissionLauncher.launch(missing.toTypedArray())
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val projectionManager = requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            Toast.makeText(requireContext(), "MediaProjection not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStreamingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(requireContext(), LiveStreamProjectionService::class.java).apply {
            action = LiveStreamProjectionService.ACTION_START_STREAM
            putExtra(LiveStreamProjectionService.EXTRA_RESULT_CODE, resultCode)
            putExtra(LiveStreamProjectionService.EXTRA_DATA, data)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not start live stream: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun syncUiState(isStreaming: Boolean, isConnecting: Boolean) {
        if (_binding == null) return

        if (isStreaming) {
            binding.tvLiveBadge.text = getString(R.string.live_stream_status_live)
            binding.layoutStreamStats.visibility = View.VISIBLE
            binding.btnGoLive.text = getString(R.string.live_stream_btn_stop_live)
            binding.btnGoLive.isEnabled = true
            binding.btnGoLive.setIconResource(R.drawable.ic_stop)
        } else if (isConnecting) {
            binding.tvLiveBadge.text = getString(R.string.live_stream_status_connecting)
            binding.layoutStreamStats.visibility = View.VISIBLE
            binding.tvStreamDuration.text = "Connecting…"
            binding.btnGoLive.text = "Connecting…"
            binding.btnGoLive.isEnabled = false
        } else {
            binding.tvLiveBadge.text = getString(R.string.live_stream_status_offline)
            binding.layoutStreamStats.visibility = View.GONE
            binding.btnGoLive.text = getString(R.string.live_stream_btn_go_live)
            binding.btnGoLive.isEnabled = true
            binding.btnGoLive.setIconResource(R.drawable.ic_live_stream)
        }
    }

    override fun onStreamStateChanged(isStreaming: Boolean, isConnecting: Boolean) {
        if (_binding == null) return
        activity?.runOnUiThread {
            syncUiState(isStreaming, isConnecting)
        }
    }

    override fun onStreamStatsUpdated(bitrateBps: Long, durationMs: Long) {
        if (_binding == null) return
        activity?.runOnUiThread {
            val sec = durationMs / 1000
            binding.tvStreamDuration.text = String.format("%02d:%02d", sec / 60, sec % 60)
            binding.tvStreamBitrate.text = "${bitrateBps / 1000} kbps"
        }
    }

    override fun onStreamError(error: String) {
        if (_binding == null) return
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Stream error: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        LiveStreamStateManager.removeListener(this)
        _binding = null
        super.onDestroyView()
    }
}
