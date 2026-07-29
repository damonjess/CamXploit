package com.spyboy.camxploit

import android.content.Context
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
class StreamViewerActivity : AppCompatActivity() {

    private val viewModel: StreamViewModel by viewModels()
    private lateinit var playerView: PlayerView
    private lateinit var ivMjpeg: android.widget.ImageView
    
    private var streamSource: StreamSource? = null
    private var targetIp: String = ""

    companion object {
        private const val EXTRA_SOURCE = "stream_source"
        private const val EXTRA_IP     = "target_ip"

        fun launch(context: Context, source: StreamSource, ip: String) {
            val intent = Intent(context, StreamViewerActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_IP,     ip)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream_viewer)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        streamSource = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SOURCE, StreamSource::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE)
        }
        targetIp = intent.getStringExtra(EXTRA_IP) ?: ""

        playerView = findViewById(R.id.playerView)
        ivMjpeg = findViewById(R.id.ivMjpeg)

        findViewById<android.widget.TextView>(R.id.tvStreamUrl).text = "URL: ${streamSource?.url ?: ""}"
        findViewById<android.widget.TextView>(R.id.tvStreamTitle).text = targetIp

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.btnRetry).setOnClickListener { streamSource?.let { startPlayback(it) } }
        findViewById<android.widget.Button>(R.id.btnSnapshot).setOnClickListener { takeSnapshot() }
        findViewById<android.widget.Button>(R.id.btnPip).setOnClickListener { enterPipMode() }
        
        val btnRecord = findViewById<android.widget.Button>(R.id.btnRecord)
        btnRecord.setOnClickListener { viewModel.toggleRecording() }

        observeViewModel()

        if (savedInstanceState == null) {
            streamSource?.let { startPlayback(it) }
        }
    }

    private fun startPlayback(source: StreamSource) {
        viewModel.startStream(source)
        updateRenderingViews()
    }

    private fun updateRenderingViews() {
        when (streamSource) {
            is StreamSource.Mjpeg -> {
                playerView.visibility = View.GONE
                ivMjpeg.visibility = View.VISIBLE
            }
            else -> {
                playerView.visibility = View.VISIBLE
                playerView.player = viewModel.getPlayer()
                ivMjpeg.visibility = View.GONE
            }
        }
    }

    private fun showCredentialDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val userField = android.widget.EditText(this).apply { hint = "Username" }
        val passField = android.widget.EditText(this).apply { 
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(userField)
        layout.addView(passField)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Authentication Required")
            .setMessage("Enter credentials for $targetIp")
            .setView(layout)
            .setPositiveButton("Connect") { _, _ ->
                val user = userField.text.toString()
                val pass = passField.text.toString()
                val brand = when {
                    targetIp.lowercase().contains("hik") -> CameraBrand.Hikvision
                    targetIp.lowercase().contains("dahua") -> CameraBrand.Dahua
                    else -> CameraBrand.Generic
                }
                viewModel.probeWithCredentials(targetIp, brand, user, pass)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        val statusText = findViewById<android.widget.TextView>(R.id.tvStreamStatus)
        val infoText   = findViewById<android.widget.TextView>(R.id.tvStreamInfo)
        val btnRecord  = findViewById<android.widget.Button>(R.id.btnRecord)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isRecording.collectLatest { isRecording ->
                        if (isRecording) {
                            btnRecord.text = "⏹ Stop"
                            btnRecord.setBackgroundColor(0xFF888888.toInt())
                        } else {
                            btnRecord.text = "⏺ Record"
                            btnRecord.setBackgroundColor(0xFFAA0000.toInt())
                        }
                    }
                }
                
                launch {
                    viewModel.recordingDuration.collectLatest { duration ->
                        if (viewModel.isRecording.value) {
                            val mins = duration / 60
                            val secs = duration % 60
                            statusText.text = "● RECORDING ${String.format("%02d:%02d", mins, secs)}"
                            statusText.setTextColor(0xFFFF0000.toInt())
                        }
                    }
                }

                launch {
                    viewModel.status.collectLatest { status ->
                        // Don't override recording status text
                        if (viewModel.isRecording.value && status is StreamStatus.Live) return@collectLatest
                        
                        when (status) {
                            is StreamStatus.Idle -> {
                                statusText.text = "● IDLE"
                                statusText.setTextColor(0xFF888888.toInt())
                            }
                            is StreamStatus.Connecting -> {
                                statusText.text = "● CONNECTING"
                                statusText.setTextColor(0xFFFFA500.toInt())
                            }
                            is StreamStatus.Buffering -> {
                                statusText.text = "● BUFFERING"
                                statusText.setTextColor(0xFFFFA500.toInt())
                            }
                            is StreamStatus.Live -> {
                                statusText.text = "● LIVE"
                                statusText.setTextColor(0xFF00FF00.toInt())
                                updateRenderingViews()
                            }
                            is StreamStatus.Unauthorized -> {
                                statusText.text = "● UNAUTHORIZED"
                                statusText.setTextColor(0xFFFF0000.toInt())
                                showCredentialDialog()
                            }
                            is StreamStatus.Error -> {
                                statusText.text = "● ERROR"
                                statusText.setTextColor(0xFFFF0000.toInt())
                                Toast.makeText(this@StreamViewerActivity, status.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.mjpegBitmap.collectLatest { bitmap ->
                        if (bitmap != null) {
                            ivMjpeg.setImageBitmap(bitmap)
                        }
                    }
                }

                launch {
                    viewModel.streamInfo.collectLatest { info ->
                        infoText.text = info
                    }
                }
            }
        }
    }

    private fun takeSnapshot() {
        if (ivMjpeg.visibility == View.VISIBLE) {
            val drawable = ivMjpeg.drawable as? android.graphics.drawable.BitmapDrawable
            if (drawable != null) {
                saveSnapshotToArchive(drawable.bitmap)
            } else {
                Toast.makeText(this, "No frame available", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val currentPlayer = viewModel.getPlayer()
        if (currentPlayer.playbackState != Player.STATE_READY) {
            Toast.makeText(this, "Wait for stream to be ready", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val surfaceView = playerView.videoSurfaceView as? SurfaceView
                ?: run {
                    Toast.makeText(this, "Surface not available", Toast.LENGTH_SHORT).show()
                    return
                }

            val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                PixelCopy.request(surfaceView, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        saveSnapshotToArchive(bitmap)
                    } else {
                        runOnUiThread { Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show() }
                    }
                }, Handler(Looper.getMainLooper()))
            } else {
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
                put("stream_url", streamSource?.url ?: "")
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

    private fun enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            @Suppress("DEPRECATION")
            enterPictureInPictureMode()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // Hide UI elements in PiP
            findViewById<View>(R.id.btnBack).parent.let { if (it is View) it.visibility = View.GONE }
            findViewById<View>(R.id.tvStreamUrl).parent.let { if (it is View) it.visibility = View.GONE }
            findViewById<View>(R.id.btnRetry).parent.let { if (it is View) it.visibility = View.GONE }
        } else {
            // Restore UI elements when back from PiP
            findViewById<View>(R.id.btnBack).parent.let { if (it is View) it.visibility = View.VISIBLE }
            findViewById<View>(R.id.tvStreamUrl).parent.let { if (it is View) it.visibility = View.VISIBLE }
            findViewById<View>(R.id.btnRetry).parent.let { if (it is View) it.visibility = View.VISIBLE }
        }
    }

    override fun onPause()   { super.onPause(); viewModel.getPlayer().playWhenReady = false }
    override fun onResume()  { super.onResume(); viewModel.getPlayer().playWhenReady = true }
    override fun onDestroy() { 
        super.onDestroy()
    }
}
