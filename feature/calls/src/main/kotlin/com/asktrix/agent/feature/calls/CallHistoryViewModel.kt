package com.asktrix.agent.feature.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.data.model.CallRecord
import com.asktrix.agent.core.data.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallHistoryUiState(
    val records: List<CallRecord> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * Call history (§7).
 *
 * Sourced entirely from the CRM. The device never reads the system call log, so the app needs no
 * `READ_CALL_LOG` permission and therefore never enters Google Play's sensitive-permission review
 * (ADR-0002). Because §5 removes the dial pad, the CRM's record is also complete - there are no
 * off-system calls to miss.
 */
@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val calls: CallRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CallHistoryUiState())
    val state: StateFlow<CallHistoryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = calls.history()) {
                is AsktrixResult.Success ->
                    _state.update { it.copy(records = result.data, isLoading = false) }

                is AsktrixResult.Failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Could not load call history.",
                        )
                    }
            }
        }
    }
}
