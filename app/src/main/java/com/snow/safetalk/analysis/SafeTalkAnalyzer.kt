package com.snow.safetalk.analysis

import java.util.Locale
import java.util.regex.Pattern
import com.snow.safetalk.signals.Signals

data class MessageAnalysisPayload(
    val cleanMessageText: String,
    val senderName: String? = null,
    val sourceApp: String? = null,
    val receivedTimestamp: Long? = null,
    val detectedFileName: String? = null,
    val detectedFileType: String? = null,
    val detectedUrls: List<String> = emptyList(),
    val detectedUrl: String? = detectedUrls.firstOrNull()
)

object SafeTalkAnalyzer {

    // Removed unused urlRegex to clean up dead code.

    fun analyzeMessage(
        payload: MessageAnalysisPayload,
        source: MessageSource = MessageSource.MANUAL,
        context: android.content.Context? = null
    ): AnalysisResult {
        val original = payload.cleanMessageText.trim()
        val senderName = payload.senderName
        val sourceApp = payload.sourceApp
        val receivedTimestamp = payload.receivedTimestamp

        if (original.isBlank() && payload.detectedFileName.isNullOrBlank() && payload.detectedUrls.isEmpty()) {
            return AnalysisResult(
                risk = RiskInfo(
                    percent = 0,
                    level = "XAVFSIZ",
                    color = "green",
                    confidence = "past"
                ),
                reasons = emptyList(),
                links = emptyList(),
                recommendation = "Xabar bo‘sh. Hech qanday xavf aniqlanmadi.",
                source = source,
                originalText = original,
                senderName = senderName,
                sourceApp = sourceApp,
                receivedTimestamp = receivedTimestamp
            )
        }

        return try {
            val normalized = normalizeText(original)
            val intent = detectIntent(normalized)
            val signals = detectSignals(normalized).toMutableList()
            
            // Refactored Link Extraction
            val extractedLinks = extractLinks(original)
            val links = (extractedLinks + payload.detectedUrls).distinct()
            
            if (links.isNotEmpty() && "suspicious_link" !in signals) {
                signals.add("suspicious_link")
            }

            // Suspicious TLD Detection
            if (links.any { link -> 
                    val lower = link.lowercase(Locale.ROOT)
                    AnalysisConstants.SUSPICIOUS_TLDS.any { tld -> lower.endsWith(tld) || lower.contains("$tld/") } 
                }) {
                if ("suspicious_tld_match" !in signals) signals.add("suspicious_tld_match")
            }

            val phishingKeywords = listOf("login", "verify", "secure", "update", "account", "bank-login", "card-update", "claim")
            if (links.any { link ->
                    val lowerLink = link.lowercase(Locale.ROOT)
                    phishingKeywords.any { kw -> lowerLink.contains(kw) }
                }) {
                if ("phishing_login" !in signals) signals.add("phishing_login")
            }

            // Detect File Name and Type (Improved)
            var detectedFileName: String? = payload.detectedFileName
            var detectedFileType: String? = payload.detectedFileType

            val allFiles = extractFiles(original)
            
            if (detectedFileName == null && allFiles.isNotEmpty()) {
                val bestFile = allFiles.first()
                detectedFileName = bestFile.name
                detectedFileType = bestFile.type
            }

            // Disguised Extension Detection (e.g. image.png.apk)
            if (detectedFileName != null) {
                val lowerName = detectedFileName.lowercase(Locale.ROOT)
                
                // Better double extension detection: excludes things like v1.2.pdf
                val isDoubleExt = lowerName.contains(Regex("\\.[a-z0-9]{2,4}\\.(?:apk|exe|scr|bat|msi|cmd|vbs|ps1)$"))
                if (isDoubleExt) {
                    if ("double_extension_trick" !in signals) signals.add("double_extension_trick")
                }

                val isDangerous = AnalysisConstants.DANGEROUS_EXTENSIONS.any { lowerName.endsWith(it) }
                if (isDangerous) {
                    if ("dangerous_file_extension" !in signals) {
                        signals.add("dangerous_file_extension")
                    }
                }
            }

            val uniqueSignals = signals.distinct()
            val financialAlert = Signals.FINANCIAL_ALERT_KEYWORDS.any { normalized.contains(it) }

            val baseScore = calculateBaseScore(uniqueSignals)
            
            // --- Pattern Engine Evaluation (Now with Intent) ---
            val patternResult = PatternEngine.evaluate(
                detectedSignals = uniqueSignals.toSet(),
                normalizedText = normalized,
                links = links,
                fileName = detectedFileName,
                fileType = detectedFileType,
                intent = intent
            )
            // ----------------------------------------------------

            // ML Integration
            val mlInferenceResult = context?.let {
                try {
                    val resultV11 = com.snow.safetalk.ml.MLInferenceModuleV11.getInstance(it).analyze(original)
                    // Map V11 result backward-compatibly to standard MLInferenceResult
                    if (resultV11 != null) {
                        com.snow.safetalk.ml.MLInferenceResult(
                            dangerousProbability = resultV11.dangerousProbability,
                            suspiciousProbability = resultV11.suspiciousProbability,
                            safeProbability = resultV11.safeProbability,
                            suspiciousTokens = resultV11.suspiciousTokens,
                            modelVersion = resultV11.modelVersion,
                            isForcedDangerous = resultV11.isForcedDangerous,
                            finalLabel = resultV11.finalLabel
                        )
                    } else null
                } catch (e: Exception) {
                    android.util.Log.e("SafeTalkML", "ML Analysis failed: ${e.message}")
                    null
                }
            }

            // Fuse Base Score, Pattern Floor, and ML Result
            val fusionResult = com.snow.safetalk.ml.RiskFusion.fuse(
                signalRisk = baseScore, 
                patternFloor = patternResult.patternFloor, 
                mlResult = mlInferenceResult,
                hasHardThreat = patternResult.hasHardThreat
            )
            
            var finalPercent = fusionResult.finalRisk.toInt()

            // --- STAGE 2 SAFE GUARD ---
            val isSafeOverride = patternResult.matchedPatternKeys.contains("safe_override_applied")
            val hasHardThreat = patternResult.hasHardThreat

            if (isSafeOverride && !hasHardThreat) {
                // Stronger SAFE override: force to SAFE band if matched service signature
                if (finalPercent >= AnalysisConstants.SUSPICIOUS_MIN) {
                    finalPercent = AnalysisConstants.SAFE_MAX 
                }
            }
            // --------------------------
            val riskInfo = determineRisk(finalPercent, mlInferenceResult)
            val recommendations = generateRecommendations(payload, uniqueSignals, riskInfo, intent)

            buildResult(
                score = finalPercent,
                riskInfo = riskInfo,
                signals = uniqueSignals,
                links = links,
                financialAlert = financialAlert,
                source = source,
                intent = intent,
                originalText = original,
                senderName = senderName,
                sourceApp = sourceApp,
                receivedTimestamp = receivedTimestamp,
                detectedFileName = detectedFileName,
                detectedFileType = detectedFileType,
                recommendationsList = recommendations,
                mlResult = mlInferenceResult,
                fusionResult = fusionResult,
                patternResult = patternResult
            )
        } catch (e: Exception) {
            AnalysisResult(
                risk = RiskInfo(
                    percent = 15,
                    level = AnalysisConstants.getLevel(15),
                    color = "yellow",
                    confidence = "o‘rta"
                ),
                reasons = listOf("Tahlil vaqtida kutilmagan xatolik yuz berdi"),
                links = emptyList(),
                recommendation = "Xabarni ehtiyotkorlik bilan ko‘rib chiqing.",
                source = source,
                originalText = original,
                senderName = senderName,
                sourceApp = sourceApp,
                receivedTimestamp = receivedTimestamp
            )
        }
    }

