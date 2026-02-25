package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.albion.marketassistant.data.ColorDetectionResult

/**
 * Utility class for detecting colors on screen
 * Used for UI element verification and highlight detection
 */
class ColorDetector {

    /**
     * Detect color at specific coordinates
     */
    fun detectColorAt(bitmap: Bitmap, x: Int, y: Int): Int {
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) {
            return Color.TRANSPARENT
        }
        return bitmap.getPixel(x, y)
    }

    /**
     * Check if color at coordinates matches expected color within tolerance
     */
    fun isColorMatch(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        expectedColorHex: String,
        tolerance: Int = 30
    ): Boolean {
        val actualColor = detectColorAt(bitmap, x, y)
        val expectedColor = parseColorHex(expectedColorHex)
        return colorDistance(actualColor, expectedColor) <= tolerance
    }

    /**
     * Detect highlighted row by scanning for specific color
     */
    fun detectHighlightedRow(
        bitmap: Bitmap,
        region: Rect,
        highlightColorHex: String,
        tolerance: Int = 30
    ): List<Int> {
        val highlightedRows = mutableListOf<Int>()
        val highlightColor = parseColorHex(highlightColorHex)

        // Scan each row in the region
        for (y in region.top until region.bottom step 10) {
            var matchCount = 0
            var totalSamples = 0

            // Sample pixels across the row
            for (x in region.left until region.right step 20) {
                if (x < bitmap.width && y < bitmap.height) {
                    val pixelColor = bitmap.getPixel(x, y)
                    if (colorDistance(pixelColor, highlightColor) <= tolerance) {
                        matchCount++
                    }
                    totalSamples++
                }
            }

            // If more than 50% of samples match, consider it highlighted
            if (totalSamples > 0 && matchCount.toFloat() / totalSamples > 0.5f) {
                highlightedRows.add(y)
            }
        }

        return highlightedRows
    }

    /**
     * Find region with specific color
     */
    fun findColorRegion(
        bitmap: Bitmap,
        searchRegion: Rect,
        targetColorHex: String,
        tolerance: Int = 30,
        minRegionSize: Int = 20
    ): Rect? {
        val targetColor = parseColorHex(targetColorHex)
        var foundLeft = Int.MAX_VALUE
        var foundTop = Int.MAX_VALUE
        var foundRight = Int.MIN_VALUE
        var foundBottom = Int.MIN_VALUE

        for (y in maxOf(0, searchRegion.top) until minOf(bitmap.height, searchRegion.bottom)) {
            for (x in maxOf(0, searchRegion.left) until minOf(bitmap.width, searchRegion.right)) {
                val pixelColor = bitmap.getPixel(x, y)
                if (colorDistance(pixelColor, targetColor) <= tolerance) {
                    foundLeft = minOf(foundLeft, x)
                    foundTop = minOf(foundTop, y)
                    foundRight = maxOf(foundRight, x)
                    foundBottom = maxOf(foundBottom, y)
                }
            }
        }

        // Check if region is large enough
        if (foundRight - foundLeft >= minRegionSize && foundBottom - foundTop >= minRegionSize) {
            return Rect(foundLeft, foundTop, foundRight, foundBottom)
        }

        return null
    }

    /**
     * Detect if a UI element is visible (has contrast with background)
     */
    fun isUIElementVisible(
        bitmap: Bitmap,
        region: Rect,
        minContrast: Float = 0.2f
    ): Boolean {
        if (region.width() <= 0 || region.height() <= 0) return false

        var totalLuminance = 0f
        var sampleCount = 0

        for (y in maxOf(0, region.top) until minOf(bitmap.height, region.bottom) step 5) {
            for (x in maxOf(0, region.left) until minOf(bitmap.width, region.right) step 5) {
                val pixelColor = bitmap.getPixel(x, y)
                totalLuminance += calculateLuminance(pixelColor)
                sampleCount++
            }
        }

        if (sampleCount == 0) return false

        val avgLuminance = totalLuminance / sampleCount

        // Check for contrast variation
        var contrastSum = 0f
        for (y in maxOf(0, region.top) until minOf(bitmap.height, region.bottom) step 5) {
            for (x in maxOf(0, region.left) until minOf(bitmap.width, region.right) step 5) {
                val pixelColor = bitmap.getPixel(x, y)
                val luminance = calculateLuminance(pixelColor)
                contrastSum += kotlin.math.abs(luminance - avgLuminance)
            }
        }

        val avgContrast = contrastSum / sampleCount
        return avgContrast >= minContrast
    }

    /**
     * Get dominant color in a region
     */
    fun getDominantColor(bitmap: Bitmap, region: Rect): Int {
        val colorCounts = mutableMapOf<Int, Int>()

        for (y in maxOf(0, region.top) until minOf(bitmap.height, region.bottom) step 3) {
            for (x in maxOf(0, region.left) until minOf(bitmap.width, region.right) step 3) {
                val pixelColor = bitmap.getPixel(x, y)
                // Quantize color to reduce noise
                val quantized = quantizeColor(pixelColor, 32)
                colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
            }
        }

        return colorCounts.maxByOrNull { it.value }?.key ?: Color.TRANSPARENT
    }

    /**
     * Calculate color detection result with confidence
     */
    fun analyzeRegion(
        bitmap: Bitmap,
        region: Rect,
        targetColorHex: String,
        tolerance: Int = 30
    ): ColorDetectionResult {
        val targetColor = parseColorHex(targetColorHex)
        var matchCount = 0
        var totalSamples = 0

        for (y in maxOf(0, region.top) until minOf(bitmap.height, region.bottom) step 3) {
            for (x in maxOf(0, region.left) until minOf(bitmap.width, region.right) step 3) {
                val pixelColor = bitmap.getPixel(x, y)
                if (colorDistance(pixelColor, targetColor) <= tolerance) {
                    matchCount++
                }
                totalSamples++
            }
        }

        val confidence = if (totalSamples > 0) matchCount.toFloat() / totalSamples else 0f
        val isMatch = confidence > 0.5f

        return ColorDetectionResult(
            hexColor = targetColorHex,
            matchConfidence = confidence,
            isMatch = isMatch
        )
    }

    // Helper functions

    private fun parseColorHex(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            Color.TRANSPARENT
        }
    }

    private fun colorDistance(color1: Int, color2: Int): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        return kotlin.math.abs(r1 - r2) + kotlin.math.abs(g1 - g2) + kotlin.math.abs(b1 - b2)
    }

    private fun calculateLuminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun quantizeColor(color: Int, levels: Int): Int {
        val step = 256 / levels
        val r = (Color.red(color) / step) * step
        val g = (Color.green(color) / step) * step
        val b = (Color.blue(color) / step) * step
        return Color.rgb(r, g, b)
    }
}
