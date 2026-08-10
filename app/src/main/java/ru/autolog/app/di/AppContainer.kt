package ru.autolog.app.di

import android.content.Context
import ru.autolog.app.data.local.AutologDatabase
import ru.autolog.app.data.repository.AutologRepository

class AppContainer(context: Context) {
    val database: AutologDatabase = AutologDatabase.getInstance(context)
    val repository: AutologRepository = AutologRepository(database)
}
