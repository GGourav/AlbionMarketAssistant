package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.albion.marketassistant.data.OCRResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OCREngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap, region: Rect): List<OCRResult> {
        return try {
            val safeLeft = Math.max(0, region.left)
            val safeTop = Math.max(0, region.top)
            val width = Math.min(bitmap.width - safeLeft, region.width())
            val height = Math.min(bitmap.height - safeTop, region.height())

            val croppedBitmap = Bitmap.createBitmap(bitmap, safeLeft, safeTop, width, height)
            val image = InputImage.fromBitmap(croppedBitmap, 0)
            
            val result = recognizer.process(image).await()
            val ocrResults = mutableListOf<OCRResult>()

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text
                    val confidence = line.confidence ?: 0f
                    val numericValue = text.replace(Regex("[^0-9]"), "").toIntOrNull()
                    
                    if (confidence > 0.6f) {
                        ocrResults.add(OCRResult(text, numericValue, confidence, line.boundingBox))
                    }
                }
            }
            croppedBitmap.recycle()
            ocrResults.sortedBy { it.boundingBox?.top ?: 0 }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
