package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Color
import com.albion.marketassistant.data.ColorDetectionResult
import kotlin.math.abs
import kotlin.math.sqrt

class ColorDetector {
    
    companion object {
        private const val ROW_BACKGROUND_NORMAL = 0x1A1A1A
        private const val ROW_BACKGROUND_HIGHLIGHT = 0x2A2A2A
        private const val PRICE_TEXT_COLOR = 0xFFFFFF
        private const val ITEM_NAME_COLOR = 0xEEEEEE
        
        private const val COLOR_TOLERANCE = 30
        private const val MIN_VALID_PIXELS_PERCENT = 0.6f
    }
    
    fun isValidItemRegion(
        bitmap: Bitmap,
        regionX: Int,
        regionY: Int,
        regionWidth: Int,
        regionHeight: Int
    ): ColorDetectionResult {
        try {
            var validPixels = 0
            var totalChecked = 0
            var dominantColor = 0
            var colorCounts = mutableMapOf<Int, Int>()
            
            val stepX = (regionWidth / 10).coerceAtLeast(1)
            val stepY = (regionHeight / 5).coerceAtLeast(1)
            
            for (y in regionY until (regionY + regionHeight) step stepY) {
                for (x in regionX until (regionX + regionWidth) step stepX) {
                    if (x < bitmap.width && y < bitmap.height) {
                        val pixelColor = bitmap.getPixel(x, y)
                        val colorKey = simplifyColor(pixelColor)
                        
                        colorCounts[colorKey] = (colorCounts[colorKey] ?: 0) + 1
                        totalChecked++
                        
                        if (isValidRowColor(pixelColor)) {
                            validPixels++
                        }
                    }
                }
            }
            
            dominantColor = colorCounts.maxByOrNull { it.value }?.key ?: 0
            
            val confidence = if (totalChecked > 0) {
                validPixels.toFloat() / totalChecked
            } else 0f
            
            val isValid = confidence >= MIN_VALID_PIXELS_PERCENT
            
            return ColorDetectionResult(
                isValid = isValid,
                detectedColor = String.format("#%06X", (0xFFFFFF and dominantColor)),
                confidence = confidence,
                regionX = regionX,
                regionY = regionY,
                regionWidth = regionWidth,
                regionHeight = regionHeight
            )
        } catch (e: Exception) {
            return ColorDetectionResult(
                isValid = false,
                detectedColor = "#000000",
                confidence = 0f,
                regionX = regionX,
                regionY = regionY,
                regionWidth = regionWidth,
                regionHeight = regionHeight
            )
        }
    }
    
    private fun isValidRowColor(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        
        val isDarkBackground = red < 60 && green < 60 && blue < 60
        
        val isHighlight = abs(red - 42) < COLOR_TOLERANCE &&
                         abs(green - 42) < COLOR_TOLERANCE &&
                         abs(blue - 42) < COLOR_TOLERANCE
        
        val isVeryDark = red < 20 && green < 20 && blue < 20
        
        return isDarkBackground || isHighlight || isVeryDark
    }
    
    private fun simplifyColor(color: Int): Int {
        val red = (Color.red(color) / 32) * 32
        val green = (Color.green(color) / 32) * 32
        val blue = (Color.blue(color) / 32) * 32
        return Color.rgb(red, green, blue)
    }
    
    fun hasBuyOrderIndicator(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        radius: Int = 10
    ): Boolean {
        try {
            var greenPixels = 0
            var totalPixels = 0
            
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val px = x + dx
                    val py = y + dy
                    
                    if (px >= 0 && px < bitmap.width && py >= 0 && py < bitmap.height) {
                        val color = bitmap.getPixel(px, py)
                        val red = Color.red(color)
                        val green = Color.green(color)
                        val blue = Color.blue(color)
                        
                        if (green > 150 && green > red * 1.5 && green > blue * 1.5) {
                            greenPixels++
                        }
                        totalPixels++
                    }
                }
            }
            
            return totalPixels > 0 && (greenPixels.toFloat() / totalPixels) > 0.2f
        } catch (e: Exception) {
            return false
        }
    }
    
    fun detectPriceTextColor(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): ColorDetectionResult {
        var lightPixels = 0
        var totalPixels = 0
        
        for (y in top until bottom) {
            for (x in left until right) {
                if (x < bitmap.width && y < bitmap.height) {
                    val color = bitmap.getPixel(x, y)
                    val brightness = getBrightness(color)
                    
                    if (brightness > 150) {
                        lightPixels++
                    }
                    totalPixels++
                }
            }
        }
        
        val confidence = if (totalPixels > 0) {
            lightPixels.toFloat() / totalPixels
        } else 0f
        
        return ColorDetectionResult(
            isValid = confidence > 0.1f,
            detectedColor = "#FFFFFF",
            confidence = confidence,
            regionX = left,
            regionY = top,
            regionWidth = right - left,
            regionHeight = bottom - top
        )
    }
    
    private fun getBrightness(color: Int): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return (red + green + blue) / 3
    }
    
    fun colorDistance(color1: Int, color2: Int): Double {
        val r1 = Color.red(color1).toDouble()
        val g1 = Color.green(color1).toDouble()
        val b1 = Color.blue(color1).toDouble()
        
        val r2 = Color.red(color2).toDouble()
        val g2 = Color.green(color2).toDouble()
        val b2 = Color.blue(color2).toDouble()
        
        return sqrt((r1 - r2) * (r1 - r2) + 
                   (g1 - g2) * (g1 - g2) + 
                   (b1 - b2) * (b1 - b2))
    }
    
    fun hasErrorMessage(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): Boolean {
        var redPixels = 0
        var totalPixels = 0
        
        val stepX = (width / 5).coerceAtLeast(1)
        val stepY = (height / 3).coerceAtLeast(1)
        
        for (dy in 0 until height step stepY) {
            for (dx in 0 until width step stepX) {
                val px = x + dx
                val py = y + dy
                
                if (px < bitmap.width && py < bitmap.height) {
                    val color = bitmap.getPixel(px, py)
                    val red = Color.red(color)
                    val green = Color.green(color)
                    val blue = Color.blue(color)
                    
                    if (red > 180 && red > green * 2 && red > blue * 2) {
                        redPixels++
                    }
                    totalPixels++
                }
            }
        }
        
        return totalPixels > 0 && (redPixels.toFloat() / totalPixels) > 0.1f
    }
    
    fun analyzeMarketInterface(bitmap: Bitmap): MarketInterfaceAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        
        val backgroundCheck = isValidItemRegion(bitmap, 0, 0, width / 4, height / 4)
        
        val hasValidBackground = backgroundCheck.isValid
        
        return MarketInterfaceAnalysis(
            isMarketInterface = hasValidBackground,
            backgroundConfidence = backgroundCheck.confidence,
            detectedBackgroundColor = backgroundCheck.detectedColor
        )
    }
    
    data class MarketInterfaceAnalysis(
        val isMarketInterface: Boolean,
        val backgroundConfidence: Float,
        val detectedBackgroundColor: String
    )
}
