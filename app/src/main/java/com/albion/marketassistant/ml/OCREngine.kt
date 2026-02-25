package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.albion.marketassistant.data.OCRResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OCREngine {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Recognize text from bitmap with proper memory management
     * IMPORTANT: Bitmap is recycled after use to prevent memory leaks
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        region: Rect,
        languageHint: String = "en"
    ): List<OCRResult> = withContext(Dispatchers.Default) {
        var croppedBitmap: Bitmap? = null
        
        try {
            // Calculate safe crop region
            val x = maxOf(0, region.left)
            val y = maxOf(0, region.top)
            val width = minOf(
                region.right - x,
                bitmap.width - x
            )
            val height = minOf(
                region.bottom - y,
                bitmap.height - y
            )
            
            if (width <= 0 || height <= 0) {
                return@withContext emptyList()
            }
            
            // Crop bitmap to region
            croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
            val image = InputImage.fromBitmap(croppedBitmap, 0)
            
            val visionText = try {
                awaitTask(recognizer.process(image))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            
            visionText?.let { parseVisionText(it, region) } ?: emptyList()
            
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            // CRITICAL: Always recycle bitmap to prevent memory leaks
            croppedBitmap?.recycle()
            croppedBitmap = null
            
            // Suggest GC to prevent OOM
            System.gc()
        }
    }
    
    /**
     * Process screenshot with automatic cleanup
     */
    suspend fun processScreenshot(
        screenshot: Bitmap,
        regions: List<Rect>
    ): Map<Rect, List<OCRResult>> = withContext(Dispatchers.Default) {
        val results = mutableMapOf<Rect, List<OCRResult>>()
        
        try {
            for (region in regions) {
                if (!isActive) break // Check for cancellation
                
                val text = recognizeText(screenshot, region)
                results[region] = text
                
                // Small delay between regions to prevent thermal throttling
                kotlinx.coroutines.delay(50)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        results
    }
    
    private suspend fun <T> awaitTask(task: com.google.android.gms.tasks.Task<T>): T {
        return suspendCancellableCoroutine { continuation ->
            task.addOnSuccessListener { result ->
                continuation.resume(result)
            }.addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
            
            continuation.invokeOnCancellation {
                // Cancel the task if coroutine is cancelled
                // Note: ML Kit tasks don't support direct cancellation
            }
        }
    }
    
    private fun parseVisionText(
        visionText: com.google.mlkit.vision.text.Text,
        region: Rect
    ): List<OCRResult> {
        val results = mutableListOf<OCRResult>()
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val text = line.text.trim()
                
                if (text.isNotEmpty()) {
                    val numericValue = extractInteger(text)
                    
                    results.add(
                        OCRResult(
                            text = text,
                            confidence = calculateConfidence(line),
                            boundingBox = line.boundingBox ?: Rect(0, 0, 0, 0),
                            isNumber = numericValue != null,
                            numericValue = numericValue
                        )
                    )
                }
            }
        }
        
        return results.sortedBy { it.boundingBox.top }
    }
    
    /**
     * Calculate confidence score for OCR result
     * ML Kit doesn't provide direct confidence, so we estimate based on characteristics
     */
    private fun calculateConfidence(line: com.google.mlkit.vision.text.Text.Line): Float {
        var score = 0.9f // Base confidence
        
        // Reduce confidence for very short text (more prone to errors)
        if (line.text.length < 2) {
            score -= 0.2f
        }
        
        // Increase confidence for numeric-only text
        if (line.text.all { it.isDigit() || it == ',' || it == '.' || it == ' ' }) {
            score += 0.05f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    private fun extractInteger(text: String): Int? {
        return try {
            // Remove common formatting characters
            val cleaned = text.replace(Regex("[^\\d-]"), "")
            if (cleaned.isNotEmpty()) {
                cleaned.toInt()
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    fun extractAllNumbers(text: String): List<Int> {
        val regex = Regex("\\d+")
        return regex.findAll(text)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }
    
    /**
     * Extract price from OCR text with sanity validation
     * Returns null if price seems invalid
     */
    fun extractPrice(
        text: String,
        maxPrice: Int = Int.MAX_VALUE,
        minPrice: Int = 1
    ): Int? {
        val numbers = extractAllNumbers(text)
        
        // Find the most likely price (largest number that's reasonable)
        for (num in numbers.sortedDescending()) {
            if (num in minPrice..maxPrice) {
                return num
            }
        }
        
        return null
    }
}
