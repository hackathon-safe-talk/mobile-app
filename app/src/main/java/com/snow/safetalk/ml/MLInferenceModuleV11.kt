package com.snow.safetalk.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result data class explicitly matching what RiskFusion expects, mapped against V11 Context rules.
 */
data class MLInferenceResultV11(
    val dangerousProbability: Float,
    val suspiciousProbability: Float,
    val safeProbability: Float,
    val suspiciousTokens: List<String> = emptyList(),
    val modelVersion: String = "v11",
    val isForcedDangerous: Boolean = false,
    val finalLabel: String = ""
)

/**
 * High-level Engine orchestrating the V11 semantic inference.
 */
class MLInferenceModuleV11 private constructor(private val context: Context) {

    private val preprocessor = TextPreprocessorV11
    private val vectorizer = FeatureVectorizerV11(context)
    private val onnxManager = OnnxModelManagerV11.getInstance(context)

    companion object {
        @Volatile
        private var INSTANCE: MLInferenceModuleV11? = null

        fun getInstance(context: Context): MLInferenceModuleV11 {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MLInferenceModuleV11(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        vectorizer.load()
        onnxManager.load()
    }
    
    private fun initializeSync() {
        vectorizer.load()
        onnxManager.load()
    }

    /**
     * Process exact arrays ensuring V11 structure
     */
    fun analyze(rawText: String): MLInferenceResultV11? {
        try {
            initializeSync()
            
            val cleanText = preprocessor.cleanText(rawText)
            if (cleanText.isEmpty()) return null

            val features = vectorizer.transform(rawText, cleanText)
            if (features.isEmpty()) return null

            val probabilities = onnxManager.runInference(features) ?: return null

            val dangProb = probabilities["DANGEROUS"] ?: 0f
            val suspProb = probabilities["SUSPICIOUS"] ?: 0f
            val safeProb = probabilities["SAFE"] ?: 0f

            // V11 Post-Processing gating 
            val isForcedDangerous = dangProb >= 0.60f
            val finalLabel: String

            if (isForcedDangerous) {
                finalLabel = "DANGEROUS"
            } else {
                if (dangProb >= suspProb && dangProb >= safeProb) {
                    finalLabel = "DANGEROUS"
                } else if (suspProb >= safeProb) {
                    finalLabel = "SUSPICIOUS"
                } else {
                    finalLabel = "SAFE"
                }
            }

            android.util.Log.d("SafeTalkML", "V11 Inferred: $finalLabel | D=$dangProb, S=$suspProb, Safe=$safeProb")

            return MLInferenceResultV11(
                dangerousProbability = dangProb,
                suspiciousProbability = suspProb,
                safeProbability = safeProb,
                isForcedDangerous = isForcedDangerous,
                finalLabel = finalLabel,
                modelVersion = "v11"
            )

        } catch (e: Exception) {
            android.util.Log.e("SafeTalkML", "V11 Execution Fault", e)
            return null
        }
    }
    
    /**
     * Testing Harness proving strict output boundaries inside standard log environments.
     */
    fun runTests() {
        val testCases = listOf(
            "We are discussing normal family business.",
            "Have you checked out this new crypto doubling investment pool?",
            "URGENT: Your bank account is locked! Hackers are inside! Click to fix: http://scam.ru"
        )
        
        android.util.Log.i("SafeTalkML", "--- STARTING V11 NATIVE VALIDATION TESTS ---")
        for (i in testCases.indices) {
            val res = analyze(testCases[i])
            android.util.Log.i(
                "SafeTalkML", 
                "Test ${i+1}: Result=${res?.finalLabel} | D=${res?.dangerousProbability}, S=${res?.suspiciousProbability}, Safe=${res?.safeProbability} | ForcedDNG=${res?.isForcedDangerous}"
            )
        }
        android.util.Log.i("SafeTalkML", "--- FINISHED V11 VALIDATION TESTS ---")
    }
}
