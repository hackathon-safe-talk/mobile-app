package com.snow.safetalk.ml

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * V11 Vectorizer precisely replicating TF-IDF Unigrams + Bigrams and 3x Structured Scaler features natively for contextual inference.
 */
class FeatureVectorizerV11(private val context: Context) {

    private var vocabulary: Map<String, Int> = emptyMap()
    private var idf: DoubleArray = doubleArrayOf() 
    private var tfidfFeaturesCount = 0
    private var totalFeaturesCount = 0
    
    private var scalerMaxAbs: DoubleArray = doubleArrayOf()
    
    // Strict Match to Python TF-IDF Default Pattern: (?u)\b\w\w+\b|\w+[.][a-z]+
    private val tokenPattern = Pattern.compile("(?U)\\b\\w\\w+\\b|\\w+\\.[a-z]+")
    
    // V11 Expanded URL Extractor accurately representing Phishing vectors
    private val advancedUrlPattern = Pattern.compile(
        "(https?://\\S+|www\\.\\S+|\\b[\\w-]+\\.(com|org|net|uz|ru|xyz|top|click|online|site|store)\\b|\\b(?:bit\\.ly|t\\.me|tinyurl\\.com)/\\S+)", 
        Pattern.CASE_INSENSITIVE
    )

    private var isLoaded = false

    fun load() {
        if (isLoaded) return
        try {
            // Load Metadata parameters dynamically targeting V11 components
            val metaJson = context.assets.open("ml/v11_metadata.json").use { it.bufferedReader().readText() }
            val metaObj = JSONObject(metaJson)
            tfidfFeaturesCount = metaObj.getInt("tfidf_features")
            totalFeaturesCount = metaObj.getInt("total_features")
            
            val maxAbsJson = metaObj.getJSONArray("scaler_max_abs")
            scalerMaxAbs = DoubleArray(maxAbsJson.length()) { i -> maxAbsJson.getDouble(i) }

            // Vocabulary Map
            val vocabJson = context.assets.open("ml/v11_tfidf_vocabulary.json").use { it.bufferedReader().readText() }
            val vocabObj = JSONObject(vocabJson)
            val vocabMap = mutableMapOf<String, Int>()
            vocabObj.keys().forEach { key ->
                vocabMap[key] = vocabObj.getInt(key)
            }
            vocabulary = vocabMap

            // Inverse Document Frequency array
            val idfJson = context.assets.open("ml/v11_tfidf_idf.json").use { it.bufferedReader().readText() }
            val idfArray = JSONArray(idfJson)
            idf = DoubleArray(idfArray.length()) { i -> idfArray.getDouble(i) }

            isLoaded = true
        } catch (e: Exception) {
            android.util.Log.e("SafeTalkML", "Failed to load V11 vectorizer JSON arrays", e)
        }
    }

    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val matcher = tokenPattern.matcher(text)
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }

    /**
     * Executes fully matching V11 mapping onto target FloatArray matching [1, 15003] bounds natively.
     */
    fun transform(originalText: String, cleanText: String): FloatArray {
        if (!isLoaded) load()
        if (totalFeaturesCount == 0) return FloatArray(0)

        val vector = DoubleArray(totalFeaturesCount) { 0.0 }

        // --- 1. TF-IDF Execution ---
        
        val tokens = tokenize(cleanText)
        val ngrams = mutableListOf<String>()
        ngrams.addAll(tokens) // 1-grams
        
        // 2-grams append mechanism
        for (i in 0 until tokens.size - 1) {
            ngrams.add("${tokens[i]} ${tokens[i + 1]}")
        }

        val termCounts = mutableMapOf<String, Int>()
        for (gram in ngrams) {
            termCounts[gram] = termCounts.getOrDefault(gram, 0) + 1
        }

        // Multiplying term frequency with isolated IDF
        for ((term, count) in termCounts) {
            vocabulary[term]?.let { index ->
                if (index < tfidfFeaturesCount) {
                    vector[index] = count.toDouble() * idf[index]
                }
            }
        }

        // L2 NORM (strict Scikit-Learn bounding) applied only to TF-IDF indexes!
        var sumSquares = 0.0
        for (i in 0 until tfidfFeaturesCount) {
            sumSquares += vector[i] * vector[i]
        }
        val norm = Math.sqrt(sumSquares)
        if (norm > 0) {
            for (i in 0 until tfidfFeaturesCount) {
                vector[i] /= norm
            }
        }
        
        // --- 2. STRUCTURED FEATURES ---
        val lengthVal = originalText.length.toDouble()
        val hasUrlVal = if (advancedUrlPattern.matcher(originalText).find()) 1.0 else 0.0
        val hasNumberVal = if (originalText.any { it.isDigit() }) 1.0 else 0.0
        
        val scaledLength = if (scalerMaxAbs.isNotEmpty() && scalerMaxAbs[0] > 0) lengthVal / scalerMaxAbs[0] else 0.0
        val scaledHasUrl = if (scalerMaxAbs.size > 1 && scalerMaxAbs[1] > 0) hasUrlVal / scalerMaxAbs[1] else 0.0
        val scaledHasNumber = if (scalerMaxAbs.size > 2 && scalerMaxAbs[2] > 0) hasNumberVal / scalerMaxAbs[2] else 0.0
        
        vector[tfidfFeaturesCount] = scaledLength
        vector[tfidfFeaturesCount + 1] = scaledHasUrl
        vector[tfidfFeaturesCount + 2] = scaledHasNumber
        
        return FloatArray(totalFeaturesCount) { i -> vector[i].toFloat() }
    }
}
