package com.haseeb.recorder.streaming

import android.content.Context
import android.content.SharedPreferences

/*
 * Configuration manager for YouTube Live Streaming, ingest presets,
 * protocols (RTMP, RTMPS, SRT), resolutions, and channel account state.
 */
class LiveStreamConfigManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("livestream_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PRESET_YOUTUBE_RTMPS = "yt_rtmps"
        const val PRESET_YOUTUBE_RTMP = "yt_rtmp"
        const val PRESET_YOUTUBE_SRT = "yt_srt"
        const val PRESET_CUSTOM = "custom"

        const val PROTOCOL_RTMPS = "rtmps"
        const val PROTOCOL_RTMP = "rtmp"
        const val PROTOCOL_SRT = "srt"

        const val URL_YOUTUBE_RTMPS = "rtmps://a.rtmps.youtube.com/live2"
        const val URL_YOUTUBE_RTMP = "rtmp://a.rtmp.youtube.com/live2"
        const val URL_YOUTUBE_SRT = "srt://a.srt.youtube.com:443"

        const val RESOLUTION_1080P = "1080p"
        const val RESOLUTION_720P = "720p"
        const val RESOLUTION_480P = "480p"

        const val KEY_PRESET = "stream_preset"
        const val KEY_PROTOCOL = "stream_protocol"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_STREAM_KEY = "stream_key"
        const val KEY_SRT_PORT = "srt_port"
        const val KEY_SRT_LATENCY = "srt_latency"
        const val KEY_SRT_STREAM_ID = "srt_stream_id"
        const val KEY_SRT_PASSPHRASE = "srt_passphrase"
        const val KEY_RESOLUTION = "stream_resolution"
        const val KEY_IS_SHORTS = "stream_is_shorts"
        const val KEY_FPS = "stream_fps"
        const val KEY_BITRATE = "stream_bitrate"
        const val KEY_AUDIO_MIC = "stream_audio_mic"
        const val KEY_AUDIO_SYS = "stream_audio_sys"
        const val KEY_FACECAM_ENABLED = "stream_facecam_enabled"

        const val KEY_YT_CONNECTED = "yt_connected"
        const val KEY_YT_EMAIL = "yt_email"
        const val KEY_YT_CHANNEL_NAME = "yt_channel_name"
        const val KEY_YT_CHANNEL_HANDLE = "yt_channel_handle"
        const val KEY_YT_CHANNEL_ID = "yt_channel_id"
        const val KEY_YT_BROADCAST_TITLE = "yt_broadcast_title"
        const val KEY_YT_BROADCAST_DESC = "yt_broadcast_desc"
        const val KEY_YT_BROADCAST_PRIVACY = "yt_broadcast_privacy"
        const val KEY_YT_BROADCAST_CATEGORY = "yt_broadcast_category"
        const val KEY_YT_BROADCAST_ID = "yt_broadcast_id"
        const val KEY_YT_BROADCAST_ACTIVE = "yt_broadcast_active"

        const val DEFAULT_PRESET = PRESET_YOUTUBE_RTMPS
        const val DEFAULT_PROTOCOL = PROTOCOL_RTMPS
        const val DEFAULT_RESOLUTION = RESOLUTION_1080P
        const val DEFAULT_IS_SHORTS = false
        const val DEFAULT_FPS = 60
        const val DEFAULT_BITRATE = 4_500_000
        const val DEFAULT_SRT_PORT = 443
        const val DEFAULT_SRT_LATENCY = 200
    }


    var preset: String
        get() = prefs.getString(KEY_PRESET, DEFAULT_PRESET) ?: DEFAULT_PRESET
        set(value) = prefs.edit().putString(KEY_PRESET, value).apply()

    var protocol: String
        get() = prefs.getString(KEY_PROTOCOL, DEFAULT_PROTOCOL) ?: DEFAULT_PROTOCOL
        set(value) = prefs.edit().putString(KEY_PROTOCOL, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, URL_YOUTUBE_RTMPS) ?: URL_YOUTUBE_RTMPS
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var streamKey: String
        get() = prefs.getString(KEY_STREAM_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STREAM_KEY, value).apply()

    var srtPort: Int
        get() = prefs.getInt(KEY_SRT_PORT, DEFAULT_SRT_PORT)
        set(value) = prefs.edit().putInt(KEY_SRT_PORT, value).apply()

    var srtLatency: Int
        get() = prefs.getInt(KEY_SRT_LATENCY, DEFAULT_SRT_LATENCY)
        set(value) = prefs.edit().putInt(KEY_SRT_LATENCY, value).apply()

    var srtStreamId: String
        get() = prefs.getString(KEY_SRT_STREAM_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SRT_STREAM_ID, value).apply()

    var srtPassphrase: String
        get() = prefs.getString(KEY_SRT_PASSPHRASE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SRT_PASSPHRASE, value).apply()

    var resolution: String
        get() = prefs.getString(KEY_RESOLUTION, DEFAULT_RESOLUTION) ?: DEFAULT_RESOLUTION
        set(value) = prefs.edit().putString(KEY_RESOLUTION, value).apply()

    var isShortsLive: Boolean
        get() = prefs.getBoolean(KEY_IS_SHORTS, DEFAULT_IS_SHORTS)
        set(value) = prefs.edit().putBoolean(KEY_IS_SHORTS, value).apply()

    var fps: Int
        get() = prefs.getInt(KEY_FPS, DEFAULT_FPS)
        set(value) = prefs.edit().putInt(KEY_FPS, value).apply()

    var bitrate: Int
        get() = prefs.getInt(KEY_BITRATE, DEFAULT_BITRATE)
        set(value) = prefs.edit().putInt(KEY_BITRATE, value).apply()

    var isAudioMicEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_MIC, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_MIC, value).apply()

    var isAudioSysEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_SYS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_SYS, value).apply()

    var isFacecamEnabled: Boolean
        get() = prefs.getBoolean(KEY_FACECAM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FACECAM_ENABLED, value).apply()

    var isYouTubeConnected: Boolean
        get() = prefs.getBoolean(KEY_YT_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_YT_CONNECTED, value).apply()

    var youTubeEmail: String
        get() = prefs.getString(KEY_YT_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_EMAIL, value).apply()

    var youTubeChannelName: String
        get() = prefs.getString(KEY_YT_CHANNEL_NAME, "My YouTube Channel") ?: "My YouTube Channel"
        set(value) = prefs.edit().putString(KEY_YT_CHANNEL_NAME, value).apply()

    var youTubeChannelHandle: String
        get() = prefs.getString(KEY_YT_CHANNEL_HANDLE, "@livecreator") ?: "@livecreator"
        set(value) = prefs.edit().putString(KEY_YT_CHANNEL_HANDLE, value).apply()

    var youTubeChannelId: String
        get() = prefs.getString(KEY_YT_CHANNEL_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_CHANNEL_ID, value).apply()

    var broadcastTitle: String
        get() = prefs.getString(KEY_YT_BROADCAST_TITLE, "My YouTube Live Stream") ?: "My YouTube Live Stream"
        set(value) = prefs.edit().putString(KEY_YT_BROADCAST_TITLE, value).apply()

    var broadcastDesc: String
        get() = prefs.getString(KEY_YT_BROADCAST_DESC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_BROADCAST_DESC, value).apply()

    var broadcastPrivacy: String
        get() = prefs.getString(KEY_YT_BROADCAST_PRIVACY, "public") ?: "public"
        set(value) = prefs.edit().putString(KEY_YT_BROADCAST_PRIVACY, value).apply()

    var broadcastCategory: String
        get() = prefs.getString(KEY_YT_BROADCAST_CATEGORY, "Gaming") ?: "Gaming"
        set(value) = prefs.edit().putString(KEY_YT_BROADCAST_CATEGORY, value).apply()

    var broadcastId: String
        get() = prefs.getString(KEY_YT_BROADCAST_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_BROADCAST_ID, value).apply()

    var isBroadcastActive: Boolean
        get() = prefs.getBoolean(KEY_YT_BROADCAST_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_YT_BROADCAST_ACTIVE, value).apply()

    /*
     * Selects a platform preset and automatically adjusts the server ingest URL and protocol.
     */
    fun selectPreset(selectedPreset: String) {
        preset = selectedPreset
        when (selectedPreset) {
            PRESET_YOUTUBE_RTMPS -> {
                protocol = PROTOCOL_RTMPS
                serverUrl = URL_YOUTUBE_RTMPS
            }
            PRESET_YOUTUBE_RTMP -> {
                protocol = PROTOCOL_RTMP
                serverUrl = URL_YOUTUBE_RTMP
            }
            PRESET_YOUTUBE_SRT -> {
                protocol = PROTOCOL_SRT
                serverUrl = URL_YOUTUBE_SRT
            }
            PRESET_CUSTOM -> {
                // Keep current serverUrl or allow editing
            }
        }
    }

    /*
     * Calculates the exact stream resolution:
     * Standard 16:9 landscape (1920x1080, 1280x720, 854x480)
     * or 9:16 vertical Shorts Live (1080x1920, 720x1280, 480x854).
     */
    fun getStreamResolution(): Pair<Int, Int> {
        val (w, h) = when (resolution) {
            RESOLUTION_1080P -> 1920 to 1080
            RESOLUTION_720P -> 1280 to 720
            RESOLUTION_480P -> 854 to 480
            else -> 1920 to 1080
        }
        return if (isShortsLive) {
            Pair(h, w)
        } else {
            Pair(w, h)
        }
    }

    /*
     * Builds the complete ingest endpoint URL.
     * YouTube RTMP ingest is rtmp://a.rtmp.youtube.com/live2 or rtmps://a.rtmps.youtube.com/live2
     * When streamKey is provided, it is appended as /stream_key.
     */
    fun getFullStreamEndpoint(): String {
        var cleanUrl = serverUrl.trim().trimEnd('/')
        var key = streamKey.trim()

        // Clean up common user input mistakes (e.g. user pasted full rtmp url into stream key, or key has leading slash)
        if (key.startsWith("rtmp://", ignoreCase = true) || key.startsWith("rtmps://", ignoreCase = true)) {
            val lastSlash = key.lastIndexOf('/')
            if (lastSlash != -1 && lastSlash < key.length - 1) {
                cleanUrl = key.substring(0, lastSlash).trimEnd('/')
                key = key.substring(lastSlash + 1).trim()
            }
        } else if (key.startsWith("/")) {
            key = key.trimStart('/')
        }

        // If serverUrl doesn't have a protocol prefix, default according to current protocol
        if (!cleanUrl.contains("://")) {
            val scheme = when (protocol) {
                PROTOCOL_RTMPS -> "rtmps://"
                PROTOCOL_SRT -> "srt://"
                else -> "rtmp://"
            }
            cleanUrl = "$scheme$cleanUrl"
        }

        // For YouTube RTMP/RTMPS, if the user only entered host (e.g. rtmp://a.rtmp.youtube.com), ensure /live2 app name is present
        if ((protocol == PROTOCOL_RTMP || protocol == PROTOCOL_RTMPS) &&
            cleanUrl.contains("youtube.com", ignoreCase = true) &&
            !cleanUrl.contains("/live2")
        ) {
            cleanUrl = "$cleanUrl/live2"
        }

        return if (protocol == PROTOCOL_SRT) {
            var url = cleanUrl
            val params = mutableListOf<String>()
            val sid = if (key.isNotEmpty()) key else srtStreamId.trim()
            if (sid.isNotEmpty()) {
                params.add("streamid=$sid")
            }
            if (srtLatency > 0) {
                params.add("latency=$srtLatency")
            }
            if (params.isNotEmpty()) {
                url += (if (url.contains("?")) "&" else "?") + params.joinToString("&")
            }
            url
        } else {
            if (key.isNotEmpty()) {
                "$cleanUrl/$key"
            } else {
                cleanUrl
            }
        }
    }
}

