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
                        _status.value = StreamStatus.Error(error.message ?: "Playback error")
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

        when (source) {
            is StreamSource.Rtsp -> startRtspStream(source)
            is StreamSource.Mjpeg -> startMjpegStream(source.getAuthenticatedUrl())
            is StreamSource.Onvif -> {
                val url = source.getAuthenticatedUrl()
                if (url.startsWith("rtsp://")) {
                    startRtspStream(StreamSource.Rtsp(url, source.username, source.password))
                } else {
                    startMjpegStream(url)
                }
            }
        }
    }

    private fun startRtspStream(source: StreamSource.Rtsp) {
        val player = getPlayer()
        val mediaItem = MediaItem.fromUri(source.getAuthenticatedUrl())
        val rtspSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(mediaItem)
        
        player.setMediaSource(rtspSource)
        player.prepare()
        player.playWhenReady = true
    }

    private fun startMjpegStream(url: String) {
        mjpegJob = viewModelScope.launch {
            MjpegFrameGrabber(url).stream(
                onFrame = { bitmap ->
                    _mjpegBitmap.value = bitmap
                    _status.value = StreamStatus.Live
                    _streamInfo.value = "Resolution: ${bitmap.width}x${bitmap.height} | MJPEG"
                    
                    if (_isRecording.value) {
                        recorder.feedMjpegFrame(bitmap)
                    }
                },
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
        
        when (source) {
            is StreamSource.Mjpeg -> {
                val bitmap = _mjpegBitmap.value ?: return
                val path = recorder.startMjpegRecording(ip, bitmap.width, bitmap.height)
                if (path != null) {
                    _isRecording.value = true
                    startRecordingTimer()
                }
            }
            is StreamSource.Rtsp -> {
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
        if (source is StreamSource.Mjpeg) {
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
