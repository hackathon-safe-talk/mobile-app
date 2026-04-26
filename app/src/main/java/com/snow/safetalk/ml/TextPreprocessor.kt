package com.snow.safetalk.ml

import java.util.Locale
import java.util.regex.Pattern

/**
 * Android implementation of the Python clean_text pipeline used in SafeTalk ML V10.
 * Reproduces the same regex-based cleaning steps to ensure exact feature parity.
 */
object TextPreprocessor {

    // Matches Python: r'(https?://\S+)'
    private val urlPattern = Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE)
    private val extensions = listOf(".apk", ".exe", ".scr", ".msi", ".bat", ".zip", ".rar")
    
    // Matches Python: r'!!+'
    private val multiExclPattern = Pattern.compile("!!+")
    
    // Matches Python: r"[^\w\s\./:']"
    private val noisePattern = Pattern.compile("[^\\w\\s\\./:']")
    
    // Matches Python: r'\s+'
    private val whitespacePattern = Pattern.compile("\\s+")

    fun cleanText(text: String?): String {
        if (text == null) return ""

        var cleaned = text

        // 1. Lowercase (must happen first according to Python pipeline)
        cleaned = cleaned.lowercase(Locale.ROOT)

        // 2. Normalize Uzbek apostrophes
        cleaned = cleaned.replace("‘", "'")
            .replace("’", "'")
            .replace("`", "'")
            .replace("ʼ", "'") // \u02bc
            
        // 3. Normalize o'/g' Uzbek characters
        cleaned = cleaned.replace("o‘", "o'")
            .replace("o’", "o'")
            .replace("o`", "o'")
        cleaned = cleaned.replace("g‘", "g'")
            .replace("g’", "g'")
            .replace("g`", "g'")

        // 4. Protect URLs (add spaces around)
        cleaned = urlPattern.matcher(cleaned ?: "").replaceAll(" $1 ")

        // 5. Protect dangerous file extensions
        for (ext in extensions) {
            if (cleaned?.contains(ext) == true) {
                cleaned = cleaned?.replace(ext, " $ext ")
            }
        }

        // 6. Normalize multiple exclamation marks
        cleaned = multiExclPattern.matcher(cleaned ?: "").replaceAll(" ! ")

        // 7. Remove noise (keep word chars, whitespace, . / : ')
        cleaned = noisePattern.matcher(cleaned ?: "").replaceAll(" ")

        // 8. Collapse whitespace and trim
        cleaned = whitespacePattern.matcher(cleaned ?: "").replaceAll(" ").trim()

        return cleaned
    }
}