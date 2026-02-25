package com.albion.marketassistant.util

import android.graphics.Path
import com.albion.marketassistant.data.AntiDetectionSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Helper class for anti-detection randomization
 * Provides methods to randomize timing, gestures, and patterns
 */
class RandomizationHelper(private val settings: AntiDetectionSettings) {

    private val random = Random(System.currentTimeMillis())

    /**
     * Get randomized delay based on base delay
     * Returns baseDelay ± randomDelayRange
     */
    fun getRandomizedDelay(baseDelay: Long): Long {
        if (!settings.enableRandomization) return baseDelay

        val range = settings.randomDelayRangeMs
        val randomOffset = random.nextLong(-range, range + 1)
        val result = baseDelay + randomOffset

        // Ensure minimum delay
        return result.coerceAtLeast(settings.minRandomDelayMs)
    }

    /**
     * Get randomized delay within a range
     */
    fun getRandomDelay(minMs: Long, maxMs: Long): Long {
        if (!settings.enableRandomization) return (minMs + maxMs) / 2
        return random.nextLong(minMs, maxMs + 1)
    }

    /**
     * Get randomized swipe distance
     * Returns original distance ± percentage
     */
    fun getRandomizedSwipeDistance(originalDistance: Int): Int {
        if (!settings.enableRandomization) return originalDistance

        val maxOffset = (originalDistance * settings.randomSwipeDistancePercent).toInt()
        if (maxOffset == 0) return originalDistance
        val randomOffset = random.nextInt(-maxOffset, maxOffset + 1)
        return (originalDistance + randomOffset).coerceAtLeast(10)
    }

    /**
     * Create a randomized tap gesture path
     * Instead of a single point, creates a small curve
     */
    fun createRandomizedTapPath(x: Int, y: Int): Path {
        val path = Path()

        if (!settings.enableRandomization || !settings.randomizeGesturePath) {
            path.moveTo(x.toFloat(), y.toFloat())
            return path
        }

        val pixels = settings.pathRandomizationPixels

        // Add small random offset to tap position
        val offsetX = if (pixels > 0) random.nextInt(-pixels, pixels + 1) else 0
        val offsetY = if (pixels > 0) random.nextInt(-pixels, pixels + 1) else 0

        path.moveTo((x + offsetX).toFloat(), (y + offsetY).toFloat())

        // Add small micro-movements to simulate human touch
        val microMoveCount = random.nextInt(2, 5)
        for (i in 0 until microMoveCount) {
            val microX = random.nextInt(-1, 2)
            val microY = random.nextInt(-1, 2)
            path.rMoveTo(microX.toFloat(), microY.toFloat())
        }

        return path
    }

    /**
     * Create a randomized swipe gesture path
     * Instead of straight line, creates a slightly curved path
     */
    fun createRandomizedSwipePath(
        startX: Int, startY: Int,
        endX: Int, endY: Int
    ): Path {
        val path = Path()

        if (!settings.enableRandomization || !settings.randomizeGesturePath) {
            path.moveTo(startX.toFloat(), startY.toFloat())
            path.lineTo(endX.toFloat(), endY.toFloat())
            return path
        }

        val pixels = settings.pathRandomizationPixels

        // Randomize start and end points slightly
        val startOffsetX = if (pixels > 0) random.nextInt(-pixels, pixels + 1) else 0
        val startOffsetY = if (pixels > 0) random.nextInt(-pixels, pixels + 1) else 0
        val endOffsetX = if (pixels > 0) random.nextInt(-pixels, pixels + 1) else 0
        val endOffsetY = if (pixels > 0) random.nextInt(-pixels, pixels + 1) else 0

        val actualStartX = startX + startOffsetX
        val actualStartY = startY + startOffsetY
        val actualEndX = endX + endOffsetX
        val actualEndY = endY + endOffsetY

        path.moveTo(actualStartX.toFloat(), actualStartY.toFloat())

        // Add control points for a curved path
        // Number of control points based on swipe distance
        val distance = sqrt(
            (actualEndX - actualStartX).toFloat() * (actualEndX - actualStartX) +
            (actualEndY - actualStartY).toFloat() * (actualEndY - actualStartY)
        )

        val controlPointCount = (distance / 100).toInt().coerceIn(1, 5)

        for (i in 1..controlPointCount) {
            val progress = i.toFloat() / (controlPointCount + 1)

            // Interpolate position
            val baseX = actualStartX + (actualEndX - actualStartX) * progress
            val baseY = actualStartY + (actualEndY - actualStartY) * progress

            // Add random perpendicular offset for curve effect
            val maxCurveOffset = pixels * 2
            val curveOffset = if (maxCurveOffset > 0) random.nextInt(-maxCurveOffset, maxCurveOffset + 1) else 0

            // Calculate perpendicular direction
            val angle = atan2(
                (actualEndY - actualStartY).toDouble(),
                (actualEndX - actualStartX).toDouble()
            ) + kotlin.math.PI / 2

            val curveX = baseX + (curveOffset * cos(angle)).toInt()
            val curveY = baseY + (curveOffset * sin(angle)).toInt()

            path.lineTo(curveX.toFloat(), curveY.toFloat())
        }

        path.lineTo(actualEndX.toFloat(), actualEndY.toFloat())

        return path
    }

    /**
     * Get randomized gesture duration
     */
    fun getRandomizedDuration(baseDurationMs: Long): Long {
        if (!settings.enableRandomization) return baseDurationMs

        // Add ±20% randomization
        val maxOffset = (baseDurationMs * 0.2).toLong()
        val randomOffset = if (maxOffset > 0) random.nextLong(-maxOffset, maxOffset + 1) else 0
        return (baseDurationMs + randomOffset).coerceAtLeast(50)
    }

    /**
     * Randomize order of processing items
     * Returns a shuffled list of indices
     */
    fun getRandomizedIndices(count: Int): List<Int> {
        if (!settings.enableRandomization || count <= 0) {
            return (0 until count).toList()
        }

        return (0 until count).shuffled(random)
    }

    /**
     * Generate a random pause between actions
     */
    fun getRandomPause(): Long {
        return getRandomDelay(settings.minRandomDelayMs, settings.maxRandomDelayMs)
    }

    /**
     * Add human-like variation to repeated actions
     * Returns a factor to multiply standard values by
     */
    fun getHumanVariation(): Float {
        if (!settings.enableRandomization) return 1.0f

        // Returns a value between 0.8 and 1.2 (±20%)
        return 0.8f + random.nextFloat() * 0.4f
    }

    /**
     * Check if should add extra delay (simulates human hesitation)
     * Probability increases with consecutive rapid actions
     */
    fun shouldAddHesitation(recentActionCount: Int): Boolean {
        if (!settings.enableRandomization) return false

        // 5% base chance + 2% per recent action
        val probability = 0.05f + (recentActionCount * 0.02f)
        return random.nextFloat() < probability
    }

    /**
     * Get hesitation delay
     */
    fun getHesitationDelay(): Long {
        return getRandomDelay(100, 500)
    }
}