    private fun detectIntent(normalizedText: String): MessageIntent {
        // Priority 1: REQUEST
        if (AnalysisConstants.REQUEST_PATTERNS.any { normalizedText.contains(it) }) {
            return MessageIntent.REQUEST
        }
        
        // Priority 2: WARNING
        if (AnalysisConstants.WARNING_PATTERNS.any { normalizedText.contains(it) }) {
            return MessageIntent.WARNING
        }
        
        // Priority 3: INFO
        if (AnalysisConstants.INFO_PATTERNS.any { normalizedText.contains(it) }) {
            return MessageIntent.INFO
        }
        
        return MessageIntent.UNKNOWN
    }

    private fun normalizeText(text: String): String {
        // Remove obfuscation patterns (Zero-width spaces, etc.)
        val clean = AnalysisConstants.OBFUSCATION_PATTERNS.replace(text, "")
        
        // Cyrillic to Latin mapping for lookalikes (Homoglyph protection)
        val normalizedHomoglyphs = clean
            .replace("а", "a")
            .replace("о", "o")
            .replace("е", "e")
            .replace("с", "c")
            .replace("р", "p")
            .replace("х", "x")
            .replace("у", "y")
            
        return normalizedHomoglyphs
            .lowercase(Locale.ROOT)
            .replace("'", "‘")
            .replace("`", "‘")
            .replace("’", "‘")
            .replace("o‘", "o‘") // Standardize
            .replace("g‘", "g‘")
            .replace(Regex("\\s+"), " ") // Collapse whitespace
            .replace(Regex("[\\r\\n]+"), " ") // Normalize line breaks
            .trim()
    }

