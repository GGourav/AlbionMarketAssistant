package com.albion.marketassistant.util

/**
 * Utility class for text comparison and similarity calculations
 * Used for end-of-list detection by comparing page content
 */
object TextSimilarity {

    /**
     * Calculate Levenshtein distance between two strings
     * Returns the number of edits needed to transform one string to another
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        // Create a matrix to store distances
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        // Initialize first column
        for (i in 0..len1) {
            dp[i][0] = i
        }

        // Initialize first row
        for (j in 0..len2) {
            dp[0][j] = j
        }

        // Fill the matrix
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // Deletion
                    dp[i][j - 1] + 1,      // Insertion
                    dp[i - 1][j - 1] + cost // Substitution
                )
            }
        }

        return dp[len1][len2]
    }

    /**
     * Calculate similarity ratio between two strings (0.0 to 1.0)
     * 1.0 means identical, 0.0 means completely different
     */
    fun similarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)

        return 1.0f - (distance.toFloat() / maxLen)
    }

    /**
     * Check if two strings are similar above a threshold
     */
    fun isSimilar(s1: String, s2: String, threshold: Float = 0.9f): Boolean {
        return similarity(s1, s2) >= threshold
    }

    /**
     * Calculate Jaccard similarity for word sets
     * Better for comparing text that may have reordered words
     */
    fun jaccardSimilarity(s1: String, s2: String): Float {
        val words1 = s1.lowercase().split(Regex("\\s+")).toSet()
        val words2 = s2.lowercase().split(Regex("\\s+")).toSet()

        if (words1.isEmpty() && words2.isEmpty()) return 1.0f
        if (words1.isEmpty() || words2.isEmpty()) return 0.0f

        val intersection = words1.intersect(words2)
        val union = words1.union(words2)

        return intersection.size.toFloat() / union.size
    }

    /**
     * Compare OCR results as page signatures
     * Extracts key identifying features from OCR text
     */
    fun comparePageSignatures(text1: String, text2: String): Float {
        // Combine multiple similarity metrics
        val levSimilarity = similarity(text1, text2)
        val jaccardSim = jaccardSimilarity(text1, text2)

        // Weight Levenshtein similarity more heavily
        return levSimilarity * 0.7f + jaccardSim * 0.3f
    }

    /**
     * Extract numeric values from text for comparison
     * Useful for detecting if prices have changed
     */
    fun extractNumericSignature(text: String): List<Int> {
        return Regex("\\d+")
            .findAll(text)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
    }

    /**
     * Compare if numeric values in text are similar
     * Returns similarity ratio for numbers found
     */
    fun compareNumericContent(text1: String, text2: String): Float {
        val nums1 = extractNumericSignature(text1)
        val nums2 = extractNumericSignature(text2)

        if (nums1.isEmpty() && nums2.isEmpty()) return 1.0f
        if (nums1.isEmpty() || nums2.isEmpty()) return 0.0f
        if (nums1.size != nums2.size) return 0.0f

        // Compare sorted numeric values
        val sorted1 = nums1.sorted()
        val sorted2 = nums2.sorted()

        var matchCount = 0
        for (i in sorted1.indices) {
            if (i < sorted2.size && sorted1[i] == sorted2[i]) {
                matchCount++
            }
        }

        return matchCount.toFloat() / sorted1.size
    }

    /**
     * Generate a fingerprint of OCR text for quick comparison
     */
    fun generateFingerprint(text: String): String {
        val normalized = text.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        // Use hash of normalized text plus first/last chars
        val firstChars = normalized.take(20)
        val lastChars = normalized.takeLast(20)
        val length = normalized.length

        return "$length:$firstChars:$lastChars"
    }

    /**
     * Quick check if pages might be identical using fingerprints
     */
    fun fingerprintsMatch(fp1: String, fp2: String): Boolean {
        return fp1 == fp2
    }

    /**
     * Extract first line from OCR text for comparison
     * Used for end-of-list detection
     */
    fun extractFirstLine(text: String, maxLength: Int = 100): String {
        return text.lines()
            .firstOrNull { it.isNotBlank() }
            ?.take(maxLength)
            ?.trim()
            ?: ""
    }

    /**
     * Calculate similarity specifically for end-of-list detection
     * Compares first lines and overall structure
     */
    fun calculatePageMatchScore(
        previousText: String,
        currentText: String,
        firstLineThreshold: Float = 0.95f,
        overallThreshold: Float = 0.9f
    ): PageMatchResult {
        val firstLinePrev = extractFirstLine(previousText)
        val firstLineCurr = extractFirstLine(currentText)

        val firstLineSimilarity = similarity(firstLinePrev, firstLineCurr)
        val overallSimilarity = comparePageSignatures(previousText, currentText)

        val isFirstLineMatch = firstLineSimilarity >= firstLineThreshold
        val isOverallMatch = overallSimilarity >= overallThreshold

        return PageMatchResult(
            firstLineSimilarity = firstLineSimilarity,
            overallSimilarity = overallSimilarity,
            isFirstLineMatch = isFirstLineMatch,
            isOverallMatch = isOverallMatch,
            isLikelySamePage = isFirstLineMatch && isOverallMatch
        )
    }

    /**
     * Result of page comparison
     */
    data class PageMatchResult(
        val firstLineSimilarity: Float,
        val overallSimilarity: Float,
        val isFirstLineMatch: Boolean,
        val isOverallMatch: Boolean,
        val isLikelySamePage: Boolean
    )
}
