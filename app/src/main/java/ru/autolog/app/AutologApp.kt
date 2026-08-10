package ru.autolog.app

import android.app.Application
import ru.autolog.app.di.AppContainer
import ru.autolog.app.notifications.ReminderCheckWorker
import ru.autolog.app.notifications.ReminderNotifications

class AutologApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        ReminderNotifications.ensureChannel(this)
        ReminderCheckWorker.enqueue(this)
    }

    companion object {
        @Volatile
        lateinit var instance: AutologApp
            private set
    }
}
