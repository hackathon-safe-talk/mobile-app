package com.snow.safetalk.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result data class explicitly matching what RiskFusion expects, with V10 extensions.
 */
data class MLInferenceResult(
    val dangerousProbability: Float,
    val suspiciousProbability: Float,
    val safeProbability: Float,
    val suspiciousTokens: List<String> = emptyList(),
    val modelVersion: String = "v10",
    val isForcedDangerous: Boolean = false,
    val finalLabel: String = ""
)

/**
 * High-level Engine orchestrating the V10 production pipeline exclusively.
 */
class MLInferenceModule private constructor(private val context: Context) {

    private val preprocessor = TextPreprocessor
    private val vectorizer = FeatureVectorizerV10(context)
    private val onnxManager = OnnxModelManager.getInstance(context)

    companion object {
        @Volatile
        private var INSTANCE: MLInferenceModule? = null

        fun getInstance(context: Context): MLInferenceModule {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MLInferenceModule(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Initialize asynchronously to avoid locking threads during file loading
    suspend fun initialize() = withContext(Dispatchers.IO) {
        vectorizer.load()
        onnxManager.load()
    }
    
    // Fallback sync initialisation if called synchronously
    private fun initializeSync() {
        vectorizer.load()
        onnxManager.load()
    }

    /**
     * Evaluates text using the V10 inference production pipeline.
     */
    fun analyze(rawText: String): MLInferenceResult? {
        try {
            initializeSync()
            
            val cleanText = preprocessor.cleanText(rawText)
            if (cleanText.isEmpty()) return null

            // Generate combined features exactly mirroring Python
            val features = vectorizer.transform(rawText, cleanText)
            if (features.isEmpty()) return null

            // Execute ONNX Inference synchronously on whichever thread triggered this
            // It is expected the caller (SafeTalkAnalyzer) is off the main thread.
            val probabilities = onnxManager.runInference(features) ?: return null

            // Extract Post-Processing parameters safely
            val dangProb = probabilities["DANGEROUS"] ?: 0f
            val suspProb = probabilities["SUSPICIOUS"] ?: 0f
            val safeProb = probabilities["SAFE"] ?: 0f

            // V10 Post-Processing Rule: Force DANGEROUS if confidence is critically high
            val isForcedDangerous = dangProb >= 0.60f
            
            val finalLabel: String

            if (isForcedDangerous) {
                finalLabel = "DANGEROUS"
            } else {
                // Determine baseline argmax natively
                if (dangProb >= suspProb && dangProb >= safeProb) {
                    finalLabel = "DANGEROUS"
                } else if (suspProb >= safeProb) {
                    finalLabel = "SUSPICIOUS"
                } else {
                    finalLabel = "SAFE"
                }
            }

            android.util.Log.d("SafeTalkML", "Inferred: $finalLabel | D=$dangProb, S=$suspProb, Safe=$safeProb")

            return MLInferenceResult(
                dangerousProbability = dangProb,
                suspiciousProbability = suspProb,
                safeProbability = safeProb,
                isForcedDangerous = isForcedDangerous,
                finalLabel = finalLabel,
                modelVersion = "v10"
            )

        } catch (e: Exception) {
            android.util.Log.e("SafeTalkML", "V10 Inference Exception", e)
            return null
        }
    }
    
    /**
     * Mandatory V10 Testing Harness to prove exact behavior
     */
    fun runTests() {
        val testCases = listOf(
            "How are you today, friend?",
            "Your password expires today. Please login at http://secure-update.com",
            "URGENT: Your account has been suspended! Click http://pay-now.xyz to unlock!!"
        )
        
        android.util.Log.i("SafeTalkML", "--- STARTING V10 VALIDATION TESTS ---")
        for (i in testCases.indices) {
            val res = analyze(testCases[i])
            android.util.Log.i(
                "SafeTalkML", 
                "Test ${i+1}: Result=${res?.finalLabel} | D=${res?.dangerousProbability}, S=${res?.suspiciousProbability}, Safe=${res?.safeProbability} | ForcedDNG=${res?.isForcedDangerous}"
            )
        }
        android.util.Log.i("SafeTalkML", "--- FINISHED V10 VALIDATION TESTS ---")
    }
}
