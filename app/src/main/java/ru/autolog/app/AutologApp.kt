package ru.autolog.app

import android.app.Application
import ru.autolog.app.di.AppContainer
import ru.autolog.app.notifications.ReminderCheckWorker
import ru.autolog.app.notifications.ReminderNotifications
import ru.autolog.app.data.sync.CloudSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.autolog.app.data.settings.SyncPeriod

class AutologApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        ReminderNotifications.ensureChannel(this)
        ReminderCheckWorker.enqueue(this)
        appScope.launch {
            val settings = container.settingsStore.settings.first()
            if (!settings.googleLinked || settings.syncPeriod == SyncPeriod.OFF) {
                CloudSyncWorker.enqueue(this@AutologApp, SyncPeriod.OFF)
                return@launch
            }
            val account = container.googleDrive.lastSignedInAccount()
            if (account == null) {
                container.settingsStore.setGoogleSessionStale(true)
                // Keep period prefs; do not cancel schedule — user can re-auth and sync.
                CloudSyncWorker.enqueue(
                    this@AutologApp,
                    settings.syncPeriod,
                    replaceSchedule = false,
                )
                return@launch
            }
            container.googleDrive.accountInfo(account)?.let { info ->
                container.settingsStore.setGoogleAccount(
                    info.email,
                    info.displayName,
                    info.photoUrl,
                )
            }
            // KEEP: opening the app must not reset the daily timer to +24h.
            CloudSyncWorker.enqueue(
                this@AutologApp,
                settings.syncPeriod,
                replaceSchedule = false,
            )
            val periodMs = CloudSyncWorker.periodMillis(settings.syncPeriod)
            val last = settings.lastCloudSyncEpochMs
            val overdue = periodMs != null &&
                (last == null || System.currentTimeMillis() - last >= periodMs)
            if (overdue) {
                CloudSyncWorker.enqueueOnce(this@AutologApp)
            }
        }
    }

    companion object {
        @Volatile
        lateinit var instance: AutologApp
            private set
    }
}
