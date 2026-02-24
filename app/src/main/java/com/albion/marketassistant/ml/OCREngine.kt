package com.albion.marketassistant.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.albion.marketassistant.data.OCRResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await


class OCREngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap, region: Rect): List<OCRResult> {
        return try {
            // Crop the image to just the area we care about to save processing power
            val safeLeft = Math.max(0, region.left)
            val safeTop = Math.max(0, region.top)
            val width = Math.min(bitmap.width - safeLeft, region.width())
            val height = Math.min(bitmap.height - safeTop, region.height())

            val croppedBitmap = Bitmap.createBitmap(bitmap, safeLeft, safeTop, width, height)
            val image = InputImage.fromBitmap(croppedBitmap, 0)
            
            // Wait for ML Kit to read the text
            val result = recognizer.process(image).await()
            val ocrResults = mutableListOf<OCRResult>()

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text
                    val confidence = line.confidence ?: 0f
                    val numericValue = extractInteger(text)
                    
                    // Only keep results we are reasonably confident about
                    if (confidence > 0.6f) {
                        ocrResults.add(OCRResult(text, numericValue, confidence, line.boundingBox))
                    }
                }
            }
            croppedBitmap.recycle()
            
            // Sort by top-to-bottom so the highest item in the list is first
            ocrResults.sortedBy { it.boundingBox?.top ?: 0 }
            
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractInteger(text: String): Int? {
        // Removes commas, spaces, and silver icons, leaving only numbers
        val cleanText = text.replace(Regex("[^0-9]"), "")
        return cleanText.toIntOrNull()
    }
}
