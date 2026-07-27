package com.asktrix.agent.core.data.model

import java.time.Instant

/**
 * The domain model the UI renders.
 *
 * Separate from the DTO and the Room entity on purpose: the wire format and the cache schema each
 * change for their own reasons, and the UI should not break when either does. It also means the
 * privacy invariant is expressed three times independently — none of these three layers has a field
 * for an unmasked phone number or email.
 */
data class Client(
    val clientId: String,
    val name: String,
    val serviceId: String?,
    val processStatus: ProcessStatus,
    val paymentStatus: PaymentStatus,
    val governmentStatus: GovernmentStatus,
    val documentsPending: Int,
    val followUpAt: Instant?,
    val lastInteractionAt: Instant?,
    val version: Int,
    val contact: MaskedContact,
    val remarks: List<Remark> = emptyList(),
    val documents: List<ClientDocument> = emptyList(),
    /** True while an edit to this client is still queued in the outbox (§9, §23). */
    val hasPendingChanges: Boolean = false,
)

/**
 * Customer contact details, masked at the server.
 *
 * There is no unmasked variant, and there must never be one. See ADR-0003.
 */
data class MaskedContact(
    val phoneMasked: String,
    val emailMasked: String,
    val callable: Boolean,
)

data class Remark(
    val remarkId: String,
    val body: String,
    val authorName: String,
    val createdAt: Instant,
)

data class ClientDocument(
    val documentId: String,
    val kind: String,
    val status: DocumentStatus,
    val receivedAt: Instant?,
)

/** The §13 quick-action statuses, plus the intermediate states the CRM reports. */
enum class ProcessStatus(val label: String) {
    NEW("New"),
    DOCUMENTS_PENDING("Documents pending"),
    DOCUMENTS_RECEIVED("Documents received"),
    CLIENT_NOT_RESPONDING("Not responding"),
    PAYMENT_PENDING("Payment pending"),
    PAYMENT_RECEIVED("Payment received"),
    WAITING_GOVERNMENT_APPROVAL("Awaiting government"),
    CALLBACK_SCHEDULED("Callback scheduled"),
    COMPLETED("Completed"),
    ;

    companion object {
        fun from(raw: String): ProcessStatus =
            entries.firstOrNull { it.name == raw } ?: NEW
    }
}

enum class PaymentStatus(val label: String) {
    NOT_DUE("Not due"),
    PENDING("Pending"),
    PARTIAL("Partial"),
    RECEIVED("Received"),
    REFUNDED("Refunded"),
    ;

    companion object {
        fun from(raw: String): PaymentStatus = entries.firstOrNull { it.name == raw } ?: NOT_DUE
    }
}

enum class GovernmentStatus(val label: String) {
    NOT_APPLICABLE("Not applicable"),
    NOT_SUBMITTED("Not submitted"),
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under review"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    ;

    companion object {
        fun from(raw: String): GovernmentStatus =
            entries.firstOrNull { it.name == raw } ?: NOT_APPLICABLE
    }
}

enum class DocumentStatus(val label: String) {
    PENDING("Pending"),
    RECEIVED("Received"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    ;

    companion object {
        fun from(raw: String): DocumentStatus = entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

data class TimelineEntry(
    val entryId: String,
    val kind: TimelineKind,
    val summary: String,
    val actorName: String?,
    val callRecordId: String?,
    val occurredAt: Instant,
)

enum class TimelineKind {
    CALL, REMARK, STATUS_CHANGE, PAYMENT, DOCUMENT, FOLLOW_UP, EMAIL,
    ;

    companion object {
        fun from(raw: String): TimelineKind = entries.firstOrNull { it.name == raw } ?: STATUS_CHANGE
    }
}
