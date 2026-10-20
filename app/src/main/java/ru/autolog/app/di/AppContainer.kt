package ru.autolog.app.di

import android.content.Context
import ru.autolog.app.data.draft.FormDraftStore
import ru.autolog.app.data.local.AutologDatabase
import ru.autolog.app.data.map.NearbyFuelStationsStore
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.data.settings.UserSettingsStore

class AppContainer(context: Context) {
    val database: AutologDatabase = AutologDatabase.getInstance(context)
    val repository: AutologRepository = AutologRepository(
        db = database,
        appContext = context.applicationContext,
    )
    val formDraftStore: FormDraftStore = FormDraftStore(context)
    val settingsStore: UserSettingsStore = UserSettingsStore(context)
    val fuelStations: NearbyFuelStationsStore = NearbyFuelStationsStore(context)
}
