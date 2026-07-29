package com.spyboy.camxploit

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
class StreamRecorder(private val context: Context) {

    private var mediaMuxer: MediaMuxer? = null
    private var videoEncoder: MediaCodec? = null
    private var videoTrackIndex = -1
    private var isRecording = false
    private var startTimeUs: Long = 0
    private var outputFilePath: String? = null

    private var inputSurface: Surface? = null

    fun startMjpegRecording(targetIp: String, width: Int, height: Int): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Record_${targetIp.replace(".", "_")}_$timestamp.mp4"
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val file = File(moviesDir, filename)
        outputFilePath = file.absolutePath

        return try {
            mediaMuxer = MediaMuxer(outputFilePath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = videoEncoder?.createInputSurface()
            videoEncoder?.start()

            isRecording = true
            startTimeUs = System.nanoTime() / 1000
            outputFilePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Feed a frame to the MJPEG encoder.
     */
    fun feedMjpegFrame(bitmap: Bitmap) {
        if (!isRecording || (videoEncoder == null) || (mediaMuxer == null)) return

        try {
            val canvas = inputSurface?.lockCanvas(null)
            canvas?.drawBitmap(bitmap, 0f, 0f, null)
            inputSurface?.unlockCanvasAndPost(canvas)

            drainEncoder(endOfStream = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
            try {
                videoEncoder?.signalEndOfInputStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val muxer = mediaMuxer ?: return
        val encoder = videoEncoder ?: return

        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                videoTrackIndex = muxer.addTrack(encoder.outputFormat)
                muxer.start()
            } else if (outputBufferIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    
                    if (startTimeUs == 0L) startTimeUs = bufferInfo.presentationTimeUs
                    bufferInfo.presentationTimeUs -= startTimeUs
                    
                    muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                }

                encoder.releaseOutputBuffer(outputBufferIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }

    fun stopMjpegRecording(): String? {
        if (!isRecording) return null
        isRecording = false
        try {
            drainEncoder(true)
            videoEncoder?.stop()
            videoEncoder?.release()
            mediaMuxer?.stop()
            mediaMuxer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            videoEncoder = null
            mediaMuxer = null
            inputSurface = null
        }
        return outputFilePath
    }

    /**
     * RTSP recording using Media3 Transformer.
     * Note: Media3 Transformer in 1.5.1 is primarily for offline files.
     * Live stream recording with Transformer requires custom MediaSource handling.
     * For RTSP, a more direct approach is often needed (e.g. muxing raw H264 packets).
     * However, the request specifically asked for Transformer API if possible.
     */
    fun startRtspRecording(url: String, targetIp: String): String? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Record_RTSP_${targetIp.replace(".", "_")}_$timestamp.mp4"
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val file = File(moviesDir, filename)
        outputFilePath = file.absolutePath

        return try {
            val transformer = Transformer.Builder(context).build()
            val mediaItem = MediaItem.fromUri(url)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
            
            // Transformer 1.5.1 starts export. For RTSP it will continue until cancelled.
            transformer.start(editedMediaItem, outputFilePath!!)
            
            isRecording = true
            outputFilePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
