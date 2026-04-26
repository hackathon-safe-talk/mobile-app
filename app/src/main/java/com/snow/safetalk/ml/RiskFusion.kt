package com.snow.safetalk.ml

import com.snow.safetalk.analysis.AnalysisConstants

/**
 * Result of the synergistic risk fusion.
 */
data class FusionResult(
    val finalRisk: Float,
    val riskBand: String,
    val mlScore: Float, // Renamed from mlProbability to reflect 0-100 scale
    val signalRisk: Float,
    val patternFloor: Int,
    val reasons: List<String>
)

/**
 * Logic for combining Rule-based signals and ML predictions into a final score.
 * Implements a Priority Max + Refined Synergy model to prevent dangerous under-detection.
 */
object RiskFusion {

    /**
     * Fuses Rule-based score, Pattern Engine floor, and ML probability into a final risk score.
     * 
     * Priority Hierarchy:
     * 1. patternFloor (Highest - non-negotiable)
     * 2. ruleScore (Technical indicators - cannot be pulled down by weak ML)
     * 3. mlScore (Textual analysis - provides boost or synergy elevation)
     */
    fun fuse(
        signalRisk: Int,
        patternFloor: Int,
        mlResult: MLInferenceResult?,
        hasHardThreat: Boolean
    ): FusionResult {
        // 1. Semantic Naming
        val dangerousProb = mlResult?.dangerousProbability ?: 0f
        val suspiciousProb = mlResult?.suspiciousProbability ?: 0f
        
        // Define ML score for existing fusion logic
        val mlScore = (dangerousProb * 100f + suspiciousProb * 50f).coerceIn(0f, 100f)
        
        val ruleScore = signalRisk.toFloat()
        
        // 2. Establish Baseline (The technical "Floor")
        var fusedRisk = Math.max(ruleScore, patternFloor.toFloat())
        
        val reasons = mutableListOf<String>()

        // Calibration Logging
        android.util.Log.d("SafeTalkFusion", "--- Calibration Start ---")
        android.util.Log.d("SafeTalkFusion", "In: signalRisk=$signalRisk, patternFloor=$patternFloor, mlScore=$mlScore")
        android.util.Log.d("SafeTalkFusion", "hasHardThreat=$hasHardThreat")

        // 3. Evaluation logic
        
        // A. Pattern Floor Persistence
        if (patternFloor >= AnalysisConstants.DANGEROUS_MIN) {
            reasons.add("Xavfli kombinatsiya aniqlandi (Pattern engine)")
        }

        // B. ML Dominance / Refinement
        if (dangerousProb >= 0.9f) {
            // Extreme ML confidence for DANGEROUS
            if (fusedRisk < 90f) {
                fusedRisk = 90f
                reasons.add("ML modeli juda yuqori ishonch bilan xavfli scamni aniqladi")
            }
        } else if (dangerousProb >= 0.5f || suspiciousProb >= 0.7f) {
            // High ML confidence provides a boost
            if (mlScore > fusedRisk) {
                fusedRisk = Math.max(fusedRisk, fusedRisk * 0.4f + mlScore * 0.6f)
                reasons.add("ML modeli xabarda shubhali elementlarni aniqladi")
            }
        }

        // --- STAGE 5.2: Apply Soft Cap (60) if no hard threat ---
        // CRITICAL BUGFIX: Soft cap MUST be applied BEFORE synergy promotions so it doesn't demote legitimate DANGEROUS cases!
        if (!hasHardThreat && fusedRisk > 60f) {
            fusedRisk = 60f
            android.util.Log.d("SafeTalkFusion", "Soft cap applied: 60")
        }

        // C. Conservative Synergy Rule
        // Only promote to XAVFLI if BOTH baseline and ML are substantially suspicious
        if (fusedRisk >= 55f && (dangerousProb >= 0.5f || mlScore >= 65f) && fusedRisk < AnalysisConstants.DANGEROUS_MIN) {
            fusedRisk = AnalysisConstants.DANGEROUS_MIN.toFloat()
            reasons.add("Texnik va matnli tahlil birgalikda yuqori xavfni tasdiqlamoqda")
        }

        // D. Technical Safety (ML Uncertainty protection)
        if (mlScore < 30f && fusedRisk >= AnalysisConstants.SUSPICIOUS_MIN) {
            reasons.add("Shubhali texnik belgilar saqlanib qoldi")
        }

        // --- STAGE 5.3: ML-Rule Synergy Promotion ---
        // If ML is suspicious (>0.5) but rules are silent, ensure score hits 40.
        if (suspiciousProb > 0.5f && !hasHardThreat && fusedRisk < 40f) {
            fusedRisk = 40f
            reasons.add("Matnli tahlil shubhali manipulyatsiyani aniqladi")
        }

        // 4. Final Clamp and Mapping
        val finalRiskVal = fusedRisk.coerceIn(0f, 100f)
        android.util.Log.d("SafeTalkFusion", "Final Fused Risk: $finalRiskVal")
        android.util.Log.d("SafeTalkFusion", "--- Calibration End ---")
        val band = AnalysisConstants.getLevel(finalRiskVal.toInt())

        return FusionResult(
            finalRisk = finalRiskVal,
            riskBand = band,
            mlScore = mlScore,
            signalRisk = ruleScore,
            patternFloor = patternFloor,
            reasons = reasons.distinct()
        )
    }
}
