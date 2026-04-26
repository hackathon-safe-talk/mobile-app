package com.snow.safetalk.protection

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.snow.safetalk.settings.SettingsDataStore

/**
 * Periodic health-check watchdog that ensures [ProtectionForegroundService] is running
 * whenever the user has always-on protection enabled.
 *
 * This provides resilience against OEM-specific process killing behaviors
 * (Samsung, Xiaomi, Huawei battery optimizations) where the OS may kill
 * the foreground service without standard lifecycle callbacks.
 *
 * The job runs every ~15 minutes (minimum periodic interval).
 * Each run is lightweight: one synchronous SharedPreferences read +
 * conditional service start (idempotent if already running).
 *
 * This is NOT a clear-all recovery mechanism. It is a standard Android
 * pattern for maintaining service liveness on restrictive OEM devices.
 */
class ProtectionJobService : JobService() {

    companion object {
        private const val TAG = "SafeTalk-JobService"
        private const val JOB_ID_PERIODIC = 9003

        /**
         * Schedule the periodic watchdog job. Idempotent — if the job is
         * already scheduled, this is a no-op (JobScheduler deduplicates by job ID).
         */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return

            // Check if already scheduled to avoid unnecessary re-scheduling
            val existing = scheduler.getPendingJob(JOB_ID_PERIODIC)
            if (existing != null) {
                Log.d(TAG, "Watchdog job already scheduled, skipping")
                return
            }

            val componentName = ComponentName(context, ProtectionJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID_PERIODIC, componentName)
                .setPeriodic(15 * 60 * 1000L)  // 15 minutes (minimum allowed)
                .setPersisted(true)              // Survive reboots
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()

            val result = scheduler.schedule(jobInfo)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Watchdog job scheduled successfully")
            } else {
                Log.w(TAG, "Failed to schedule watchdog job")
            }
        }

        /**
         * Cancel the periodic watchdog job. Called when protection is disabled.
         */
        fun cancel(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            scheduler.cancel(JOB_ID_PERIODIC)
            Log.d(TAG, "Watchdog job cancelled")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.w(TAG, "⏰ onStartJob FIRED jobId=${params?.jobId} pid=${android.os.Process.myPid()}")

        val settingsStore = SettingsDataStore(applicationContext)
        if (!settingsStore.hasAcceptedLegalSync()) {
            cancel(applicationContext)
            return false
        }

        val enabled = settingsStore.isAlwaysOnProtectionEnabledSync()
        Log.w(TAG, "⏰ Protection enabled = $enabled")

        if (enabled) {
            val result = ProtectionForegroundService.start(applicationContext, "ProtectionJobService")
            when (result) {
                is ServiceStarterResult.BlockedBySystem -> {
                    Log.w(TAG, "⏰ FGS start blocked by system. Will retry on next periodic run.")
                }
                is ServiceStarterResult.FailedUnexpectedly -> {
                    Log.e(TAG, "⏰ FGS start error: ${result.error.message}")
                }
                else -> {
                    Log.d(TAG, "⏰ FGS health check: $result")
                }
            }
        } else {
            Log.w(TAG, "⏰ Protection is OFF, cancelling watchdog job")
            cancel(applicationContext)
        }

        return false // No ongoing work; job is complete
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Return false: let the periodic schedule handle the next run naturally.
        return false
    }
}
