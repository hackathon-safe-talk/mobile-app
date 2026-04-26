package com.snow.safetalk.notification

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.GuardedBy

/**
 * Persistent registry mapping `analysisId` (UUID string) → unique `notificationId` (Int).
 *
 * Design decisions:
 *  - Uses SharedPreferences for persistence across process death, app kill, and reboot.
 *  - Avoids hashCode() which has high collision probability across UUID strings.
 *  - Uses an atomic incrementing counter starting at 10_000 to avoid clashing with
 *    any hardcoded notification IDs used elsewhere (e.g., foreground service = 1).
 *  - Auto-cleans entries older than 24 hours on every register() call to prevent
 *    unbounded growth.
 *  - All public methods are synchronized for thread-safety from concurrent
 *    BroadcastReceiver / NotificationListenerService / UI thread access.
 *
 * Storage layout (SharedPreferences "safetalk_notif_id_registry"):
 *   "counter"               → Int   (next available notificationId)
 *   "id:<analysisId>"       → Int   (notificationId)
 *   "ts:<analysisId>"       → Long  (registration epoch millis)
 */
object NotificationIdRegistry {

    private const val TAG = "NotifIdRegistry"

    private const val PREFS_NAME = "safetalk_notif_id_registry"
    private const val KEY_COUNTER = "counter"
    private const val PREFIX_ID = "id:"
    private const val PREFIX_TS = "ts:"

    /** Starting offset to prevent clashing with foreground-service or other fixed IDs. */
    private const val COUNTER_START = 10_000

    /** Stale entries older than this are auto-removed on next register(). */
    private const val STALE_THRESHOLD_MS = 24L * 60 * 60 * 1000 // 24 hours

    /** Hard cap: if registry exceeds this count, force a full cleanup. */
    private const val MAX_ENTRIES = 200

    @GuardedBy("this")
    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Register a new analysisId and return its unique notificationId.
     *
     * If the analysisId is already registered, returns the existing notificationId
     * (idempotent — safe to call from debounced receivers).
     *
     * Triggers stale-entry cleanup opportunistically.
     */
    @Synchronized
    fun register(context: Context, analysisId: String): Int {
        val sp = prefs(context)

        // Idempotent: return existing if present
        val existingKey = PREFIX_ID + analysisId
        if (sp.contains(existingKey)) {
            val existingId = sp.getInt(existingKey, -1)
            Log.d(TAG, "register() idempotent hit: $analysisId → $existingId")
            // Refresh timestamp to prevent premature cleanup
            sp.edit().putLong(PREFIX_TS + analysisId, System.currentTimeMillis()).apply()
            return existingId
        }

        // Allocate new ID from counter
        val counter = sp.getInt(KEY_COUNTER, COUNTER_START)
        val newId = counter

        sp.edit()
            .putInt(KEY_COUNTER, counter + 1)
            .putInt(PREFIX_ID + analysisId, newId)
            .putLong(PREFIX_TS + analysisId, System.currentTimeMillis())
            .apply()

        Log.d(TAG, "register() new: $analysisId → $newId (counter now ${counter + 1})")

        // Opportunistic cleanup
        cleanupStaleEntries(context)

        return newId
    }

    /**
     * Look up the notificationId for a given analysisId.
     * Returns null if not registered (already dismissed or never posted).
     */
    @Synchronized
    fun getNotificationId(context: Context, analysisId: String): Int? {
        val sp = prefs(context)
        val key = PREFIX_ID + analysisId
        return if (sp.contains(key)) sp.getInt(key, -1) else null
    }

    /**
     * Remove the mapping for a given analysisId.
     * Called after successfully cancelling the notification from ResultScreen.
     */
    @Synchronized
    fun unregister(context: Context, analysisId: String) {
        val sp = prefs(context)
        sp.edit()
            .remove(PREFIX_ID + analysisId)
            .remove(PREFIX_TS + analysisId)
            .apply()
        Log.d(TAG, "unregister() $analysisId")
    }

    /**
     * Return all currently registered analysisId → notificationId mappings.
     */
    @Synchronized
    fun getAllActiveIds(context: Context): Map<String, Int> {
        val sp = prefs(context)
        val result = mutableMapOf<String, Int>()
        for ((key, value) in sp.all) {
            if (key.startsWith(PREFIX_ID) && value is Int) {
                val analysisId = key.removePrefix(PREFIX_ID)
                result[analysisId] = value
            }
        }
        return result
    }

    /**
     * Check whether a given analysisId already has an active notification registered.
     */
    @Synchronized
    fun isRegistered(context: Context, analysisId: String): Boolean {
        return prefs(context).contains(PREFIX_ID + analysisId)
    }

    /**
     * Remove ALL mappings — used as nuclear reset (e.g., user clears app data).
     */
    @Synchronized
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        Log.w(TAG, "clearAll() — registry wiped")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Removes entries whose timestamp is older than [STALE_THRESHOLD_MS].
     * Also enforces [MAX_ENTRIES] hard cap.
     */
    @Synchronized
    private fun cleanupStaleEntries(context: Context) {
        val sp = prefs(context)
        val now = System.currentTimeMillis()
        val editor = sp.edit()
        var removedCount = 0

        val allEntries = sp.all
        val analysisIds = mutableListOf<String>()

        for ((key, _) in allEntries) {
            if (key.startsWith(PREFIX_ID)) {
                analysisIds.add(key.removePrefix(PREFIX_ID))
            }
        }

        // Remove stale entries
        for (aid in analysisIds) {
            val ts = sp.getLong(PREFIX_TS + aid, 0L)
            if (ts > 0 && (now - ts) > STALE_THRESHOLD_MS) {
                editor.remove(PREFIX_ID + aid)
                editor.remove(PREFIX_TS + aid)
                removedCount++
            }
        }

        // Hard cap enforcement: if still too many, remove oldest
        if (analysisIds.size - removedCount > MAX_ENTRIES) {
            val remaining = analysisIds
                .filter { sp.getLong(PREFIX_TS + it, 0L) > 0 }
                .sortedBy { sp.getLong(PREFIX_TS + it, 0L) }

            val toRemove = remaining.size - MAX_ENTRIES
            for (i in 0 until toRemove) {
                val aid = remaining[i]
                editor.remove(PREFIX_ID + aid)
                editor.remove(PREFIX_TS + aid)
                removedCount++
            }
        }

        if (removedCount > 0) {
            editor.apply()
            Log.d(TAG, "cleanupStaleEntries() removed $removedCount stale entries")
        }
    }
}
