package com.snow.safetalk.core

data class AnalysisResult(
    val message: String,
    val risk: Int,
    val confidence: Int,
    val title: String,
    val reasons: List<String>
)

enum class SignalConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class Signal(
    val keywords: List<String>,
    val score: Int,
    val category: String,
    val confidence: SignalConfidence
)
