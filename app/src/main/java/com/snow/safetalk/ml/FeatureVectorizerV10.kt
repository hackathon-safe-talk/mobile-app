package com.snow.safetalk.ml

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Reconstructs Python TF-IDF + Structured Scaler logic perfectly.
 */
class FeatureVectorizerV10(private val context: Context) {

    private var vocabulary: Map<String, Int> = emptyMap()
    private var idf: DoubleArray = doubleArrayOf() // Using double for precision during TF-IDF
    private var tfidfFeaturesCount = 0
    private var totalFeaturesCount = 0
    
    private var scalerMaxAbs: DoubleArray = doubleArrayOf()
    
    // Exactly matches Python token_pattern: r'(?u)\b\w\w+\b|\w+[.][a-z]+'
    private val tokenPattern = Pattern.compile("(?U)\\b\\w\\w+\\b|\\w+\\.[a-z]+")
    
    // Full URL Regex matching advanced TLDs as requested in previous pipeline
    private val advancedUrlPattern = Pattern.compile(
        "(https?://\\S+|www\\.\\S+|\\b[\\w-]+\\.(com|org|net|uz|ru|xyz|top|click|online|site|store)\\b)", 
        Pattern.CASE_INSENSITIVE
    )

    private var isLoaded = false

    fun load() {
        if (isLoaded) return
        try {
            // Load Metadata
            val metaJson = context.assets.open("ml/v10_metadata.json").use { it.bufferedReader().readText() }
            val metaObj = JSONObject(metaJson)
            tfidfFeaturesCount = metaObj.getInt("tfidf_features")
            totalFeaturesCount = metaObj.getInt("total_features")
            
            val maxAbsJson = metaObj.getJSONArray("scaler_max_abs")
            scalerMaxAbs = DoubleArray(maxAbsJson.length()) { i -> maxAbsJson.getDouble(i) }

            // Load Vocab
            val vocabJson = context.assets.open("ml/v10_tfidf_vocabulary.json").use { it.bufferedReader().readText() }
            val vocabObj = JSONObject(vocabJson)
            val vocabMap = mutableMapOf<String, Int>()
            vocabObj.keys().forEach { key ->
                vocabMap[key] = vocabObj.getInt(key)
            }
            vocabulary = vocabMap

            // Load IDF
            val idfJson = context.assets.open("ml/v10_tfidf_idf.json").use { it.bufferedReader().readText() }
            val idfArray = JSONArray(idfJson)
            idf = DoubleArray(idfArray.length()) { i -> idfArray.getDouble(i) }

            isLoaded = true
        } catch (e: Exception) {
            android.util.Log.e("SafeTalkML", "Failed to load V10 vectorizer assets", e)
        }
    }

    /**
     * Extracts tokens exactly like scikit-learn.
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val matcher = tokenPattern.matcher(text)
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }

    /**
     * Extracts TF-IDF and structured features, merging them exactly as the Python pipeline.
     */
    fun transform(originalText: String, cleanText: String): FloatArray {
        if (!isLoaded) load()
        if (totalFeaturesCount == 0) return FloatArray(0)

        // The final vector will be fed into ONNX
        val vector = DoubleArray(totalFeaturesCount) { 0.0 }

        // --- 1. COMPUTING TF-IDF ---
        
        val tokens = tokenize(cleanText)
        val ngrams = mutableListOf<String>()
        ngrams.addAll(tokens)
        
        // Add bigrams (ngram_range = 1,2)
        for (i in 0 until tokens.size - 1) {
            ngrams.add("${tokens[i]} ${tokens[i + 1]}")
        }

        val termCounts = mutableMapOf<String, Int>()
        for (gram in ngrams) {
            termCounts[gram] = termCounts.getOrDefault(gram, 0) + 1
        }

        // TF-IDF Multiplication
        for ((term, count) in termCounts) {
            vocabulary[term]?.let { index ->
                if (index < tfidfFeaturesCount) {
                    vector[index] = count.toDouble() * idf[index]
                }
            }
        }

        // Apply L2 Normalization exclusively to the TF-IDF feature slice
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
        
        // --- 2. COMPUTING STRUCTURED FEATURES ---

        // length = original text length
        val lengthVal = originalText.length.toDouble()
        
        // has_url = boolean check against advanced regex
        val hasUrlVal = if (advancedUrlPattern.matcher(originalText).find()) 1.0 else 0.0
        
        // has_number = contains digits
        val hasNumberVal = if (originalText.any { it.isDigit() }) 1.0 else 0.0
        
        // Scale structured features using MaxAbsScaler logic: scaled = x / max_abs
        val scaledLength = if (scalerMaxAbs.isNotEmpty() && scalerMaxAbs[0] > 0) lengthVal / scalerMaxAbs[0] else 0.0
        val scaledHasUrl = if (scalerMaxAbs.size > 1 && scalerMaxAbs[1] > 0) hasUrlVal / scalerMaxAbs[1] else 0.0
        val scaledHasNumber = if (scalerMaxAbs.size > 2 && scalerMaxAbs[2] > 0) hasNumberVal / scalerMaxAbs[2] else 0.0
        
        // Append structured features strictly in order
        vector[tfidfFeaturesCount] = scaledLength
        vector[tfidfFeaturesCount + 1] = scaledHasUrl
        vector[tfidfFeaturesCount + 2] = scaledHasNumber
        
        // Downcast natively to FloatArray for ONNX compatibility
        return FloatArray(totalFeaturesCount) { i -> vector[i].toFloat() }
    }
}
