package com.asktrix.agent.feature.client

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.designsystem.component.StatusTone

/** Domain status to visual tone. Kept in the feature so the design system stays data-agnostic. */
fun ProcessStatus.tone(): StatusTone = when (this) {
    ProcessStatus.COMPLETED,
    ProcessStatus.PAYMENT_RECEIVED,
    ProcessStatus.DOCUMENTS_RECEIVED,
    -> StatusTone.POSITIVE

    ProcessStatus.DOCUMENTS_PENDING,
    ProcessStatus.PAYMENT_PENDING,
    -> StatusTone.WARNING

    ProcessStatus.CLIENT_NOT_RESPONDING -> StatusTone.NEGATIVE

    ProcessStatus.WAITING_GOVERNMENT_APPROVAL,
    ProcessStatus.CALLBACK_SCHEDULED,
    -> StatusTone.PENDING

    ProcessStatus.NEW -> StatusTone.NEUTRAL
}

/**
 * Failure copy for the detail screen.
 *
 * Offline is silent here: the outbox has the action, the cached record is on screen, and there is
 * nothing for the employee to do about it.
 */
fun AsktrixError.toDetailMessage(): String? = when (this) {
    is AsktrixError.Offline -> null
    is AsktrixError.Timeout -> "Could not reach the CRM. Showing saved data."
    is AsktrixError.ServerUnavailable -> "The CRM is unavailable. Your changes are queued."
    is AsktrixError.Unauthenticated -> "Your session expired. Sign in again."
    is AsktrixError.Forbidden -> "This client is no longer assigned to you."
    is AsktrixError.NotFound -> "This client is no longer available."
    is AsktrixError.Validation ->
        fieldErrors.values.firstOrNull() ?: "Please check the details and try again."
    is AsktrixError.CallNotPlaced -> providerReason ?: "The call could not be placed."
    else -> "Something went wrong. Try again."
}
