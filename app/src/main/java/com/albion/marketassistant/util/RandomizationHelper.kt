package com.albion.marketassistant.util

import com.albion.marketassistant.data.RandomizationSettings
import kotlin.random.Random

/**
 * Helper class for randomizing gestures and delays to avoid detection
 */
object RandomizationHelper {
    
    private var settings: RandomizationSettings = RandomizationSettings()
    
    /**
     * Initialize with custom settings
     */
    fun initialize(newSettings: RandomizationSettings) {
        settings = newSettings
    }
    
    /**
     * Get current settings
     */
    fun getSettings(): RandomizationSettings = settings
    
    /**
     * Generate a random delay between min and max
     */
    fun getRandomDelay(): Long {
        return Random.nextLong(settings.minRandomDelayMs, settings.maxRandomDelayMs + 1)
    }
    
    /**
     * Generate a random delay within a custom range
     */
    fun getRandomDelay(minMs: Long, maxMs: Long): Long {
        return Random.nextLong(minMs, maxMs + 1)
    }
    
    /**
     * Apply randomization to swipe distance
     */
    fun randomizeSwipeDistance(baseDistance: Float): Float {
        if (settings.randomSwipeDistancePercent <= 0f) return baseDistance
        
        val variation = baseDistance * settings.randomSwipeDistancePercent
        return baseDistance + Random.nextFloat() * variation * 2 - variation
    }
    
    /**
     * Randomize a gesture path by adding noise to coordinates
     */
    fun randomizePath(startX: Float, startY: Float, endX: Float, endY: Float): List<Pair<Float, Float>> {
        if (!settings.randomizeGesturePath) {
            return listOf(startX to startY, endX to endY)
        }
        
        val path = mutableListOf<Pair<Float, Float>>()
        path.add(startX to startY)
        
        // Add intermediate points with randomization
        val steps = 3
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val baseX = startX + (endX - startX) * t
            val baseY = startY + (endY - startY) * t
            
            val randomizedX = baseX + randomPixelOffset(settings.pathRandomizationPixels)
            val randomizedY = baseY + randomPixelOffset(settings.pathRandomizationPixels)
            
            path.add(randomizedX to randomizedY)
        }
        
        path.add(endX to endY)
        return path
    }
    
    /**
     * Randomize a single coordinate
     */
    fun randomizeCoordinate(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        if (!settings.randomizeGesturePath) {
            return x to y
        }
        
        val pixelOffset = settings.pathRandomizationPixels
        val xPercent = randomPixelOffset(pixelOffset) / screenWidth.toFloat()
        val yPercent = randomPixelOffset(pixelOffset) / screenHeight.toFloat()
        
        return (x + xPercent).coerceIn(0f, 1f) to (y + yPercent).coerceIn(0f, 1f)
    }
    
    /**
     * Generate a random pixel offset
     */
    private fun randomPixelOffset(maxPixels: Int): Float {
        if (maxPixels <= 0) return 0f
        return Random.nextInt(-maxPixels, maxPixels + 1).toFloat()
    }
    
    /**
     * Randomize tap duration
     */
    fun randomizeTapDuration(baseDuration: Long): Long {
        val variation = baseDuration * 0.1f
        return (baseDuration + Random.nextFloat() * variation * 2 - variation).toLong()
            .coerceAtLeast(50)
    }
    
    /**
     * Randomize swipe duration
     */
    fun randomizeSwipeDuration(baseDuration: Long): Long {
        val variation = baseDuration * 0.15f
        return (baseDuration + Random.nextFloat() * variation * 2 - variation).toLong()
            .coerceAtLeast(100)
    }
    
    /**
     * Add random jitter to a long press
     */
    fun randomizeLongPressDuration(baseDuration: Long): Long {
        val jitter = Random.nextLong(-50, 51)
        return (baseDuration + jitter).coerceAtLeast(300)
    }
    
    /**
     * Generate random loop delay with variation
     */
    fun randomizeLoopDelay(baseDelay: Long): Long {
        val variation = baseDelay * 0.2f
        return (baseDelay + Random.nextFloat() * variation * 2 - variation).toLong()
            .coerceAtLeast(100)
    }
    
    /**
     * Randomize scroll gesture
     */
    fun randomizeScroll(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): Quadruple<Float, Float, Float, Float> {
        val (rx1, ry1) = randomizeCoordinate(startX, startY, screenWidth, screenHeight)
        val (rx2, ry2) = randomizeCoordinate(endX, endY, screenWidth, screenHeight)
        
        return Quadruple(rx1, ry1, rx2, ry2)
    }
    
    /**
     * Get random bezier control points for natural gesture motion
     */
    fun getBezierControlPoints(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2
        
        val controlOffset = settings.pathRandomizationPixels / 500f
        
        val cp1x = midX + Random.nextFloat() * controlOffset * 2 - controlOffset
        val cp1y = midY + Random.nextFloat() * controlOffset * 2 - controlOffset
        val cp2x = midX + Random.nextFloat() * controlOffset * 2 - controlOffset
        val cp2y = midY + Random.nextFloat() * controlOffset * 2 - controlOffset
        
        return (cp1x to cp1y) to (cp2x to cp2y)
    }
    
    /**
     * Generate a random gesture curve for more natural movement
     */
    fun generateCurvePoints(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        numPoints: Int = 10
    ): List<Pair<Float, Float>> {
        if (!settings.randomizeGesturePath) {
            return (0 until numPoints).map { i ->
                val t = i.toFloat() / (numPoints - 1)
                (startX + (endX - startX) * t) to (startY + (endY - startY) * t)
            }
        }
        
        val (cp1, cp2) = getBezierControlPoints(startX, startY, endX, endY)
        val (cp1x, cp1y) = cp1
        val (cp2x, cp2y) = cp2
        
        return (0 until numPoints).map { i ->
            val t = i.toFloat() / (numPoints - 1)
            bezierPoint(startX, startY, cp1x, cp1y, cp2x, cp2y, endX, endY, t)
        }
    }
    
    /**
     * Calculate bezier curve point
     */
    private fun bezierPoint(
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        x3: Float, y3: Float,
        t: Float
    ): Pair<Float, Float> {
        val cx = 3 * (x1 - x0)
        val bx = 3 * (x2 - x1) - cx
        val ax = x3 - x0 - cx - bx
        
        val cy = 3 * (y1 - y0)
        val by = 3 * (y2 - y1) - cy
        val ay = y3 - y0 - cy - by
        
        val x = ax * t * t * t + bx * t * t + cx * t + x0
        val y = ay * t * t * t + by * t * t + cy * t + y0
        
        return x to y
    }
    
    /**
     * Reset to default settings
     */
    fun reset() {
        settings = RandomizationSettings()
    }
}

/**
 * Extension function to apply randomization to any gesture
 */
fun Long.withRandomization(): Long {
    return RandomizationHelper.randomizeTapDuration(this)
}
