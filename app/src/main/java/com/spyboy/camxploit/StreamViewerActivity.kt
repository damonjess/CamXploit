package com.spyboy.camxploit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
class StreamViewerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var streamUrl: String = ""
    private var targetIp: String = ""

    companion object {
        private const val EXTRA_URL = "stream_url"
        private const val EXTRA_IP  = "target_ip"

        fun launch(context: Context, url: String, ip: String) {
            val intent = Intent(context, StreamViewerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_IP,  ip)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream_viewer)

        // Keep screen on while viewing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        streamUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        targetIp  = intent.getStringExtra(EXTRA_IP)  ?: ""

        playerView = findViewById(R.id.playerView)

        findViewById<android.widget.TextView>(R.id.tvStreamUrl).text = "URL: $streamUrl"
        findViewById<android.widget.TextView>(R.id.tvStreamTitle).text = targetIp

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnRetry).setOnClickListener {
            releasePlayer()
            initPlayer()
        }

        findViewById<android.widget.Button>(R.id.btnSnapshot).setOnClickListener {
            takeSnapshot()
        }

        initPlayer()
    }

    private fun initPlayer() {
        val statusText = findViewById<android.widget.TextView>(R.id.tvStreamStatus)
        val infoText   = findViewById<android.widget.TextView>(R.id.tvStreamInfo)

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo

            val mediaItem = MediaItem.fromUri(streamUrl)

            // Use RTSP source for rtsp:// URLs, default for http://
            if (streamUrl.startsWith("rtsp://")) {
                val rtspSource = RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true) // TCP more reliable than UDP on LAN
                    .createMediaSource(mediaItem)
                exo.setMediaSource(rtspSource)
            } else {
                exo.setMediaItem(mediaItem)
            }

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            statusText.text     = "● BUFFERING"
                            statusText.setTextColor(0xFFFFA500.toInt())
                        }
                        Player.STATE_READY -> {
                            statusText.text = "● LIVE"
                            statusText.setTextColor(0xFF00FF00.toInt())

                            // Show resolution and codec
                            val format = exo.videoFormat
                            if (format != null) {
                                infoText.text = "Resolution: ${format.width}x${format.height}" +
                                    " | Codec: ${format.codecs ?: "unknown"}"
                            }
                        }
                        Player.STATE_ENDED -> {
                            statusText.text = "● ENDED"
                            statusText.setTextColor(0xFF888888.toInt())
                        }
                        Player.STATE_IDLE -> {
                            statusText.text = "● IDLE"
                            statusText.setTextColor(0xFF888888.toInt())
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    statusText.text = "● ERROR"
                    statusText.setTextColor(0xFFFF0000.toInt())
                    Toast.makeText(
                        this@StreamViewerActivity,
                        "Stream error: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })

            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun takeSnapshot() {
        val statusText = findViewById<android.widget.TextView>(R.id.tvStreamStatus)

        // Must be in READY state to grab a frame
        if (player?.playbackState != Player.STATE_READY) {
            Toast.makeText(this, "Wait for stream to be ready", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Use PixelCopy for reliable frame capture from SurfaceView
            val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
                ?: run {
                    Toast.makeText(this, "Surface not available", Toast.LENGTH_SHORT).show()
                    return
                }

            val bitmap = Bitmap.createBitmap(
                surfaceView.width,
                surfaceView.height,
                Bitmap.Config.ARGB_8888
            )

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.view.PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { result ->
                        if (result == android.view.PixelCopy.SUCCESS) {
                            saveSnapshotToArchive(bitmap)
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "Frame capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
            } else {
                // Fallback for older Android
                val canvas = Canvas(bitmap)
                surfaceView.draw(canvas)
                saveSnapshotToArchive(bitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Snapshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSnapshotToArchive(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safIp     = targetIp.replace(".", "_")
            val filename  = "Vigil_Snap_${safIp}_$timestamp.jpg"

            // Save image to Pictures (Archive tab reads this)
            val picDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imgFile = File(picDir, filename)
            FileOutputStream(imgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Save metadata JSON alongside in Documents
            val meta = org.json.JSONObject().apply {
                put("type",      "snapshot")
                put("target_ip", targetIp)
                put("stream_url", streamUrl)
                put("timestamp", System.currentTimeMillis())
                put("filename",  filename)
                put("width",     bitmap.width)
                put("height",    bitmap.height)
            }
            val docDir   = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val metaFile = File(docDir, "Vigil_Snap_${safIp}_$timestamp.json")
            FileOutputStream(metaFile).use { it.write(meta.toString(2).toByteArray()) }

            runOnUiThread {
                Toast.makeText(
                    this,
                    "📷 Snapshot saved to Archive",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onPause()   { super.onPause();   player?.pause() }
    override fun onResume()  { super.onResume();  player?.play()  }
    override fun onDestroy() { super.onDestroy(); releasePlayer() }
}