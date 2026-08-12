package ru.autolog.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
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
        if (container.googleDrive.lastSignedInAccount() == null) return Result.success()

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
        private const val UNIQUE = "autolog_cloud_sync"

        fun enqueue(context: Context, period: SyncPeriod) {
            val wm = WorkManager.getInstance(context)
            if (period == SyncPeriod.OFF) {
                wm.cancelUniqueWork(UNIQUE)
                return
            }
            val days = when (period) {
                SyncPeriod.OFF -> return
                SyncPeriod.DAILY -> 1L
                SyncPeriod.EVERY_3_DAYS -> 3L
                SyncPeriod.WEEKLY -> 7L
            }
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(days, TimeUnit.DAYS)
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
