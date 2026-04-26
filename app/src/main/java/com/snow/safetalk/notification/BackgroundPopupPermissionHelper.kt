package com.snow.safetalk.notification

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Centralized helper for MIUI/HyperOS "Display pop-up windows while running
 * in the background" permission management.
 *
 * This permission is CRITICAL for full-screen security alerts on Xiaomi devices.
 * Without it, [SecurityAlertNotificationManager.launchSecurityAlertDirectly] calls
 * `startActivity()` from background — which MIUI silently blocks.
 *
 * Detection strategy:
 *  - On MIUI: attempt to read AppOpsManager op code 10021
 *    (`OP_BACKGROUND_START_ACTIVITY`), a MIUI-private op.
 *  - On non-MIUI devices: always returns `false` (permission concept doesn't exist,
 *    but we treat it as "unverified" to encourage the user to check).
 *  - On ANY error: returns `false` (safe default — prompts user to verify manually).
 *
 * Navigation strategy:
 *  - Primary: `miui.intent.action.APP_PERM_EDITOR` with `extra_pkgname`
 *  - Fallback: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (standard Android)
 *  - Ultra-fallback: no-op with log (should never happen in practice)
 */
object BackgroundPopupPermissionHelper {

    private const val TAG = "BgPopupPermHelper"

    // MIUI-private AppOps code for "background popup window" / "background activity start"
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open the system settings screen where the user can enable the
     * "Display pop-up windows while running in the background" permission.
     *
     * On MIUI/HyperOS: opens the MIUI app permission editor directly.
     * On other devices: opens the standard app details settings page.
     *
     * This method NEVER throws — all exceptions are caught and logged.
     */
    fun openSettings(context: Context) {
        // ── Attempt 1: MIUI-specific permission editor ────────────────────────
        if (isMiuiDevice()) {
            try {
                val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    putExtra("extra_pkgname", context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(miuiIntent)
                Log.d(TAG, "openSettings(): launched MIUI APP_PERM_EDITOR")
                return
            } catch (e: Exception) {
                Log.w(TAG, "openSettings(): MIUI intent failed, falling back: ${e.message}")
            }
        }

        // ── Attempt 2: Standard app details settings ──────────────────────────
        try {
            val detailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(detailsIntent)
            Log.d(TAG, "openSettings(): launched ACTION_APPLICATION_DETAILS_SETTINGS")
        } catch (e: Exception) {
            // Ultra-fallback: should never happen on a real device
            Log.e(TAG, "openSettings(): all intents failed: ${e.message}")
        }
    }

    /**
     * Check whether the MIUI "background popup" permission is enabled.
     *
     * Returns `true` ONLY when ALL conditions are met:
     *  1. Device is running MIUI/HyperOS
     *  2. AppOpsManager successfully reads op code 10021
     *  3. The op mode is [AppOpsManager.MODE_ALLOWED]
     *
     * Returns `false` in ALL other cases:
     *  - Not a MIUI device (permission concept doesn't exist)
     *  - Op code lookup throws (MIUI version doesn't expose it)
     *  - Op mode is IGNORED/ERRORED/DEFAULT
     *
     * This safe-default approach ensures we ALWAYS prompt the user to verify
     * when detection is uncertain.
     */
    fun isPermissionEnabled(context: Context): Boolean {
        if (!isMiuiDevice()) {
            Log.d(TAG, "isPermissionEnabled(): not MIUI device → false")
            return false
        }

        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val uid = context.applicationInfo.uid
            val packageName = context.packageName

            // Use reflection to call checkOpNoThrow with the MIUI-private op code.
            // The public API limits op codes to the AOSP-defined range, but MIUI
            // extends it with vendor-specific codes like 10021.
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ has unsafeCheckOpNoThrow which accepts int ops
                appOps.unsafeCheckOpNoThrow(OP_BACKGROUND_START_ACTIVITY.toString(), uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(OP_BACKGROUND_START_ACTIVITY.toString(), uid, packageName)
            }

            val enabled = result == AppOpsManager.MODE_ALLOWED
            Log.d(TAG, "isPermissionEnabled(): appOps result=$result, enabled=$enabled")
            enabled
        } catch (e: Exception) {
            // Expected on non-MIUI, or MIUI versions that don't expose this op.
            // Safe default: treat as disabled → user gets prompted to check.
            Log.d(TAG, "isPermissionEnabled(): detection failed (${e.javaClass.simpleName}: ${e.message}) → false")
            false
        }
    }

    /**
     * Detect whether the current device is running MIUI or HyperOS.
     *
     * Checks the `ro.miui.ui.version.name` system property, which is set on
     * all Xiaomi/Redmi/POCO devices running MIUI or HyperOS (which is MIUI-based).
     *
     * Uses reflection to access [android.os.SystemProperties] (hidden API)
     * since there's no public accessor for vendor properties.
     */
    fun isMiuiDevice(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val value = method.invoke(null, "ro.miui.ui.version.name") as? String
            val isMiui = !value.isNullOrBlank()
            Log.d(TAG, "isMiuiDevice(): ro.miui.ui.version.name='$value' → $isMiui")
            isMiui
        } catch (e: Exception) {
            Log.d(TAG, "isMiuiDevice(): SystemProperties check failed → false")
            false
        }
    }
}
