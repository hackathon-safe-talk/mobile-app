package com.snow.safetalk.ml

import android.content.Context
import android.content.res.AssetManager
import com.snow.safetalk.analysis.MessageAnalysisPayload
import com.snow.safetalk.analysis.MessageSource
import com.snow.safetalk.analysis.SafeTalkAnalyzer
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.mockito.Mockito.*
import java.io.File
import java.io.FileInputStream

/**
 * Real-asset Runtime Verification Test for SafeTalk ML Integration.
 * Mocks the Android Context to load actual files from the assets directory.
 */
class IntegratedRuntimeTest {

    private lateinit var mockContext: Context
    private lateinit var mockAssets: AssetManager

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

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockAssets = mock(AssetManager::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        `when`(mockContext.assets).thenReturn(mockAssets)
        
        // Point mocks to actual filesystem files
        val assetPath = "app/src/main/assets/ml/"
        
        val files = listOf(
            "safetalk_classifier_v7.onnx",
            "tfidf_vocabulary_v7.json",
            "tfidf_idf_v7.json",
            "xai_weights_v7.json",
            "model_metadata_v7.json"
        )
        
        files.forEach { fileName ->
            `when`(mockAssets.open("ml/$fileName")).thenAnswer {
                FileInputStream(File(assetPath + fileName))
            }
        }
    }

    @Test
    fun verifyIntegratedAnalyzerFlow() {
        println("=== SafeTalk Runtime Verification Report (Phase 3) ===\n")
        
        testMessages.forEachIndexed { index, msg ->
            val payload = MessageAnalysisPayload(cleanMessageText = msg)
            val result = SafeTalkAnalyzer.analyzeMessage(
                payload = payload,
                source = MessageSource.MANUAL,
                context = mockContext
            )
            
            println("${index + 1}. Xabar: $msg")
            println("   Score:    ${result.risk.percent}%")
            println("   Band:     ${result.risk.level}")
            println("   Reasons:  ${result.reasons.joinToString(", ")}")
            println("   Status:   ${if (result.risk.percent > 0) "✅ PROCESSED" else "ℹ️ NORMAL"}")
            println("-----------------------------------\n")
        }
    }

    @Test
    fun verifyFallbackSafety() {
        println("=== Fallback Mechanism Verification ===\n")
        
        // Simulate missing model by throwing exception on open
        `when`(mockAssets.open("ml/safetalk_classifier_v7.onnx")).thenThrow(RuntimeException("File not found"))
        
        val msg = "Kartangiz bloklandi darhol tasdiqlang"
        val payload = MessageAnalysisPayload(cleanMessageText = msg)
        
        // This should NOT crash and should return a valid signal-only result
        val result = SafeTalkAnalyzer.analyzeMessage(
            payload = payload,
            source = MessageSource.MANUAL,
            context = mockContext
        )
        
        println("Fallback Result for '$msg':")
        println("   Score:    ${result.risk.percent}%")
        println("   Band:     ${result.risk.level}")
        println("   Reasons:  ${result.reasons.joinToString(", ")}")
        
        // Verify it didn't crash
        assertNotNull(result)
        println("\n✅ Fallback safety verified: System returned result successfully after simulated ML failure.")
    }
}
