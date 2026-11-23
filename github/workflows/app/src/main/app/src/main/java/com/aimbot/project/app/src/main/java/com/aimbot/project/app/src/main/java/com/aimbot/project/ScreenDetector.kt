package com.aimbot.project

import android.media.Image
import java.nio.ByteBuffer

data class Detection(val x: Float, val y: Float, val sizePx: Int)

object ScreenDetector {

    /**
     * Scans an RGBA frame for pixels matching any enemy color (with tolerance).
     * Returns the centroid + blob height, or null when nothing found.
     * Samples every 2nd pixel in x and y for speed.
     */
    fun detect(
        image: Image,
        enemyColors: Array<IntArray>,
        tolerance: Int,
        minPixels: Int,
        minBlobHeightPx: Int
    ): Detection? {
        val plane = image.planes[0]
        val buf: ByteBuffer = plane.buffer

        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE

        val rowStride = plane.rowStride
        val rowLen = image.width * plane.pixelStride

        var y = 0
        while (y < image.height) {
            val rowStart = y * rowStride
            val rowEnd = rowStart + rowLen
            var idx = rowStart
            var x = 0
            while (idx < rowEnd) {
                val r = buf.get(idx).toInt() and 0xFF
                val g = buf.get(idx + 1).toInt() and 0xFF
                val b = buf.get(idx + 2).toInt() and 0xFF
                for (c in enemyColors) {
                    if (kotlin.math.abs(r - c[0]) <= tolerance &&
                        kotlin.math.abs(g - c[1]) <= tolerance &&
                        kotlin.math.abs(b - c[2]) <= tolerance
                    ) {
                        sumX += x
                        sumY += y
                        count++
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        break
                    }
                }
                idx += plane.pixelStride * 2
                x += 2
            }
            y += 2
        }

        if (count < minPixels) return null
        val blobH = maxY - minY
        if (blobH < minBlobHeightPx) return null
        return Detection((sumX / count).toFloat(), (sumY / count).toFloat(), blobH)
    }
}
