package com.asktrix.agent.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asktrix.agent.core.common.result.AsktrixResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val employeeCode: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val signedInAs: String? = null,
) {
    val canSubmit: Boolean
        get() = employeeCode.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH && !isSubmitting

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmployeeCodeChange(value: String) =
        _state.update { it.copy(employeeCode = value, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, errorMessage = null) }

    fun onSubmit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.login(current.employeeCode, current.password)) {
                is AsktrixResult.Success ->
                    _state.update {
                        // The password is dropped from state on success so it does not linger in
                        // memory or in a saved-state bundle.
                        it.copy(isSubmitting = false, password = "", signedInAs = result.data.displayName)
                    }

                is AsktrixResult.Failure ->
                    _state.update {
                        it.copy(isSubmitting = false, errorMessage = result.error.toUserMessage())
                    }
            }
        }
    }
}
