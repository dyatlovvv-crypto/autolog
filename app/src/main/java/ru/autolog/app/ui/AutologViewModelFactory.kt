package ru.autolog.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import ru.autolog.app.AutologApp
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.ui.dashboard.DashboardViewModel
import ru.autolog.app.ui.refuel.AddRefuelViewModel
import ru.autolog.app.ui.reminder.AddReminderViewModel
import ru.autolog.app.ui.vehicle.AddVehicleViewModel

class AutologViewModelFactory(
    private val repository: AutologRepository = AutologApp.instance.container.repository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(repository) as T
            modelClass.isAssignableFrom(AddVehicleViewModel::class.java) ->
                AddVehicleViewModel(repository) as T
            modelClass.isAssignableFrom(AddRefuelViewModel::class.java) ->
                AddRefuelViewModel(repository) as T
            modelClass.isAssignableFrom(AddReminderViewModel::class.java) ->
                AddReminderViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
