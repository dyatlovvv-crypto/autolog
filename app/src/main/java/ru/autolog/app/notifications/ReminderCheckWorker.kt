package ru.autolog.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.autolog.app.data.local.AutologDatabase
import ru.autolog.app.ui.util.Formatters
import java.util.concurrent.TimeUnit

class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = AutologDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()
        val dueSoonMs = 3L * 24 * 60 * 60 * 1000
        db.reminderDao().getActiveWithNotifications().forEach { reminder ->
            val dueAt = reminder.nextDueDateEpochMs ?: return@forEach
            val remainingKm = reminder.remainingKm
            val soonByDate = dueAt - now <= dueSoonMs
            val soonByKm = remainingKm != null && remainingKm <= 500
            if (!soonByDate && !soonByKm) return@forEach
            val last = reminder.lastNotifiedAtEpochMs ?: 0L
            if (now - last < 20L * 60 * 60 * 1000) return@forEach

            val body = buildString {
                append(reminder.title)
                append(": осталось ")
                append(remainingKm?.toString() ?: "—")
                append(" км или ")
                append(reminder.remainingDays?.toString() ?: "—")
                append(" дн. (примерно ")
                append(Formatters.date(dueAt))
                append(")")
            }
            ReminderNotifications.show(
                applicationContext,
                reminder.id,
                "Пора к сервису",
                body,
            )
            db.reminderDao().update(reminder.copy(lastNotifiedAtEpochMs = now))
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "autolog_reminder_check"

        fun enqueue(context: Context) {
            ReminderNotifications.ensureChannel(context)
            val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
