package com.snow.safetalk.ml

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Metadata for a single token's contribution to the scam prediction.
 */
data class TokenWeight(val token: String, val weight: Float)

/**
 * Helper to identify suspicious tokens based on ML feature weights.
 * Provides "Explainable AI" context to the user.
 */
class XAIHelper(private val context: Context) {

    private var xaiWeights: FloatArray = floatArrayOf()
    private var vocabulary: Map<String, Int> = emptyMap()
    private var isInitialized = false

    fun load() {
        if (isInitialized) return
        try {
            // Load Vocabulary
            val vocabJson = context.assets.open("ml/tfidf_vocabulary_v9.json").use { it.bufferedReader().readText() }
            val vocabObj = JSONObject(vocabJson)
            val vocabMap = mutableMapOf<String, Int>()
            vocabObj.keys().forEach { vocabMap[it] = vocabObj.getInt(it) }
            vocabulary = vocabMap

            // Load Global weights for XAI (DANGEROUS class coefficients)
            val weightsJson = context.assets.open("ml/xai_weights_v9.json").use { it.bufferedReader().readText() }
            val weightsArray = JSONArray(weightsJson)
            xaiWeights = FloatArray(weightsArray.length()) { i -> weightsArray.getDouble(i).toFloat() }
            
            isInitialized = true
        } catch (e: Exception) {
            android.util.Log.e("SafeTalkXAI", "Load failed: ${e.message}")
        }
    }

    /**
     * Extracts top 3 suspicious tokens from the current message's features.
     */
    fun extractSuspiciousTokens(cleanText: String, features: FloatArray): List<String> {
        if (!isInitialized) load()
        
        val tokens = cleanText.split(" ").filter { it.isNotEmpty() }
        val candidates = mutableListOf<TokenWeight>()

        // Check 1-grams and 2-grams contribution
        // Contribution = FeatureValue * ModelWeight
        
        // Unigrams
        for (token in tokens) {
            vocabulary[token]?.let { index ->
                val contribution = features[index] * xaiWeights[index]
                if (contribution > 0.1) { // Only significant positive contributors
                    candidates.add(TokenWeight(token, contribution))
                }
            }
        }
        
        // Bigrams
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]} ${tokens[i+1]}"
            vocabulary[bigram]?.let { index ->
                val contribution = features[index] * xaiWeights[index]
                if (contribution > 0.1) {
                    candidates.add(TokenWeight(bigram, contribution))
                }
            }
        }

        return candidates
            .sortedByDescending { it.weight }
            .map { it.token }
            .distinct()
            .take(3)
    }
}
