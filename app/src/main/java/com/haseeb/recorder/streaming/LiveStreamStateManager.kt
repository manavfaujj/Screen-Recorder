package com.haseeb.recorder.streaming

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/*
 * Thread-safe state tracker for active live stream session and listeners.
 */
object LiveStreamStateManager {

    interface Listener {
        fun onStreamStateChanged(isStreaming: Boolean, isConnecting: Boolean)
        fun onStreamStatsUpdated(bitrateBps: Long, durationMs: Long)
        fun onStreamError(error: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<Listener>()

    @Volatile var isStreaming: Boolean = false
        private set

    @Volatile var isConnecting: Boolean = false
        private set

    @Volatile var currentBitrateBps: Long = 0L
        private set

    @Volatile var streamStartTimeMs: Long = 0L
        private set

    @Volatile var lastError: String? = null
        private set

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onStreamStateChanged(isStreaming, isConnecting)
            if (isStreaming) {
                listener.onStreamStatsUpdated(currentBitrateBps, getDurationMs())
            }
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun onConnecting() {
        isConnecting = true
        isStreaming = false
        lastError = null
        notifyStateChanged()
    }

    fun onStreamStarted() {
        isConnecting = false
        isStreaming = true
        streamStartTimeMs = SystemClock.elapsedRealtime()
        lastError = null
        notifyStateChanged()
    }

    fun onStreamStopped(error: String? = null) {
        isConnecting = false
        isStreaming = false
        currentBitrateBps = 0L
        streamStartTimeMs = 0L
        lastError = error
        notifyStateChanged()
        if (error != null) {
            notifyError(error)
        }
    }

    fun onBitrateUpdated(bitrateBps: Long) {
        currentBitrateBps = bitrateBps
        val duration = getDurationMs()
        mainHandler.post {
            listeners.forEach { it.onStreamStatsUpdated(bitrateBps, duration) }
        }
    }

    fun getDurationMs(): Long {
        return if (isStreaming && streamStartTimeMs > 0L) {
            SystemClock.elapsedRealtime() - streamStartTimeMs
        } else {
            0L
        }
    }

    private fun notifyStateChanged() {
        mainHandler.post {
            listeners.forEach { it.onStreamStateChanged(isStreaming, isConnecting) }
        }
    }

    private fun notifyError(error: String) {
        mainHandler.post {
            listeners.forEach { it.onStreamError(error) }
        }
    }
}
