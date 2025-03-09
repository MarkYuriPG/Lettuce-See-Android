package com.example.lettucesee

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.scale

class YoloModelInterpreter(context: Context) {
    private var interpreter: Interpreter
    private val inputSize = 640 // YOLOv8 default input size
    private val numClasses = 3  // Your model classes
    private val colors = mapOf(
        0 to android.graphics.Color.BLUE,    // normal_lettuce
        1 to android.graphics.Color.RED,     // disease_lettuce
        2 to android.graphics.Color.YELLOW   // weed
    )
    private val classNames = mapOf(
        0 to "normal_lettuce",
        1 to "disease_lettuce",
        2 to "weed"
    )

    init {
        val modelFile = FileUtil.loadMappedFile(context, "best_float32.tflite")
        interpreter = Interpreter(modelFile)
    }

    fun processImage(bitmap: Bitmap, confidenceThreshold: Float = 0.25f): List<Detection> {
        // Scale the bitmap to match input size
        val scaledBitmap = bitmap.scale(inputSize, inputSize)

        // Prepare input
        val inputBuffer = convertBitmapToByteBuffer(scaledBitmap)

        // Prepare output
        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputBuffer = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

        // Run inference
        interpreter.run(inputBuffer, outputBuffer)

        // Post-process the output
        val results = ArrayList<Detection>()

        // Get original image dimensions for scaling
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        val xScale = imageWidth / inputSize
        val yScale = imageHeight / inputSize

        // Process each detection
        for (i in 0 until outputShape[2]) {  // 8400 boxes
            val confidence = outputBuffer[0][4][i]
            if (confidence > confidenceThreshold) {
                // Get scaled box coordinates
                val x = outputBuffer[0][0][i] * xScale
                val y = outputBuffer[0][1][i] * yScale
                val w = outputBuffer[0][2][i] * xScale
                val h = outputBuffer[0][3][i] * yScale

                // Find class with highest probability
                var maxClass = -1
                var maxScore = 0f
                for (c in 0 until numClasses) {
                    val score = outputBuffer[0][5 + c][i]
                    if (score > maxScore) {
                        maxScore = score
                        maxClass = c
                    }
                }

                if (maxClass != -1) {
                    results.add(
                        Detection(
                            bbox = RectF(
                                x - w/2,
                                y - h/2,
                                x + w/2,
                                y + h/2
                            ),
                            confidence = confidence,
                            classIndex = maxClass,
                            className = classNames[maxClass] ?: "unknown",
                            color = colors[maxClass] ?: android.graphics.Color.GREEN
                        )
                    )
                }
            }
        }

        return results
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (i in 0 until inputSize * inputSize) {
            val pixel = intValues[i]
            // Normalize pixel values to [-1, 1]
            byteBuffer.putFloat(((pixel shr 16 and 0xFF) / 255.0f * 2 - 1.0f))
            byteBuffer.putFloat(((pixel shr 8 and 0xFF) / 255.0f * 2 - 1.0f))
            byteBuffer.putFloat(((pixel and 0xFF) / 255.0f * 2 - 1.0f))
        }

        return byteBuffer
    }

    data class Detection(
        val bbox: RectF,
        val confidence: Float,
        val classIndex: Int,
        val className: String,
        val color: Int
    )
}
