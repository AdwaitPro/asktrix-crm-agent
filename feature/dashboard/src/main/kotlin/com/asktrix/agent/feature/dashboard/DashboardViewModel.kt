package com.asktrix.agent.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.data.repository.ClientRepository
import com.asktrix.agent.core.sync.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The §12 dashboard filters: everything, work that is waiting, and follow-ups that are due. */
enum class DashboardFilter(val label: String) {
    ALL("All"),
    PENDING("Pending work"),
    FOLLOW_UP("Follow-ups due"),
}

data class DashboardUiState(
    val clients: List<Client> = emptyList(),
    val filter: DashboardFilter = DashboardFilter.ALL,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val pendingSyncCount: Int = 0,
    val errorMessage: String? = null,
    val hasLoadedOnce: Boolean = false,
) {
    val visibleClients: List<Client> = when (filter) {
        DashboardFilter.ALL -> clients
        DashboardFilter.PENDING -> clients.filter {
            it.documentsPending > 0 ||
                it.processStatus in PENDING_STATUSES
        }
        DashboardFilter.FOLLOW_UP -> clients.filter { it.isFollowUpDue() }
    }

    val followUpDueCount: Int = clients.count { it.isFollowUpDue() }

    val pendingWorkCount: Int =
        clients.count { it.documentsPending > 0 || it.processStatus in PENDING_STATUSES }

    private companion object {
        /**
         * Extracted rather than inlined: `followUpAt` is a public property from another module, so
         * Kotlin cannot smart-cast it after a null check. A local binding makes the intent explicit
         * and keeps the null handling in one place.
         */
        private fun Client.isFollowUpDue(now: Instant = Instant.now()): Boolean {
            val due = followUpAt ?: return false
            return due <= now
        }

        val PENDING_STATUSES = setOf(
            ProcessStatus.DOCUMENTS_PENDING,
            ProcessStatus.PAYMENT_PENDING,
            ProcessStatus.CLIENT_NOT_RESPONDING,
            ProcessStatus.NEW,
        )
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ClientRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val filter = MutableStateFlow(DashboardFilter.ALL)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val loadedOnce = MutableStateFlow(false)

    val state: StateFlow<DashboardUiState> = combine(
        repository.observeClients(),
        filter,
        refreshing,
        connectivity.isOnline,
        error,
    ) { clients, selectedFilter, isRefreshing, isOnline, errorMessage ->
        DashboardUiState(
            clients = clients,
            filter = selectedFilter,
            isRefreshing = isRefreshing,
            isOnline = isOnline,
            errorMessage = errorMessage,
            hasLoadedOnce = loadedOnce.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = DashboardUiState(),
    )

    init {
        refresh()
    }

    fun onFilterChange(value: DashboardFilter) {
        filter.value = value
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        error.value = null

        viewModelScope.launch {
            when (val result = repository.refreshClients()) {
                is AsktrixResult.Success -> error.value = null
                is AsktrixResult.Failure -> error.value = result.error.toDashboardMessage()
            }
            loadedOnce.value = true
            refreshing.value = false
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
