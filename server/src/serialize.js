'use strict';
const { maskPhone, maskEmail } = require('./mask');
const { permissionsFor, statusesFor } = require('./roles');

/**
 * Row -> API projection.
 *
 * Every function here is a deliberate allow-list: it names the fields that go to the device rather
 * than spreading the row. `SELECT *` plus object spread is exactly how `phone_full` would one day
 * end up on a handset, so it is never done.
 */

const iso = (d) => (d ? new Date(d).toISOString() : undefined);

function clientSummary(row) {
  return {
    clientId: row.client_id,
    name: row.name,
    serviceId: row.service_id || undefined,
    processStatus: row.process_status,
    paymentStatus: row.payment_status,
    documentsPending: row.documents_pending,
    followUpAt: iso(row.follow_up_at),
    lastInteractionAt: iso(row.last_interaction_at),
    version: row.version,
  };
}

function clientDetail(row, { remarks = [], documents = [], cacheTtlSeconds = 3600 } = {}) {
  return {
    ...clientSummary(row),
    contact: {
      // The only representation of contact details that exists on the wire.
      phoneMasked: maskPhone(row.phone_full),
      emailMasked: maskEmail(row.email_full),
      callable: Boolean(row.phone_full) && row.process_status !== 'COMPLETED',
    },
    governmentStatus: row.government_status,
    assignedEmployeeId: row.assigned_employee || undefined,
    internalRemarks: remarks.map(remark),
    documents: documents.map(documentRef),
    cacheTtlSeconds,
  };
}

function remark(row) {
  return {
    remarkId: row.remark_id,
    body: row.body,
    authorName: row.author_name,
    createdAt: iso(row.created_at),
  };
}

function documentRef(row) {
  return {
    documentId: row.document_id,
    kind: row.kind,
    status: row.status,
    receivedAt: iso(row.received_at),
  };
}

function timelineEntry(row) {
  return {
    entryId: row.entry_id,
    kind: row.kind,
    occurredAt: iso(row.occurred_at),
    summary: row.summary,
    actorName: row.actor_name || undefined,
    callRecordId: row.call_record_id || undefined,
  };
}

function callSession(row) {
  return {
    callSessionId: row.call_session_id,
    clientId: row.client_id,
    state: row.state,
    requestedAt: iso(row.requested_at),
    connectedAt: iso(row.connected_at),
    endedAt: iso(row.ended_at),
    durationSeconds: row.duration_seconds ?? undefined,
    failureReason: row.failure_reason || undefined,
  };
}

function callRecord(row) {
  return {
    callRecordId: row.call_record_id,
    callSessionId: row.call_session_id || undefined,
    clientId: row.client_id,
    clientName: row.client_name || undefined,
    direction: row.direction,
    state: row.state,
    startedAt: iso(row.started_at),
    durationSeconds: row.duration_seconds,
    recordingAvailable: row.recording_available,
    // Note: recording_uri is intentionally NOT projected. The device is told a recording exists;
    // it never receives a link to the audio (§6).
  };
}

function attendanceRecord(row) {
  return {
    attendanceId: row.attendance_id,
    kind: row.kind,
    occurredAt: iso(row.occurred_at),
    recordedAt: iso(row.recorded_at),
    location: {
      latitude: row.latitude,
      longitude: row.longitude,
      accuracyMetres: row.accuracy_metres,
    },
    photoUploaded: row.photo_uploaded,
  };
}

function employee(row) {
  // Derived from the role rather than read from the row, so a permission change takes effect
  // everywhere at once instead of needing a data migration.
  const permissions = permissionsFor(row.role);
  return {
    employeeId: row.employee_id,
    employeeCode: row.employee_code,
    displayName: row.display_name,
    role: row.role,
    permissions,
    /** The §13 quick actions this role may use. The app renders exactly these. */
    allowedStatuses: statusesFor(row.role),
  };
}

module.exports = {
  clientSummary,
  clientDetail,
  remark,
  documentRef,
  timelineEntry,
  callSession,
  callRecord,
  attendanceRecord,
  employee,
};