    private fun detectSignals(textLower: String): List<String> {
        val detected = mutableListOf<String>()
        
        // De-space for standard detection. Warning: De-dot is done dynamically 
        // to prevent false positives when checking keywords that contain dots natively like bit.ly.
        val searchSafeText = textLower.replace(" ", "")

        for ((signal, data) in Signals.SIGNALS) {
            if (data.keywords.any { keyword -> 
                val normKwWithDot = keyword.lowercase(Locale.ROOT).replace(" ", "")
                val normKwNoDot = normKwWithDot.replace(".", "")
                
                textLower.contains(keyword) || 
                searchSafeText.contains(normKwWithDot) ||
                // Apply strict de-dotting ONLY when comparing non-dot strings to avoid domain breakage
                searchSafeText.replace(".", "").contains(normKwNoDot)
            }) {
                detected.add(signal)
            }
        }
        return detected
    }

    private fun extractLinks(text: String): List<String> {
        // More robust URL regex handling punctuation and brackets
        val robustUrlPattern = Pattern.compile(
            "(?i)(?:https?://|www\\.|(?:t\\.me|wa\\.me|bit\\.ly|tinyurl\\.com|rb\\.gy|is\\.gd)/)[\\w\\d.\\-/?=&%#~+!]*[\\w\\d/]",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = robustUrlPattern.matcher(text)
        val links = mutableListOf<String>()
        while (matcher.find()) {
            val link = matcher.group()
            // Clean trail if captured by aggressive regex
            val cleaned = link.removeSuffix("!").removeSuffix(",").removeSuffix(")").removeSuffix(".")
            links.add(cleaned)
        }
        
        // Also check for suspicious TLDs standalone
        val standaloneTldPattern = Pattern.compile(
            "(?i)\\b[\\w\\d.-]+\\.(?:click|xyz|top|gift|info|biz|site|apk|online|shop|vip)\\b",
            Pattern.CASE_INSENSITIVE
        )
        val tldMatcher = standaloneTldPattern.matcher(text)
        while (tldMatcher.find()) {
            links.add(tldMatcher.group())
        }

        return links.distinct()
    }

    private data class FileInfo(val name: String, val type: String)

    private fun extractFiles(text: String): List<FileInfo> {
        val results = mutableListOf<FileInfo>()
        val extensions = (AnalysisConstants.DANGEROUS_EXTENSIONS + AnalysisConstants.ARCHIVE_EXTENSIONS + listOf(".pdf", ".doc", ".docx"))
        
        for (ext in extensions.distinct()) {
            val regex = Regex("([\\w\\-_.() ]+?\\Q$ext\\E)", RegexOption.IGNORE_CASE)
            val matches = regex.findAll(text)
            for (match in matches) {
                val fileName = match.groupValues[1].trim()
                val type = when {
                    AnalysisConstants.DANGEROUS_EXTENSIONS.any { fileName.lowercase().endsWith(it) } -> "Xavfli dastur fayli"
                    AnalysisConstants.ARCHIVE_EXTENSIONS.any { fileName.lowercase().endsWith(it) } -> "Arxiv fayli"
                    fileName.lowercase().endsWith(".pdf") -> "PDF hujjat"
                    else -> "Hujjat fayli"
                }
                results.add(FileInfo(fileName, type))
            }
        }
        return results.distinctBy { it.name }
    }

    private fun calculateBaseScore(signals: List<String>): Int {
        val categoryScores = mutableMapOf<String, Int>()
        
        // Custom deduplication for known overlapped signals
        val deduplicatedSignals = signals.toMutableList()
        if ("urgency_pressure" in deduplicatedSignals && "urgency_intent" in deduplicatedSignals) {
            deduplicatedSignals.remove("urgency_intent") // Keep higher one (15 > 5)
        }
        if ("financial_data_request" in deduplicatedSignals && "card_phishing_bait" in deduplicatedSignals) {
            deduplicatedSignals.remove("financial_data_request") // Keep higher (35 > 25)
        }
        if ("account_update_pressure" in deduplicatedSignals && "vague_action_request" in deduplicatedSignals) {
            deduplicatedSignals.remove("vague_action_request") // Keep higher (15 > 10)
        }
        if ("personal_info_request" in deduplicatedSignals && "curiosity_lure" in deduplicatedSignals) {
            deduplicatedSignals.remove("curiosity_lure") // Keep higher (30 > 15)
        }
        
        // Group by category and sum
        for (signal in deduplicatedSignals) {
            val def = Signals.SIGNALS[signal] ?: continue
            categoryScores[def.category] = categoryScores.getOrDefault(def.category, 0) + def.score
        }
        
        // Apply strict category caps to prevent runaway score accumulation
        if (categoryScores.containsKey("SOCIAL_ENGINEERING")) {
            categoryScores["SOCIAL_ENGINEERING"] = categoryScores["SOCIAL_ENGINEERING"]!!.coerceAtMost(30)
        }
        if (categoryScores.containsKey("FINANCIAL")) {
            categoryScores["FINANCIAL"] = categoryScores["FINANCIAL"]!!.coerceAtMost(40)
        }
        
        return categoryScores.values.sum().coerceIn(0, 100)
    }

    private fun determineRisk(score: Int, mlResult: com.snow.safetalk.ml.MLInferenceResult?): RiskInfo {
        // Derive real confidence directly from ML model probabilities if available, else fallback to score tier
        val confidenceStr = if (mlResult != null) {
            val maxProb = maxOf(mlResult.dangerousProbability, mlResult.suspiciousProbability, mlResult.safeProbability)
            val percent = (maxProb * 100).toInt()
            when {
                percent >= 85 -> "yuqori"
                percent >= 60 -> "o‘rta"
                else -> "past"
            }
        } else {
            AnalysisConstants.getConfidenceLabel(score)
        }

        return RiskInfo(
            percent    = score,
            level      = AnalysisConstants.getLevel(score),
            color      = AnalysisConstants.getColor(score),
            confidence = confidenceStr
        )
    }

    private fun generateRecommendations(
        payload: MessageAnalysisPayload,
        signals: List<String>,
        risk: RiskInfo,
        intent: MessageIntent
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (payload.detectedFileName != null || "dangerous_file_extension" in signals) {
            recommendations.add("Bu faylni ochmang yoki o‘rnatmang")
            recommendations.add("Noma'lum fayllar qurilmangizni buzishi mumkin")
        }

        if (payload.detectedUrls.isNotEmpty() || "suspicious_link" in signals || "suspicious_tld_match" in signals) {
            recommendations.add("Havolani bosishdan oldin manzilini tekshiring")
            recommendations.add("Hech qachon noma'lum saytga login yoki parolingizni kiritmang")
        }

        if ("phishing_auth_bait" in signals || "prize_bait" in signals) {
            recommendations.add("Tanish odam nomidan kelgan bo'lsa ham, bunday yutuqlarga ishonmang")
        }

        if (intent == MessageIntent.REQUEST && ("otp_request" in signals || "financial_data_request" in signals)) {
            recommendations.add("Hech qachon SMS kodingizni yoki karta ma'lumotlarini begonaga aytmang")
        }

        if ("personal_info_request" in signals || "financial_data_request" in signals || "otp_request" in signals) {
            recommendations.add("Bank karta ma'lumotlarini yoki SMS kodni hech kimga aytmang")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Xabarni ehtiyotkorlik bilan ko‘rib chiqing")
        }

        return recommendations.distinct().take(3)
    }

    private fun buildResult(
        score: Int,
        riskInfo: RiskInfo,
        signals: List<String>,
        links: List<String>,
        financialAlert: Boolean,
        source: MessageSource,
        intent: MessageIntent,
        originalText: String,
        senderName: String?,
        sourceApp: String?,
        receivedTimestamp: Long?,
        detectedFileName: String?,
        detectedFileType: String?,
        recommendationsList: List<String>,
        mlResult: com.snow.safetalk.ml.MLInferenceResult? = null,
        fusionResult: com.snow.safetalk.ml.FusionResult? = null,
        patternResult: PatternMatchResult? = null
    ): AnalysisResult {
        // Structured Grouped Reasons (STAGE 4 Final Quality Pass)
        val finalReasons = HumanReasons.generateGroupedReasons(
            signals = signals.toSet(),
            riskLevel = riskInfo.level,
            patternMatchedKeys = patternResult?.matchedPatternKeys ?: emptyList()
        ).toMutableList()

        // Only add extra technical metadata if not already implied in the natural sentence
        val hasMainReason = finalReasons.isNotEmpty()
        
        if (mlResult != null && mlResult.suspiciousTokens.isNotEmpty() && riskInfo.level == "XAVFLI") {
            val tokens = mlResult.suspiciousTokens.take(2).joinToString(", ")
            finalReasons.add("Shubhali so'zlar: $tokens")
        }

        if (detectedFileName != null) {
            finalReasons.add("Fayl nomi: $detectedFileName")
        }

        val recommendation = when (riskInfo.level) {
            "XAVFLI" -> "Xabar firibgarlik ekanligi aniq. Havolani ochmang, faylni yuklamang!"
            "SHUBHALI" -> "Xabar shubhali ko‘rinmoqda. Manba va ma'lumotlarni qayta tekshiring."
            else -> "Xabar xavfsiz ko‘rinadi."
        }

        return AnalysisResult(
            risk = riskInfo.copy(percent = score),
            reasons = finalReasons,
            links = links,
            recommendation = recommendation,
            source = source,
            intent = intent,
            originalText = originalText,
            senderName = senderName,
            sourceApp = sourceApp,
            receivedTimestamp = receivedTimestamp,
            detectedFileName = detectedFileName,
            detectedFileType = detectedFileType,
            detectedUrl = links.firstOrNull(),
            recommendations = recommendationsList
        )
    }
}
