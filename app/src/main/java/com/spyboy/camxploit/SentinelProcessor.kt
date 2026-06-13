package com.spyboy.camxploit

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class SentinelResult(
    val label: String,
    val confidence: Float,
    val isThreat: Boolean  // true for person, vehicle, unknown
)

class SentinelProcessor(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val INPUT_SIZE = 320  // YOLOv8n input
    private val CONFIDENCE_THRESHOLD = 0.45f

    // Labels that count as "threats" worth alerting on
    private val threatLabels = setOf(
        "person", "car", "truck", "motorcycle", "bicycle",
        "bus", "boat", "airplane", "cat", "dog"
    )

    // COCO labels (80 classes)
    private val labels = listOf(
        "person","bicycle","car","motorcycle","airplane","bus","train","truck",
        "boat","traffic light","fire hydrant","stop sign","parking meter","bench",
        "bird","cat","dog","horse","sheep","cow","elephant","bear","zebra","giraffe",
        "backpack","umbrella","handbag","tie","suitcase","frisbee","skis","snowboard",
        "sports ball","kite","baseball bat","baseball glove","skateboard","surfboard",
        "tennis racket","bottle","wine glass","cup","fork","knife","spoon","bowl",
        "banana","apple","sandwich","orange","broccoli","carrot","hot dog","pizza",
        "donut","cake","chair","couch","potted plant","bed","dining table","toilet",
        "tv","laptop","mouse","remote","keyboard","cell phone","microwave","oven",
        "toaster","sink","refrigerator","book","clock","vase","scissors","teddy bear",
        "hair drier","toothbrush"
    )

    fun load(): Boolean {
        return try {
            val modelFile = loadModelFile()
            interpreter = Interpreter(modelFile, Interpreter.Options().apply {
                numThreads = 2
            })
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFd = context.assets.openFd("yolov8n_float32.tflite")
        return FileInputStream(assetFd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    fun detect(bitmap: Bitmap): List<SentinelResult> {
        val interp = interpreter ?: return emptyList()

        // Resize and normalize
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixel shr 8  and 0xFF) / 255.0f))
            inputBuffer.putFloat(((pixel        and 0xFF) / 255.0f))
        }
        inputBuffer.rewind()

        // Output: [1, 84, 8400] for YOLOv8n
        val outputBuffer = Array(1) { Array(84) { FloatArray(8400) } }

        interp.run(inputBuffer, outputBuffer)

        // Parse detections
        val results = mutableListOf<SentinelResult>()
        val output = outputBuffer[0]

        for (i in 0 until 8400) {
            var maxConf = 0f
            var maxClass = 0
            for (c in 4 until 84) {
                if (output[c][i] > maxConf) {
                    maxConf = output[c][i]
                    maxClass = c - 4
                }
            }
            if (maxConf >= CONFIDENCE_THRESHOLD && maxClass < labels.size) {
                val label = labels[maxClass]
                results.add(SentinelResult(
                    label      = label,
                    confidence = maxConf,
                    isThreat   = label in threatLabels
                ))
            }
        }

        // Deduplicate — return highest confidence per label
        return results
            .groupBy { it.label }
            .map { (_, group) -> group.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }
            .take(5)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
