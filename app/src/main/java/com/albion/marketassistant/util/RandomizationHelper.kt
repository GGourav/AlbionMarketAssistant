package com.albion.marketassistant.util

import com.albion.marketassistant.data.RandomizationSettings
import kotlin.random.Random

object RandomizationHelper {
    
    private var settings: RandomizationSettings = RandomizationSettings()
    
    fun initialize(newSettings: RandomizationSettings) {
        settings = newSettings
    }
    
    fun getSettings(): RandomizationSettings = settings
    
    fun getRandomDelay(): Long {
        return Random.nextLong(settings.minRandomDelayMs, settings.maxRandomDelayMs + 1)
    }
    
    fun getRandomDelay(minMs: Long, maxMs: Long): Long {
        return Random.nextLong(minMs, maxMs + 1)
    }
    
    fun randomizeSwipeDistance(baseDistance: Float): Float {
        if (settings.randomSwipeDistancePercent <= 0f) return baseDistance
        
        val variation = baseDistance * settings.randomSwipeDistancePercent
        return baseDistance + Random.nextFloat() * variation * 2 - variation
    }
    
    fun randomizePath(startX: Float, startY: Float, endX: Float, endY: Float): List<Pair<Float, Float>> {
        if (!settings.randomizeGesturePath) {
            return listOf(startX to startY, endX to endY)
        }
        
        val path = mutableListOf<Pair<Float, Float>>()
        path.add(startX to startY)
        
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
    
    fun randomizeCoordinate(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        if (!settings.randomizeGesturePath) {
            return x to y
        }
        
        val pixelOffset = settings.pathRandomizationPixels
        val xPercent = randomPixelOffset(pixelOffset) / screenWidth.toFloat()
        val yPercent = randomPixelOffset(pixelOffset) / screenHeight.toFloat()
        
        return (x + xPercent).coerceIn(0f, 1f) to (y + yPercent).coerceIn(0f, 1f)
    }
    
    private fun randomPixelOffset(maxPixels: Int): Float {
        if (maxPixels <= 0) return 0f
        return Random.nextInt(-maxPixels, maxPixels + 1).toFloat()
    }
    
    fun randomizeTapDuration(baseDuration: Long): Long {
        val variation = baseDuration * 0.1f
        return (baseDuration + Random.nextFloat() * variation * 2 - variation).toLong()
            .coerceAtLeast(50)
    }
    
    fun randomizeSwipeDuration(baseDuration: Long): Long {
        val variation = baseDuration * 0.15f
        return (baseDuration + Random.nextFloat() * variation * 2 - variation).toLong()
            .coerceAtLeast(100)
    }
    
    fun randomizeLongPressDuration(baseDuration: Long): Long {
        val jitter = Random.nextLong(-50, 51)
        return (baseDuration + jitter).coerceAtLeast(300)
    }
    
    fun randomizeLoopDelay(baseDelay: Long): Long {
        val variation = baseDelay * 0.2f
        return (baseDelay + Random.nextFloat() * variation * 2 - variation).toLong()
            .coerceAtLeast(100)
    }
    
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
    
    fun reset() {
        settings = RandomizationSettings()
    }
}

fun Long.withRandomization(): Long {
    return RandomizationHelper.randomizeTapDuration(this)
}
