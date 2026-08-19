package com.spyboy.camxploit

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class StreamStatus {
    object Idle : StreamStatus()
    object Connecting : StreamStatus()
    object Buffering : StreamStatus()
    object Live : StreamStatus()
    object Snapshot : StreamStatus()
    object Web : StreamStatus()
    object Unauthorized : StreamStatus()
    data class Error(val message: String) : StreamStatus()
}

@UnstableApi
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val _status = MutableStateFlow<StreamStatus>(StreamStatus.Idle)
    val status: StateFlow<StreamStatus> = _status

    private val _mjpegBitmap = MutableStateFlow<Bitmap?>(null)
    val mjpegBitmap: StateFlow<Bitmap?> = _mjpegBitmap

    private val _streamInfo = MutableStateFlow<String>("Resolution: — | Codec: —")
    val streamInfo: StateFlow<String> = _streamInfo

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration

    private var exoPlayer: ExoPlayer? = null
    private var mjpegJob: Job? = null
    private var recordingJob: Job? = null
    
    private val recorder = StreamRecorder(application)
    private var currentSource: StreamSource? = null

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> _status.value = StreamStatus.Buffering
                            Player.STATE_READY -> {
                                _status.value = StreamStatus.Live
                                val format = videoFormat
                                if (format != null) {
                                    _streamInfo.value = "Resolution: ${format.width}x${format.height} | Codec: ${format.codecs ?: "unknown"}"
                                }
                            }
                            Player.STATE_ENDED -> _status.value = StreamStatus.Idle
                            Player.STATE_IDLE -> _status.value = StreamStatus.Idle
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (error.message?.contains("401") == true) {
                            _status.value = StreamStatus.Unauthorized
                        } else {
                            _status.value = StreamStatus.Error(error.message ?: "Playback error")
                        }
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun startStream(source: StreamSource) {
        currentSource = source
        stopStream()
        _status.value = StreamStatus.Connecting

        when (source.protocol.lowercase()) {
            "rtsp", "rtmp" -> startRtspStream(source)
            "mjpeg" -> startMjpegStream(source.getAuthenticatedUrl())
            "snapshot" -> {
                _status.value = StreamStatus.Snapshot
                _streamInfo.value = "Mode: Periodic Snapshot"
            }
            "onvif" -> {
                val url = source.getAuthenticatedUrl()
                if (url.startsWith("rtsp://")) {
                    startRtspStream(source)
                } else {
                    startMjpegStream(url)
                }
            }
            else -> {
                val lower = source.url.lowercase()
                val cleanUrl = lower.substringBefore("?")
                val isSnapshot = cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || 
                                cleanUrl.endsWith(".png") || lower.contains("snapshot") ||
                                lower.contains("current") || lower.contains("still") ||
                                lower.contains("snap.jpg")

                if (isSnapshot) {
                    _status.value = StreamStatus.Snapshot
                    _streamInfo.value = "Mode: Periodic Snapshot"
                } else if (lower.startsWith("http") && (lower.contains("webcam") || lower.contains("camera") || lower.contains("view"))) {
                    // Likely a web page fallback
                    _status.value = StreamStatus.Web
                    _streamInfo.value = "Mode: Web Player Fallback"
                } else if (source.url.startsWith("rtsp://") || source.url.startsWith("http://")) {
                    startRtspStream(source)
                } else {
                    _status.value = StreamStatus.Error("Unsupported protocol: ${source.protocol}")
                }
            }
        }
    }

    private fun startRtspStream(source: StreamSource) {
        val player = getPlayer()
        val mediaItem = MediaItem.fromUri(source.getAuthenticatedUrl())
        
        // RTSP-specific factory for robustness (handles handshake, RTP over TCP/UDP)
        val rtspSource = RtspMediaSource.Factory()
            .setDebugLoggingEnabled(true)
            .setForceUseRtpTcp(true) // Common for camera stability over NAT/Firewalls
            .createMediaSource(mediaItem)
        
        player.setMediaSource(rtspSource)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Marks an MJPEG source as ready for the UI-owned continuous renderer.
     * The renderer emits frames through [onMjpegFrame], so this method deliberately
     * does not start a second HTTP connection.
     */
    fun prepareMjpegPlayback(source: StreamSource) {
        currentSource = source
        stopStream()
        _status.value = StreamStatus.Connecting
        _streamInfo.value = "Mode: Continuous MJPEG"
    }

    /** Receives frames from the single UI-owned MJPEG connection. */
    fun onMjpegFrame(bitmap: Bitmap) {
        _mjpegBitmap.value = bitmap
        _status.value = StreamStatus.Live
        _streamInfo.value = "Resolution: ${bitmap.width}x${bitmap.height} | MJPEG"

        if (_isRecording.value) {
            recorder.feedMjpegFrame(bitmap)
        }
    }

    private fun startMjpegStream(url: String) {
        mjpegJob = viewModelScope.launch {
            MjpegFrameGrabber(url).stream(
                onFrame = ::onMjpegFrame,
                onError = { error ->
                    _status.value = StreamStatus.Error(error)
                }
            )
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val source = currentSource ?: return
        val ip = source.url.substringAfter("//").substringBefore("/")
        
        when (source.protocol.lowercase()) {
            "mjpeg" -> {
                val bitmap = _mjpegBitmap.value ?: return
                val path = recorder.startMjpegRecording(ip, bitmap.width, bitmap.height)
                if (path != null) {
                    _isRecording.value = true
                    startRecordingTimer()
                }
            }
            "rtsp", "rtmp", "onvif" -> {
                val path = recorder.startRtspRecording(source.getAuthenticatedUrl(), ip)
                if (path != null) {
                    _isRecording.value = true
                    startRecordingTimer()
                }
            }
            else -> {}
        }
    }

    private fun stopRecording() {
        if (!_isRecording.value) return
        
        _isRecording.value = false
        recordingJob?.cancel()
        
        val source = currentSource
        if (source?.protocol?.lowercase() == "mjpeg") {
            val path = recorder.stopMjpegRecording()
            if (path != null) {
                // Signal completion to UI or save to database
            }
        }
    }

    private fun startRecordingTimer() {
        _recordingDuration.value = 0L
        recordingJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _recordingDuration.value += 1
            }
        }
    }

    fun probeWithCredentials(ip: String, brand: CameraBrand, user: String, pass: String) {
        viewModelScope.launch {
            _status.value = StreamStatus.Connecting
            val prober = RtspUrlProber()
            val urls = brand.rtspUrls.map { it.replace("{ip}", ip) }
            
            var foundUrl: String? = null
            withContext(Dispatchers.IO) {
                for (baseUrl in urls) {
                    val authUrl = if (baseUrl.startsWith("rtsp://")) {
                        baseUrl.replace("rtsp://", "rtsp://$user:$pass@")
                    } else baseUrl
                    
                    if (prober.isRtspEndpointValid(authUrl)) {
                        foundUrl = baseUrl
                        break
                    }
                }
            }
            
            if (foundUrl != null) {
                startStream(StreamSource(url = foundUrl!!, protocol = "rtsp", username = user, password = pass))
            } else {
                _status.value = StreamStatus.Error("Failed to authenticate or invalid path")
            }
        }
    }

    fun stopStream() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        mjpegJob?.cancel()
        mjpegJob = null
        _mjpegBitmap.value = null
        _status.value = StreamStatus.Idle
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
        mjpegJob?.cancel()
        mjpegJob = null
    }
}
