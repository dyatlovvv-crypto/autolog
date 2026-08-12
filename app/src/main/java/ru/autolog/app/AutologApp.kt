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
            if (settings.googleLinked) {
                // Refresh profile photo/name from Play Services if still signed in
                container.googleDrive.accountInfo()?.let { info ->
                    container.settingsStore.setGoogleAccount(
                        info.email,
                        info.displayName,
                        info.photoUrl,
                    )
                }
                CloudSyncWorker.enqueue(this@AutologApp, settings.syncPeriod)
            } else {
                CloudSyncWorker.enqueue(this@AutologApp, SyncPeriod.OFF)
            }
        }
    }

    companion object {
        @Volatile
        lateinit var instance: AutologApp
            private set
    }
}
