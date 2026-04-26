package com.snow.safetalk.ml

import java.util.Locale
import java.util.regex.Pattern

/**
 * Android implementation of the Python clean_text pipeline used in SafeTalk ML V11.
 * strictly ensures all contextual combinations are caught without syntax breakage.
 */
object TextPreprocessorV11 {

    private val urlPattern = Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE)
    private val extensions = listOf(".apk", ".exe", ".scr", ".msi", ".bat", ".zip", ".rar")
    
    // Multiple exclamation collapse
    private val multiExclPattern = Pattern.compile("!!+")
    
    // Python NOISE_PATTERN = re.compile(r"[^\w\s\./:']")
    private val noisePattern = Pattern.compile("[^\\w\\s\\./:']")
    
    // Python WHITESPACE_PATTERN = re.compile(r"\s+")
    private val whitespacePattern = Pattern.compile("\\s+")

    fun cleanText(text: String?): String {
        if (text == null) return ""

        var cleaned = text

        // 1. Lowercase globally matching Python logic
        cleaned = cleaned.lowercase(Locale.ROOT)

        // 2. Uniform Uzbek apostrophe resolution
        cleaned = cleaned.replace("‘", "'")
            .replace("’", "'")
            .replace("`", "'")
            .replace("ʼ", "'")
            
        // 3. Resolve unified o/g variants
        cleaned = cleaned.replace("o‘", "o'")
            .replace("o’", "o'")
            .replace("o`", "o'")
        cleaned = cleaned.replace("g‘", "g'")
            .replace("g’", "g'")
            .replace("g`", "g'")

        // 4. Force spaces around URLs to protect against vocabulary merging
        cleaned = urlPattern.matcher(cleaned ?: "").replaceAll(" $1 ")

        // 5. Force space encapsulation on critical extensions
        for (ext in extensions) {
            if (cleaned?.contains(ext) == true) {
                cleaned = cleaned?.replace(ext, " $ext ")
            }
        }

        // 6. Urgency marker normalization
        cleaned = multiExclPattern.matcher(cleaned ?: "").replaceAll(" ! ")

        // 7. Eradicate noise characters
        cleaned = noisePattern.matcher(cleaned ?: "").replaceAll(" ")

        // 8. Collapse whitespace properly matching pipeline output
        cleaned = whitespacePattern.matcher(cleaned ?: "").replaceAll(" ").trim()

        return cleaned
    }
}
