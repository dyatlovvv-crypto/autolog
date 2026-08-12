package ru.autolog.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.autolog.app.data.local.entity.VehicleEntity
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.data.repository.DashboardSnapshot
import ru.autolog.app.domain.VehicleLimits

data class DashboardUiState(
    val loading: Boolean = true,
    val vehicles: List<VehicleEntity> = emptyList(),
    val currentVehicleId: Long? = null,
    val dashboard: DashboardSnapshot = DashboardSnapshot.Empty,
    val canAddVehicle: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val repository: AutologRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = repository.observeVehicles()
        .flatMapLatest { vehicles ->
            val active = vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
            if (active == null) {
                flowOf(
                    DashboardUiState(
                        loading = false,
                        vehicles = vehicles,
                        currentVehicleId = null,
                        canAddVehicle = VehicleLimits.canAdd(vehicles.size),
                    ),
                )
            } else {
                repository.observeDashboard(active.id).map { dash ->
                    DashboardUiState(
                        loading = false,
                        vehicles = vehicles,
                        currentVehicleId = active.id,
                        dashboard = dash,
                        canAddVehicle = VehicleLimits.canAdd(vehicles.size),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    fun selectVehicle(id: Long) {
        viewModelScope.launch { repository.switchVehicle(id) }
    }

    fun deleteRefuelling(id: Long) {
        viewModelScope.launch { repository.deleteRefuelling(id) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { repository.deleteExpense(id) }
    }

    fun deleteService(id: Long) {
        viewModelScope.launch { repository.deleteService(id) }
    }
}
