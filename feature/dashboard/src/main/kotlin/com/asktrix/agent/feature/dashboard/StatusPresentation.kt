package com.asktrix.agent.feature.dashboard

import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.designsystem.component.StatusTone

/**
 * Maps domain status onto a visual tone.
 *
 * Lives in the feature, not the design system: `:core:designsystem` must not depend on the data
 * layer, and what counts as "warning" is a product judgement rather than a styling one.
 */
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
 * Failure copy for the dashboard.
 *
 * Being offline is deliberately not surfaced as an error here — the offline banner already says so,
 * and the cached list is still perfectly usable.
 */
fun AsktrixError.toDashboardMessage(): String? = when (this) {
    is AsktrixError.Offline -> null
    is AsktrixError.Timeout -> "Could not reach the CRM. Showing saved data."
    is AsktrixError.ServerUnavailable -> "The CRM is unavailable. Showing saved data."
    is AsktrixError.Unauthenticated -> "Your session expired. Sign in again."
    is AsktrixError.Forbidden -> "You do not have access to these clients."
    else -> "Could not refresh. Showing saved data."
}
