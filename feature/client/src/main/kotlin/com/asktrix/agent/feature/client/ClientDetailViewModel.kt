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
import com.asktrix.agent.core.common.session.EmployeeStore
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
    /** Set when a data call is ready to join; the screen opens the in-call view. */
    /** The §13 actions this role may apply. Empty until the session loads. */
    val availableStatuses: List<ProcessStatus> = emptyList(),
    /** Whether this role may place calls at all (§2). */
    val canPlaceCalls: Boolean = false,
    val roleLabel: String = "",
    val launchCallUrl: String? = null,
    /** The one-time link to send the customer so they can join. */
    val customerCallLink: String? = null,
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
    private val employees: EmployeeStore,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val clientId: String = checkNotNull(savedStateHandle["clientId"]) {
        "ClientDetail requires a clientId argument"
    }

    private val loading = MutableStateFlow(true)
    private val activeCall = MutableStateFlow<CallSession?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val launchCall = MutableStateFlow<String?>(null)
    private val role = MutableStateFlow<com.asktrix.agent.core.common.session.SignedInEmployee?>(null)
    private val customerLink = MutableStateFlow<String?>(null)

    val state: StateFlow<ClientDetailUiState> = combine(
        clients.observeClient(clientId),
        clients.observeTimeline(clientId),
        loading,
        connectivity.isOnline,
        combine(activeCall, message, error, launchCall, customerLink) { call, msg, err, launch, link ->
            listOf(call, msg, err, launch, link)
        },
    ) { client, timeline, isLoading, isOnline, extras ->
        // combine() caps out at five flows, so the tail is bundled into one list. Named indices
        // keep the unpacking readable rather than a run of bare integers.
        val call = extras[EXTRA_CALL] as CallSession?
        val msg = extras[EXTRA_MESSAGE] as String?
        val err = extras[EXTRA_ERROR] as String?
        val launch = extras[EXTRA_LAUNCH] as String?
        val link = extras[EXTRA_LINK] as String?
        ClientDetailUiState(
            client = client,
            timeline = timeline,
            isLoading = isLoading && client == null,
            isOnline = isOnline,
            activeCall = call,
            message = msg,
            errorMessage = err,
            launchCallUrl = launch,
            customerCallLink = link,
            availableStatuses = role.value?.allowedStatuses
                ?.map(ProcessStatus::from)
                ?.filter { it in QUICK_STATUSES }
                ?: emptyList(),
            canPlaceCalls = role.value?.can("calls:place") ?: false,
            roleLabel = role.value?.roleLabel.orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = ClientDetailUiState(),
    )

    init {
        refresh()
        viewModelScope.launch { role.value = employees.currentEmployee() }
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
                    message.value = "${status.label} - saved"
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
     * bridges the customer - so no customer number ever reaches this device.
     */
    fun startCall() {
        if (activeCall.value?.state?.isActive == true) return
        viewModelScope.launch {
            when (val result = calls.placeCall(clientId)) {
                is AsktrixResult.Success -> {
                    activeCall.value = result.data

                    // A data call: open the in-call screen and surface the customer's link so the
                    // agent can send it. No number is dialled and none is shown (ADR-0006).
                    result.data.rtc?.let { rtc ->
                        launchCall.value = rtc.agentUrl
                        customerLink.value = rtc.customerUrl
                    }

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

    /** Called once the in-call screen has been opened, so it is not opened twice. */
    fun consumeLaunch() {
        launchCall.value = null
    }

    fun dismissCall() {
        customerLink.value = null
        if (activeCall.value?.state?.isTerminal != false) activeCall.value = null
    }

    fun consumeMessage() {
        message.value = null
        error.value = null
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        const val EXTRA_CALL = 0
        const val EXTRA_MESSAGE = 1
        const val EXTRA_ERROR = 2
        const val EXTRA_LAUNCH = 3
        const val EXTRA_LINK = 4
    }
}
