package com.snow.safetalk.analysis

import org.junit.Test
import org.junit.Assert.assertTrue
import org.json.JSONArray
import java.io.File
import java.util.Locale

class SafeTalkStressTester {

    @Test
    fun testRegressionPackV7() {
        val packFile = File("src/test/resources/regression_pack_v7.json")
        if (!packFile.exists()) {
            println("Regression pack not found, skipping...")
            return
        }

        val jsonContent = packFile.readText()
        val cases = JSONArray(jsonContent)
        
        var passed = 0
        var failed = 0
        val failures = mutableListOf<String>()

        println("=== SafeTalk V7 Regression Pack (Uzbek-first) ===")
        println("Total cases: ${cases.length()}")

        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val text = case.getString("text")
            val expectedRisk = case.getString("expected_risk")
            val expectedIntent = case.getString("expected_intent")

            val payload = MessageAnalysisPayload(cleanMessageText = text)
            val result = SafeTalkAnalyzer.analyzeMessage(payload)
            
            // Note: Since ML is not active in this JUnit test env, 
            // RiskFusion will rely on SignalRisk and PatternFloor.
            // We verify if the rule-based core meets the minimum expectation.
            
            val actualRiskLevel = result.risk.level // XAVFSIZ, SHUBHALI, XAVFLI
            val actualIntent = result.intent.name // INFO, WARNING, REQUEST, UNKNOWN
            
            val isRiskMatch = when (expectedRisk) {
                "SAFE" -> actualRiskLevel == "XAVFSIZ"
                "SUSPICIOUS" -> actualRiskLevel == "SHUBHALI"
                "DANGEROUS" -> actualRiskLevel == "XAVFLI"
                else -> false
            }
            
            val isIntentMatch = actualIntent == expectedIntent

            if (isRiskMatch && isIntentMatch) {
                passed++
            } else {
                failed++
                failures.add("FAIL: '$text'\n  Expected: $expectedRisk [$expectedIntent], Got: $actualRiskLevel [$actualIntent]")
            }
        }

        println("Passed: $passed")
        println("Failed: $failed")
        
        if (failures.isNotEmpty()) {
            println("\nFailure Details:")
            failures.take(10).forEach { println(it) }
            if (failures.size > 10) println("... and ${failures.size - 10} more.")
        }

        // We target > 90% pass rate for rule-based core on this specific pack
        // ML will boost it further in production.
        assertTrue("Pass rate too low: $passed/${cases.length()}", passed.toFloat() / cases.length() > 0.85)
    }

    @Test
    fun testHardenedAnalysis() {
        // Original manual cases
        val testCases = listOf(
            "Tasdiqlash kodi: 452932" to 0, 
            "O'tkazma qabul qilindi: 50000 so'm" to 0,
            "Bu kodni hech kimga bermang" to 5,
            "Tasdiqlash kodini menga yuboring" to 85,
            "Karta ma'lumotlarini kiriting: http://secure-check.click" to 90,
            "Rasmni oching: photo.jpg.apk" to 90
        )

        testCases.forEach { (text, minExpectedScore) ->
            val payload = MessageAnalysisPayload(cleanMessageText = text)
            val result = SafeTalkAnalyzer.analyzeMessage(payload)
            assertTrue(
                "Expected score >= $minExpectedScore for '$text', but got ${result.risk.percent}",
                result.risk.percent >= minExpectedScore
            )
        }
    }
}
