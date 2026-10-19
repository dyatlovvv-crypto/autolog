package ru.autolog.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import ru.autolog.app.AutologApp
import ru.autolog.app.data.settings.SyncPeriod

/**
 * Upload-only cloud snapshot. Never downloads / never wipes local DB.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = AutologApp.instance.container
        val settings = container.settingsStore.settings.first()
        if (!settings.googleLinked || settings.syncPeriod == SyncPeriod.OFF) {
            return Result.success()
        }
        if (container.googleDrive.isOAuthConfigured().not()) return Result.success()
        // Prefs can say "linked" after Android backup while Play Services session is gone.
        if (container.googleDrive.lastSignedInAccount() == null) {
            container.settingsStore.setGoogleSessionStale(true)
            return Result.success()
        }
        container.settingsStore.setGoogleSessionStale(false)

        return runCatching {
            val json = container.repository.exportBackupJson()
            container.googleDrive.uploadSnapshot(json).getOrThrow()
            container.settingsStore.setLastCloudSyncEpochMs(System.currentTimeMillis())
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_PERIODIC = "autolog_cloud_sync"
        private const val UNIQUE_ONCE = "autolog_cloud_sync_once"

        private val networkConstraints: Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        /**
         * @param replaceSchedule true when user changes period (reset timer).
         *   false on app start — KEEP so opening the app does not push the next run +24h.
         */
        fun enqueue(context: Context, period: SyncPeriod, replaceSchedule: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            if (period == SyncPeriod.OFF) {
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
                wm.cancelUniqueWork(UNIQUE_ONCE)
                return
            }
            val days = when (period) {
                SyncPeriod.OFF -> return
                SyncPeriod.DAILY -> 1L
                SyncPeriod.EVERY_3_DAYS -> 3L
                SyncPeriod.WEEKLY -> 7L
            }
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(days, TimeUnit.DAYS)
                .setConstraints(networkConstraints)
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                if (replaceSchedule) {
                    ExistingPeriodicWorkPolicy.UPDATE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                },
                request,
            )
        }

        /** Immediate upload attempt (e.g. overdue on cold start, or after link). */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun periodMillis(period: SyncPeriod): Long? = when (period) {
            SyncPeriod.OFF -> null
            SyncPeriod.DAILY -> TimeUnit.DAYS.toMillis(1)
            SyncPeriod.EVERY_3_DAYS -> TimeUnit.DAYS.toMillis(3)
            SyncPeriod.WEEKLY -> TimeUnit.DAYS.toMillis(7)
        }
    }
}
