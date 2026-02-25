package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.albion.marketassistant.data.ColorDetectionResult

class ColorDetector {
    
    fun detectColor(
        bitmap: Bitmap,
        region: Rect,
        targetHexColor: String,
        toleranceRGB: Int = 30
    ): ColorDetectionResult {
        try {
            val targetColor = Color.parseColor(targetHexColor)
            val targetR = Color.red(targetColor)
            val targetG = Color.green(targetColor)
            val targetB = Color.blue(targetColor)

            var matchCount = 0
            var totalPixels = 0

            val safeLeft = Math.max(0, region.left)
            val safeTop = Math.max(0, region.top)
            val safeRight = Math.min(bitmap.width, region.right)
            val safeBottom = Math.min(bitmap.height, region.bottom)

            for (y in safeTop until safeBottom step 5) {
                for (x in safeLeft until safeRight step 5) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    if (Math.abs(r - targetR) <= toleranceRGB &&
                        Math.abs(g - targetG) <= toleranceRGB &&
                        Math.abs(b - targetB) <= toleranceRGB) {
                        matchCount++
                    }
                    totalPixels++
                }
            }

            if (totalPixels == 0) return ColorDetectionResult(
                hexColor = "",
                matchConfidence = 0f,
                isMatch = false
            )

            val confidence = matchCount.toFloat() / totalPixels.toFloat()
            val isMatch = confidence > 0.15f

            return ColorDetectionResult(
                hexColor = if (isMatch) targetHexColor else "",
                matchConfidence = confidence,
                isMatch = isMatch
            )
        } catch (e: Exception) {
            return ColorDetectionResult(
                hexColor = "",
                matchConfidence = 0f,
                isMatch = false
            )
        }
    }
}
