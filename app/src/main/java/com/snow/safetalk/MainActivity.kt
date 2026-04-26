package com.snow.safetalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.snow.safetalk.navigation.SafeTalkNavigation
import com.snow.safetalk.settings.SettingsDataStore
import com.snow.safetalk.ui.theme.SafeTalkTheme
import androidx.compose.runtime.remember
import com.snow.safetalk.protection.ProtectionManager
import com.snow.safetalk.telegram.NotificationAccessHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Activity-scoped; survives rotation; no nullable field needed. */
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Cold-start: seed the ViewModel from the launch intent once.
        // If the Activity is recreated due to config change the ViewModel
        // already holds the correct value — no re-seeding needed.
        if (savedInstanceState == null) {
            intent?.getStringExtra("analysis_id")?.let { id ->
                val isTrusted = intent.getBooleanExtra("from_persistent_notification", false) || intent.getBooleanExtra("from_safetalk_internal", false)
                if (isTrusted) {
                    mainViewModel.setPendingId(id)
                } else {
                    android.util.Log.d("SafeTalk", "Ignored untrusted intent for analysis_id=$id")
                }
            }
            // ⚠ IMPORTANT: DO NOT dismiss persistent notification here.
            // Notification MUST remain until ResultScreen is ACTUALLY DISPLAYED.
        }

        // Ensure the protection service state matches the persisted flag.
        // Covers the case where the service was killed (force-stop, OEM kill)
        // and the user re-opens the app — idempotent, safe to call always.
        lifecycleScope.launch(Dispatchers.IO) {
            ProtectionManager(applicationContext).syncProtectionState()
        }

        setContent {
            val settingsStore = remember { SettingsDataStore(this@MainActivity) }
            val isDark by settingsStore.isDarkMode.collectAsState(initial = true)

            // Collect the StateFlow — single source of truth for pending deep-links.
            val pendingId by mainViewModel.pendingAnalysisId.collectAsState(initial = null)

            SafeTalkTheme(darkTheme = isDark) {
                SafeTalkNavigation(
                    pendingAnalysisId  = pendingId,
                    onAnalysisConsumed = { mainViewModel.setPendingId(null) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Force rebind NotificationListenerService to fix Xiaomi/HyperOS caching issues
        // which prevents the service from waking up after app install/stop
        if (NotificationAccessHelper.isNotificationAccessGranted(this)) {
            try {
                NotificationAccessHelper.forceRebindService(this)
            } catch (e: Exception) {
                // Ignore safe errors if the system restricts component modification
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra("analysis_id")
        if (id != null) {
            val isTrusted = intent.getBooleanExtra("from_persistent_notification", false) || intent.getBooleanExtra("from_safetalk_internal", false)
            if (isTrusted) {
                mainViewModel.setPendingId(id)
            } else {
                android.util.Log.d("SafeTalk", "Ignored untrusted intent for analysis_id=$id")
            }
        }
        // ⚠ IMPORTANT: DO NOT dismiss persistent notification here.
        // Notification MUST remain until ResultScreen is ACTUALLY DISPLAYED.
    }
}