package com.snow.safetalk.telegram

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, bounded deduplication cache to prevent redundant analysis.
 */
object MessageDeduplicator {
    private val cache = ConcurrentHashMap<String, Long>()
    private const val TTL_MS = 15 * 60 * 1000L // 15 minutes
    private const val MAX_SIZE = 200

    fun isDuplicate(packageName: String, postTime: Long, textHash: Int, senderOrTitle: String?): Boolean {
        cleanup()
        val key = "$packageName|${postTime / 1000L}|$textHash|${senderOrTitle ?: ""}"
        val now = System.currentTimeMillis()
        
        val existing = cache.putIfAbsent(key, now)
        return existing != null
    }

    @Synchronized
    private fun cleanup() {
        val now = System.currentTimeMillis()
        
        // Remove expired entries
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > TTL_MS) {
                iterator.remove()
            }
        }
        
        // Bounded LRU-style eviction: remove the oldest ones if we exceed size
        if (cache.size > MAX_SIZE) {
            val entriesToRemove = cache.entries.sortedBy { it.value }.take(50)
            entriesToRemove.forEach { cache.remove(it.key) }
        }
    }
}
