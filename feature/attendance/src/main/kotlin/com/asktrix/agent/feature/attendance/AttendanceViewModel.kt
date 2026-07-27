package com.asktrix.agent.feature.attendance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.data.repository.AttendanceRepository
import com.asktrix.agent.core.data.repository.AttendanceToday
import com.asktrix.agent.core.location.LocationSampler
import com.asktrix.agent.core.location.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendanceUiState(
    val today: AttendanceToday = AttendanceToday(checkedIn = false),
    val isBusy: Boolean = false,
    val needsLocationPermission: Boolean = false,
    val lastFixAccuracy: Float? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AttendanceRepository,
    private val sampler: LocationSampler,
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(needsLocationPermission = !sampler.hasForegroundPermission()) }
            when (val result = repository.today()) {
                is AsktrixResult.Success -> _state.update { it.copy(today = result.data) }
                is AsktrixResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.toAttendanceMessage())
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(needsLocationPermission = !granted) }
    }

    /**
     * Records attendance and starts or stops location tracking to match.
     *
     * The service is bound to the attendance session on purpose: §10 limits tracking to working
     * hours, and check-in/check-out is the most honest signal of when those actually are. Starting
     * it here also means the call originates from a visible screen, which is what Android 12+
     * requires for a foreground service start.
     */
    fun toggle() {
        val checkingIn = !_state.value.today.checkedIn
        _state.update { it.copy(isBusy = true, errorMessage = null, message = null) }

        viewModelScope.launch {
            when (val result = repository.record(checkIn = checkingIn)) {
                is AsktrixResult.Success -> {
                    if (checkingIn) {
                        LocationTrackingService.start(context)
                    } else {
                        LocationTrackingService.stop(context)
                    }
                    _state.update {
                        it.copy(
                            isBusy = false,
                            lastFixAccuracy = result.data.accuracyMetres,
                            message = if (checkingIn) {
                                "Checked in. Location tracking is on."
                            } else {
                                "Checked out. Location tracking stopped."
                            },
                            today = it.today.copy(checkedIn = checkingIn),
                        )
                    }
                    refresh()
                }

                is AsktrixResult.Failure -> _state.update {
                    it.copy(
                        isBusy = false,
                        needsLocationPermission = result.error is AsktrixError.PermissionDenied,
                        errorMessage = result.error.toAttendanceMessage(),
                    )
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null, errorMessage = null) }
}

fun AsktrixError.toAttendanceMessage(): String = when (this) {
    is AsktrixError.PermissionDenied ->
        "Location permission is required to record attendance."
    is AsktrixError.Offline ->
        "Saved. It will sync when you are back online."
    is AsktrixError.Validation ->
        fieldErrors.values.firstOrNull() ?: "Check the details and try again."
    is AsktrixError.Unexpected ->
        debugContext ?: "Could not get a location fix. Move to an open area and try again."
    else -> "Could not record attendance. Try again."
}
