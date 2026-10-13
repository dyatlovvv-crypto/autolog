package ru.autolog.app.di

import android.content.Context
import ru.autolog.app.data.draft.FormDraftStore
import ru.autolog.app.data.local.AutologDatabase
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.data.settings.UserSettingsStore
import ru.autolog.app.data.sync.CloudBackupGateway
import ru.autolog.app.data.sync.GoogleDriveBackupRepository

class AppContainer(context: Context) {
    val database: AutologDatabase = AutologDatabase.getInstance(context)
    val repository: AutologRepository = AutologRepository(
        db = database,
        appContext = context.applicationContext,
    )
    val formDraftStore: FormDraftStore = FormDraftStore(context)
    val settingsStore: UserSettingsStore = UserSettingsStore(context)

    /** Replaceable in instrumented tests (quota / network fakes). */
    @Volatile
    var googleDrive: CloudBackupGateway = GoogleDriveBackupRepository(context)
}
