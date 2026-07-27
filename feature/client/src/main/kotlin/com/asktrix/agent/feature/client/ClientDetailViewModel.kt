package com.asktrix.agent.feature.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.data.model.CallSession
import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.data.model.TimelineEntry
import com.asktrix.agent.core.data.repository.CallRepository
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

data class ClientDetailUiState(
    val client: Client? = null,
    val timeline: List<TimelineEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isOnline: Boolean = true,
    val activeCall: CallSession? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

/** The six §13 quick actions, in the order the requirements list them. */
val QUICK_STATUSES: List<ProcessStatus> = listOf(
    ProcessStatus.DOCUMENTS_RECEIVED,
    ProcessStatus.CLIENT_NOT_RESPONDING,
    ProcessStatus.PAYMENT_RECEIVED,
    ProcessStatus.WAITING_GOVERNMENT_APPROVAL,
    ProcessStatus.COMPLETED,
    ProcessStatus.CALLBACK_SCHEDULED,
)

@HiltViewModel
class ClientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clients: ClientRepository,
    private val calls: CallRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"]) {
        "ClientDetail requires a clientId argument"
    }

    private val loading = MutableStateFlow(true)
    private val activeCall = MutableStateFlow<CallSession?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ClientDetailUiState> = combine(
        clients.observeClient(clientId),
        clients.observeTimeline(clientId),
        loading,
        connectivity.isOnline,
        combine(activeCall, message, error) { call, msg, err -> Triple(call, msg, err) },
    ) { client, timeline, isLoading, isOnline, (call, msg, err) ->
        ClientDetailUiState(
            client = client,
            timeline = timeline,
            isLoading = isLoading && client == null,
            isOnline = isOnline,
            activeCall = call,
            message = msg,
            errorMessage = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = ClientDetailUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            when (val result = clients.refreshClient(clientId)) {
                is AsktrixResult.Success -> error.value = null
                is AsktrixResult.Failure -> error.value = result.error.toDetailMessage()
            }
            clients.refreshTimeline(clientId)
            loading.value = false
        }
    }

    /**
     * Applies a quick status update (§13).
     *
     * Enqueued in the outbox, so it works offline and the UI can confirm immediately. The version the
     * user was looking at is sent as `expectedVersion`, so a concurrent edit elsewhere surfaces as a
     * conflict rather than silently overwriting someone else's change.
     */
    fun applyStatus(status: ProcessStatus, followUpAt: Instant? = null, note: String? = null) {
        val current = state.value.client ?: return
        viewModelScope.launch {
            when (
                val result = clients.updateStatus(
                    clientId = clientId,
                    status = status,
                    note = note,
                    followUpAt = followUpAt,
                    expectedVersion = current.version,
                )
            ) {
                is AsktrixResult.Success -> {
                    message.value = "${status.label} — saved"
                    refresh()
                }
                is AsktrixResult.Failure -> error.value = result.error.toDetailMessage()
            }
        }
    }

    /**
     * Starts a click-to-call (§5).
     *
     * The request carries only the client id. The provider dials the agent's own handset first, then
     * bridges the customer — so no customer number ever reaches this device.
     */
    fun startCall() {
        if (activeCall.value?.state?.isActive == true) return
        viewModelScope.launch {
            when (val result = calls.placeCall(clientId)) {
                is AsktrixResult.Success -> {
                    activeCall.value = result.data
                    calls.observeCall(result.data.callSessionId).collect { session ->
                        activeCall.value = session
                        if (session.state.isTerminal) {
                            refresh()
                        }
                    }
                }
                is AsktrixResult.Failure -> error.value = result.error.toDetailMessage()
            }
        }
    }

    fun dismissCall() {
        if (activeCall.value?.state?.isTerminal != false) activeCall.value = null
    }

    fun consumeMessage() {
        message.value = null
        error.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
    }
}
