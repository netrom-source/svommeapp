package com.example.svommeapp.detector

import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * Very small and naive motion detector that compares luminance values between
 * frames inside a region of interest (ROI). It is not intended to be fast or
 * production ready but demonstrates how a detector could be structured.
 */
class MotionDetector(
    private val roi: Rect,
    private val sensitivity: Float = 0.2f,
    private val onMotion: () -> Unit
) : ImageAnalysis.Analyzer {

    private var lastFrame: ByteArray? = null

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        if (lastFrame != null && lastFrame!!.size >= data.size) {
            var diffSum = 0
            var count = 0
            val stride = image.width
            for (y in roi.top until roi.bottom) {
                val offset = y * stride
                for (x in roi.left until roi.right) {
                    val idx = offset + x
                    diffSum += abs(data[idx] - lastFrame!![idx])
                    count++
                }
            }
            val avg = diffSum.toFloat() / count
            if (avg / 255f > sensitivity) {
                onMotion()
            }
        }
        if (lastFrame == null || lastFrame!!.size != data.size) {
            lastFrame = ByteArray(data.size)
        }
        System.arraycopy(data, 0, lastFrame!!, 0, data.size)
        image.close()
    }
}
