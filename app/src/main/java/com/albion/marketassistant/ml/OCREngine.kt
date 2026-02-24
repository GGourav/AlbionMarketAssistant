package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.albion.marketassistant.data.OCRResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OCREngine {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    suspend fun recognizeText(
        bitmap: Bitmap,
        region: Rect,
        languageHint: String = "en"
    ): List<OCRResult> = withContext(Dispatchers.Default) {
        try {
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
            
            val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
            val image = InputImage.fromBitmap(croppedBitmap, 0)
            
            return@withContext try {
                val visionText = recognizer.process(image).await()
                parseVisionText(visionText, region)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            } finally {
                croppedBitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
                            confidence = line.confidence,
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
    
    private fun extractInteger(text: String): Int? {
        return try {
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
}
