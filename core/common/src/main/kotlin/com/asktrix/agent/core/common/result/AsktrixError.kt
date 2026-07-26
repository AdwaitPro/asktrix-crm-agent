package com.asktrix.agent.core.common.result

/**
 * Every failure this app can produce, modelled explicitly.
 *
 * Deliberate design choices:
 * - No error carries a customer phone number, email, or KYC content. Error messages are shown in the
 *   UI and written to diagnostics, so they are a PII leak path (§4). The `debugContext` field is for
 *   non-sensitive technical detail only.
 * - [Retryable] failures are safe for the sync engine to retry; [Permanent] ones must surface to the
 *   user instead of retrying forever (§9, §23).
 */
sealed interface AsktrixError {

    /** Non-sensitive technical context for diagnostics. Never put PII here. */
    val debugContext: String?

    /** Transient conditions the outbox should retry with backoff. */
    sealed interface Retryable : AsktrixError

    /** Conditions that will not improve on retry and must be surfaced. */
    sealed interface Permanent : AsktrixError

    // --- Connectivity (§9) -------------------------------------------------------------------

    /** The device has no usable network. Work stays queued in the outbox. */
    data class Offline(override val debugContext: String? = null) : Retryable

    /** The request reached the network but did not complete in time. */
    data class Timeout(override val debugContext: String? = null) : Retryable

    /** 5xx from the CRM, or an unreachable host that resolved. */
    data class ServerUnavailable(
        val statusCode: Int?,
        override val debugContext: String? = null,
    ) : Retryable

    // --- Authentication and authorisation ----------------------------------------------------

    /** The access token is missing, expired, or was rejected and refresh did not recover it. */
    data class Unauthenticated(override val debugContext: String? = null) : Permanent

    /** Authenticated, but this employee is not permitted to see or act on this resource (RBAC). */
    data class Forbidden(override val debugContext: String? = null) : Permanent

    /** This device is not the device bound to this session. */
    data class DeviceNotBound(override val debugContext: String? = null) : Permanent

    // --- Contract and validation -------------------------------------------------------------

    /** The server's payload did not match the agreed contract. A bug on one side, never a retry. */
    data class MalformedResponse(override val debugContext: String? = null) : Permanent

    /** The server rejected our input. [fieldErrors] maps field name to a non-sensitive reason. */
    data class Validation(
        val fieldErrors: Map<String, String>,
        override val debugContext: String? = null,
    ) : Permanent

    /** The requested resource does not exist, or is not assigned to this employee. */
    data class NotFound(override val debugContext: String? = null) : Permanent

    // --- Local storage and integrity ---------------------------------------------------------

    /** The encrypted cache could not be opened or written. */
    data class StorageFailure(override val debugContext: String? = null) : Permanent

    /**
     * The device failed an integrity check (§14–§20). The caller must purge the local cache and end
     * the session; this is never retried.
     */
    data class IntegrityFailure(
        val reason: IntegrityFailureReason,
        override val debugContext: String? = null,
    ) : Permanent

    /** A required runtime permission has not been granted. */
    data class PermissionDenied(
        val permission: String,
        val permanentlyDenied: Boolean,
        override val debugContext: String? = null,
    ) : Permanent

    // --- Telephony (§5, §6, §7) --------------------------------------------------------------

    /** The CRM accepted the call request but the provider could not place it. */
    data class CallNotPlaced(
        val providerReason: String?,
        override val debugContext: String? = null,
    ) : Permanent

    // --- Fallback ----------------------------------------------------------------------------

    /**
     * An unclassified failure. Every occurrence should be triaged into a specific case above; this
     * exists so that an unexpected condition degrades gracefully instead of crashing.
     */
    data class Unexpected(override val debugContext: String? = null) : Permanent
}

enum class IntegrityFailureReason {
    ROOTED,
    EMULATOR,
    DEBUGGER_ATTACHED,
    TAMPERED_SIGNATURE,
    ATTESTATION_REJECTED,
}
