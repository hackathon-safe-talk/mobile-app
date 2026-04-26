package com.snow.safetalk.ml

import android.content.Context
import org.json.JSONObject
import java.io.InputStreamReader
import java.util.*

/**
 * A lightweight TF-IDF Vectorizer that loads exported Python metadata.
 * Reproduces TfidfVectorizer(ngram_range=(1,2)).
 */
class TfidfVectorizerLite(private val context: Context) {

    private var vocabulary: Map<String, Int> = emptyMap()
    private var idf: FloatArray = floatArrayOf()
    private var featureCount: Int = 0
    private var isLoaded = false

    fun load() {
        if (isLoaded) return
        try {
            // Load Metadata for feature count
            val metadataJson = context.assets.open("ml/model_metadata_v9.json").use { 
                it.bufferedReader().readText() 
            }
            val metadataObj = JSONObject(metadataJson)
            featureCount = metadataObj.getInt("vectorizer_features")

            // Load Vocabulary
            val vocabJson = context.assets.open("ml/tfidf_vocabulary_v9.json").use { 
                it.bufferedReader().readText() 
            }
            val vocabObj = JSONObject(vocabJson)
            val vocabMap = mutableMapOf<String, Int>()
            vocabObj.keys().forEach { key ->
                vocabMap[key] = vocabObj.getInt(key)
            }
            vocabulary = vocabMap

            // Load IDF Weights
            val idfJson = context.assets.open("ml/tfidf_idf_v9.json").use { 
                it.bufferedReader().readText() 
            }
            val idfList = org.json.JSONArray(idfJson)
            idf = FloatArray(idfList.length()) { i -> idfList.getDouble(i).toFloat() }

            isLoaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Transforms cleaned text into a TF-IDF vector.
     */
    fun transform(cleanText: String): FloatArray {
        if (!isLoaded) load()
        if (featureCount == 0) return FloatArray(0)
        
        val vector = FloatArray(featureCount) { 0f }
        
        val tokens = cleanText.split(" ").filter { it.isNotEmpty() }
        
        // 1-grams and 2-grams
        val ngrams = mutableListOf<String>()
        ngrams.addAll(tokens)
        
        for (i in 0 until tokens.size - 1) {
            ngrams.add("${tokens[i]} ${tokens[i + 1]}")
        }

        // Calculate Term Frequency (Raw Count)
        val termCounts = mutableMapOf<String, Int>()
        for (gram in ngrams) {
            termCounts[gram] = termCounts.getOrDefault(gram, 0) + 1
        }

        // Compute TF-IDF
        for ((term, count) in termCounts) {
            vocabulary[term]?.let { index ->
                if (index < featureCount) {
                    vector[index] = count.toFloat() * idf[index]
                }
            }
        }

        // Apply L2 Normalization (standard sklearn behavior)
        normalizeL2(vector)
        
        return vector
    }

    private fun normalizeL2(vector: FloatArray) {
        var sumSquares = 0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = Math.sqrt(sumSquares.toDouble()).toFloat()
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }
}
