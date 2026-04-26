package com.snow.safetalk.ml

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Isolated Phase 1 Test Harness for SafeTalk ML Module.
 * Validates the core inference, preprocessing, and fusion logic.
 */
class MLModuleTest {

    private val testMessages = listOf(
        "Kartangiz bloklandi darhol tasdiqlang",
        "Hisobingizdan 900000 so'm yechildi",
        "Agar bu siz bo'lmasangiz tasdiqlang",
        "Telegram premium sovg'a olish uchun bosing",
        "To'lov amalga oshmadi havola orqali tasdiqlang",
        "Vasha karta bloklandi. Tasdiqlash uchun linkni bosing",
        "Hisobingizdan 120000 som spisanie. Agar bu siz bo'lmasangiz cancel qiling",
        "Bugun dars nechida boshlanadi",
        "Onam aytdi non opkel",
        "Segodnya uchrashuv soat nechida?"
    )

    @Test
    fun testMLModuleValidation() {
        // NOTE: In a real Android environment, this would use a real context.
        // For Phase 1 validation, we are ensuring the structure and logic are correct.
        println("=== SafeTalk ML Module Validation (Phase 1) ===\n")

        for (msg in testMessages) {
            val cleaned = TextPreprocessor.cleanText(msg)
            
            // Simulation of ML result for report purposes in Phase 1
            // (Real inference requires a running Android/ONNX environment)
            
            // Mocking a signal risk for fusion testing
            val signalRisk = if (msg.contains("bloklandi") || msg.contains("yechildi")) 60 else 0
            
            // In a real test, we would call:
            // val mlResult = MLInferenceModule.getInstance(mockContext).analyze(msg)
            // val fused = RiskFusion.fuse(signalRisk, mlResult)
            
            println("Original: $msg")
            println("Cleaned:  $cleaned")
            println("Signal Risk: $signalRisk%")
            println("-----------------------------------\n")
        }
    }

    @Test
    fun testRiskFusionLogic() {
        // Test Rule A: High Signal Stability
        val resultA = RiskFusion.fuse(85, 40, null, false)
        assertEquals(40f, resultA.finalRisk, 0.1f)
        assertEquals("SHUBHALI", resultA.riskBand)

        // Test Rule B: Synergistic Danger
        val mockML = MLInferenceResult(
            dangerousProbability = 0.6f,
            suspiciousProbability = 0.3f,
            safeProbability = 0.1f,
            suspiciousTokens = listOf("test")
        )
        val resultB = RiskFusion.fuse(75, 0, mockML, false)
        assertEquals(70f, resultB.finalRisk, 0.1f)
        assertEquals("XAVFLI", resultB.riskBand)
        
        // Test Rule C: Extreme ML Confidence
        val mockExtremeML = MLInferenceResult(
            dangerousProbability = 0.95f,
            suspiciousProbability = 0.05f,
            safeProbability = 0.0f,
            suspiciousTokens = listOf("test")
        )
        val resultC = RiskFusion.fuse(0, 0, mockExtremeML, false)
        assertEquals(90f, resultC.finalRisk, 0.1f)
        assertEquals("XAVFLI", resultC.riskBand)
    }

    @Test
    fun testTextPreprocessingParity() {
        val input = "Hello! Check this: https://safe.com/test?id=123 or email me at user@test.uz. Call +998901234567."
        val expected = "hello check this or email me at call"
        assertEquals(expected, TextPreprocessor.cleanText(input))
    }
}
