package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.albion.marketassistant.data.OCRResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Singleton OCR Engine using ML Kit
 */
object OCREngine {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isProcessing = AtomicBoolean(false)
    
    suspend fun recognizeText(
        bitmap: Bitmap,
        region: Rect,
        languageHint: String = "en"
    ): List<OCRResult> = withContext(Dispatchers.Default) {
        var croppedBitmap: Bitmap? = null
        
        try {
            val x = maxOf(0, region.left)
            val y = maxOf(0, region.top)
            val width = minOf(region.right - x, bitmap.width - x)
            val height = minOf(region.bottom - y, bitmap.height - y)
            
            if (width <= 0 || height <= 0) {
                return@withContext emptyList()
            }
            
            if (bitmap.isRecycled) {
                return@withContext emptyList()
            }
            
            croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
            val image = InputImage.fromBitmap(croppedBitmap, 0)
            
            val visionText = try {
                awaitTask(recognizer.process(image))
            } catch (e: Exception) {
                android.util.Log.e("OCREngine", "OCR failed", e)
                null
            }
            
            visionText?.let { parseVisionText(it, region) } ?: emptyList()
            
        } catch (e: Exception) {
            android.util.Log.e("OCREngine", "recognizeText error", e)
            emptyList()
        } finally {
            croppedBitmap?.recycle()
        }
    }
    
    suspend fun processScreenshot(
        screenshot: Bitmap,
        regions: List<Rect>
    ): Map<Rect, List<OCRResult>> = withContext(Dispatchers.Default) {
        val results = mutableMapOf<Rect, List<OCRResult>>()
        
        try {
            for (region in regions) {
                ensureActive()
                
                val text = recognizeText(screenshot, region)
                results[region] = text
                
                kotlinx.coroutines.delay(50)
            }
        } catch (e: Exception) {
            android.util.Log.e("OCREngine", "processScreenshot error", e)
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
    
    private fun calculateConfidence(line: com.google.mlkit.vision.text.Text.Line): Float {
        var score = 0.9f
        
        if (line.text.length < 2) {
            score -= 0.2f
        }
        
        if (line.text.all { it.isDigit() || it == ',' || it == '.' || it == ' ' }) {
            score += 0.05f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    private fun extractInteger(text: String): Int? {
        return try {
            val cleaned = text.replace(Regex("[^\\d-]"), "")
            if (cleaned.isNotEmpty()) {
                val value = cleaned.toLong()
                if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else null
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
    
    fun extractPrice(text: String, maxPrice: Int = Int.MAX_VALUE, minPrice: Int = 1): Int? {
        val numbers = extractAllNumbers(text)
        
        for (num in numbers.sortedDescending()) {
            if (num in minPrice..maxPrice) {
                return num
            }
        }
        
        return null
    }
    
    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            android.util.Log.e("OCREngine", "Error closing recognizer", e)
        }
    }
}
