package com.asktrix.agent.core.data.mapper

import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.ClientDocument
import com.asktrix.agent.core.data.model.DocumentStatus
import com.asktrix.agent.core.data.model.GovernmentStatus
import com.asktrix.agent.core.data.model.MaskedContact
import com.asktrix.agent.core.data.model.PaymentStatus
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.data.model.Remark
import com.asktrix.agent.core.data.model.TimelineEntry
import com.asktrix.agent.core.data.model.TimelineKind
import com.asktrix.agent.core.database.entity.CachedClientEntity
import com.asktrix.agent.core.database.entity.CachedTimelineEntity
import com.asktrix.agent.core.network.dto.ClientDetailDto
import com.asktrix.agent.core.network.dto.ClientSummaryDto
import com.asktrix.agent.core.network.dto.TimelineEntryDto
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Conversions between wire, cache and domain.
 *
 * Timestamp parsing never throws: a malformed date from the server degrades to null rather than
 * crashing a screen the employee is relying on mid-visit.
 */

fun String?.toInstantOrNull(): Instant? = this?.let {
    try {
        Instant.parse(it)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun ClientSummaryDto.toEntity(now: Long, ttlSeconds: Int): CachedClientEntity = CachedClientEntity(
    clientId = clientId,
    name = name,
    serviceId = serviceId,
    processStatus = processStatus,
    paymentStatus = paymentStatus,
    // A summary carries no government status; a later detail fetch fills it in.
    governmentStatus = GovernmentStatus.NOT_APPLICABLE.name,
    documentsPending = documentsPending,
    followUpAtMillis = followUpAt.toInstantOrNull()?.toEpochMilli(),
    lastInteractionAtMillis = lastInteractionAt.toInstantOrNull()?.toEpochMilli(),
    version = version,
    // A list response has no contact block. Placeholders are still masked shapes, never real values.
    phoneMasked = "",
    emailMasked = "",
    callable = false,
    cachedAtMillis = now,
    expiresAtMillis = now + ttlSeconds * MILLIS_PER_SECOND,
)

fun ClientDetailDto.toEntity(now: Long): CachedClientEntity = CachedClientEntity(
    clientId = clientId,
    name = name,
    serviceId = serviceId,
    processStatus = processStatus,
    paymentStatus = paymentStatus,
    governmentStatus = governmentStatus,
    documentsPending = documentsPending,
    followUpAtMillis = followUpAt.toInstantOrNull()?.toEpochMilli(),
    lastInteractionAtMillis = lastInteractionAt.toInstantOrNull()?.toEpochMilli(),
    version = version,
    phoneMasked = contact.phoneMasked,
    emailMasked = contact.emailMasked,
    callable = contact.callable,
    cachedAtMillis = now,
    expiresAtMillis = now + cacheTtlSeconds * MILLIS_PER_SECOND,
)

fun CachedClientEntity.toDomain(hasPendingChanges: Boolean = false): Client = Client(
    clientId = clientId,
    name = name,
    serviceId = serviceId,
    processStatus = ProcessStatus.from(processStatus),
    paymentStatus = PaymentStatus.from(paymentStatus),
    governmentStatus = GovernmentStatus.from(governmentStatus),
    documentsPending = documentsPending,
    followUpAt = followUpAtMillis?.let(Instant::ofEpochMilli),
    lastInteractionAt = lastInteractionAtMillis?.let(Instant::ofEpochMilli),
    version = version,
    contact = MaskedContact(phoneMasked, emailMasked, callable),
    hasPendingChanges = hasPendingChanges,
)

fun ClientDetailDto.toDomain(hasPendingChanges: Boolean = false): Client = Client(
    clientId = clientId,
    name = name,
    serviceId = serviceId,
    processStatus = ProcessStatus.from(processStatus),
    paymentStatus = PaymentStatus.from(paymentStatus),
    governmentStatus = GovernmentStatus.from(governmentStatus),
    documentsPending = documentsPending,
    followUpAt = followUpAt.toInstantOrNull(),
    lastInteractionAt = lastInteractionAt.toInstantOrNull(),
    version = version,
    contact = MaskedContact(contact.phoneMasked, contact.emailMasked, contact.callable),
    remarks = internalRemarks.map {
        Remark(it.remarkId, it.body, it.authorName, it.createdAt.toInstantOrNull() ?: Instant.EPOCH)
    },
    documents = documents.map {
        ClientDocument(it.documentId, it.kind, DocumentStatus.from(it.status), it.receivedAt.toInstantOrNull())
    },
    hasPendingChanges = hasPendingChanges,
)

fun TimelineEntryDto.toEntity(now: Long, ttlSeconds: Int = DEFAULT_TTL_SECONDS): CachedTimelineEntity =
    CachedTimelineEntity(
        entryId = entryId,
        clientId = "",
        kind = kind,
        summary = summary,
        actorName = actorName,
        callRecordId = callRecordId,
        occurredAtMillis = occurredAt.toInstantOrNull()?.toEpochMilli() ?: now,
        expiresAtMillis = now + ttlSeconds * MILLIS_PER_SECOND,
    )

fun CachedTimelineEntity.toDomain(): TimelineEntry = TimelineEntry(
    entryId = entryId,
    kind = TimelineKind.from(kind),
    summary = summary,
    actorName = actorName,
    callRecordId = callRecordId,
    occurredAt = Instant.ofEpochMilli(occurredAtMillis),
)

private const val MILLIS_PER_SECOND = 1000L
private const val DEFAULT_TTL_SECONDS = 3600
