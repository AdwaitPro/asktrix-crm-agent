package com.asktrix.agent.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asktrix.agent.core.common.session.EmployeeStore
import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.data.repository.ClientRepository
import com.asktrix.agent.core.database.CachePurger
import com.asktrix.agent.core.mdm.DeviceComplianceReporter
import com.asktrix.agent.core.security.DeviceIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val employeeName: String = "",
    val employeeCode: String = "",
    val roleLabel: String = "",
    /** What this role may do (§2). Shown so an employee can see why an action is unavailable. */
    val permissions: List<String> = emptyList(),
    val deviceModel: String = "",
    val androidVersion: String = "",
    val appVersion: String = "",
    val managedDevice: Boolean = false,
    val backgroundRestricted: Boolean = false,
    val integrityConcern: Boolean = false,
    val isSigningOut: Boolean = false,
    val signedOut: Boolean = false,
)

/**
 * A deliberately minimal settings screen (§26).
 *
 * There are no preferences to change. Everything configurable is set by the CRM or by EMM policy, so
 * this is a status page plus a sign-out. Offering toggles would imply the employee can alter
 * behaviour they cannot, and every one would be another thing to lock down.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokens: SessionTokenStore,
    private val employees: EmployeeStore,
    private val clients: ClientRepository,
    private val purger: CachePurger,
    private val deviceIdentity: DeviceIdentity,
    private val compliance: DeviceComplianceReporter,
    private val appVersion: AppVersionName,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            employees.currentEmployee()?.let { employee ->
                _state.update {
                    it.copy(
                        employeeName = employee.displayName,
                        employeeCode = employee.employeeCode,
                        roleLabel = employee.roleLabel,
                        permissions = employee.permissions,
                    )
                }
            }
        }

        val signals = compliance.collect()
        _state.update {
            it.copy(
                deviceModel = "${deviceIdentity.manufacturer} ${deviceIdentity.model}",
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                appVersion = appVersion.value,
                managedDevice = signals.isDeviceOwnerManaged,
                backgroundRestricted = signals.backgroundRestricted,
                integrityConcern = signals.rootIndicators || signals.debuggerAttached,
            )
        }
    }

    /**
     * Signs out and erases every trace of customer data (§3).
     *
     * Order matters. Tokens are cleared first so no in-flight request can repopulate the cache; then
     * the cache is emptied; then the database file is deleted and the Keystore key destroyed, which
     * renders any residual bytes permanently undecryptable.
     */
    fun signOut() {
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            tokens.clear()
            clients.clearCache()
            deviceIdentity.clear()
            purger.purge()
            _state.update { it.copy(isSigningOut = false, signedOut = true) }
        }
    }
}

/** Supplied by the app module so the feature does not read BuildConfig directly. */
data class AppVersionName(val value: String)
