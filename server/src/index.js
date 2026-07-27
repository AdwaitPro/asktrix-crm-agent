'use strict';
require('dotenv').config();

const express = require('express');
const crypto = require('crypto');
const { query, tx } = require('./db');
const { findLeaks } = require('./mask');
const S = require('./serialize');
const {
  hashPassword, verifyPassword, issueAccessToken, requireAuth, newId, TTL,
} = require('./auth');

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.raw({ type: 'image/jpeg', limit: '4mb' }));

// ---------------------------------------------------------------------------------------------
// Privacy tripwire.
//
// Every JSON response is scanned for anything resembling a full phone number or email before it
// leaves the process. The OpenAPI schema already makes a leak impossible by construction; this
// catches the case where a future change adds a field and forgets ADR-0003. In development it fails
// loudly rather than silently shipping PII to a handset.
// ---------------------------------------------------------------------------------------------
app.use((req, res, next) => {
  const originalJson = res.json.bind(res);
  res.json = (body) => {
    const leaks = findLeaks(body);
    if (leaks.length > 0) {
      console.error(`[PRIVACY] ${req.method} ${req.path} would leak unmasked PII at: ${leaks.join(', ')}`);
      return originalJson.call(res, {
        code: 'SERVER_ERROR',
        message: 'Response blocked: it contained unmasked contact details.',
      });
    }
    return originalJson(body);
  };
  next();
});

const fail = (res, status, code, message, extra = {}) =>
  res.status(status).json({ code, message, ...extra });

/**
 * Idempotency (§9, §23). A replayed key returns the stored result instead of repeating the action,
 * which is what makes the offline outbox safe to retry without duplicating a payment or a call.
 */
async function idempotent(req, res, handler) {
  const key = req.get('idempotency-key');
  if (!key) {
    return fail(res, 400, 'VALIDATION_FAILED', 'Idempotency-Key header is required.', {
      fieldErrors: { 'Idempotency-Key': 'missing' },
    });
  }
  const existing = await query(
    'SELECT status_code, response_body FROM idempotency_keys WHERE key = $1 AND employee_id = $2',
    [key, req.employee.employee_id],
  );
  if (existing.rows.length > 0) {
    return res.status(existing.rows[0].status_code).json(existing.rows[0].response_body);
  }
  const result = await handler();
  await query(
    `INSERT INTO idempotency_keys (key, employee_id, endpoint, status_code, response_body)
     VALUES ($1, $2, $3, $4, $5) ON CONFLICT DO NOTHING`,
    [key, req.employee.employee_id, `${req.method} ${req.path}`, result.status, JSON.stringify(result.body)],
  );
  return res.status(result.status).json(result.body);
}

const asyncRoute = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

// ============================================================================== Health / meta ===
app.get('/health', asyncRoute(async (_req, res) => {
  const { rows } = await query('SELECT now() AS now');
  res.json({ status: 'ok', serverTime: rows[0].now.toISOString() });
}));

// ======================================================================================= Auth ===
app.post('/auth/login', asyncRoute(async (req, res) => {
  const { employeeCode, password, device } = req.body || {};
  if (!employeeCode || !password || !device?.deviceId) {
    return fail(res, 422, 'VALIDATION_FAILED', 'employeeCode, password and device.deviceId are required.');
  }

  const { rows } = await query(
    'SELECT * FROM employees WHERE employee_code = $1 AND active = TRUE',
    [employeeCode],
  );
  const emp = rows[0];
  // Same response for unknown user and wrong password — do not confirm which employee codes exist.
  if (!emp || !verifyPassword(password, emp.password_hash, emp.password_salt)) {
    return fail(res, 401, 'UNAUTHENTICATED', 'Incorrect employee code or password.');
  }

  await query(
    `INSERT INTO devices (device_id, employee_id, manufacturer, model, os_version, app_version, attestation)
     VALUES ($1,$2,$3,$4,$5,$6,$7)
     ON CONFLICT (device_id) DO UPDATE SET
       employee_id = EXCLUDED.employee_id, model = EXCLUDED.model,
       os_version = EXCLUDED.os_version, app_version = EXCLUDED.app_version,
       last_seen_at = now()`,
    [device.deviceId, emp.employee_id, device.manufacturer, device.model,
      device.osVersion, device.appVersion, device.attestationStatement || null],
  );

  const familyId = newId('fam');
  const refreshToken = newId('rt');
  await query(
    `INSERT INTO refresh_tokens (token_id, family_id, employee_id, device_id, expires_at)
     VALUES ($1,$2,$3,$4, now() + interval '30 days')`,
    [refreshToken, familyId, emp.employee_id, device.deviceId],
  );

  res.json({
    accessToken: issueAccessToken(emp, device.deviceId),
    refreshToken,
    expiresInSeconds: TTL,
    employee: S.employee(emp),
  });
}));

app.post('/auth/refresh', asyncRoute(async (req, res) => {
  const { refreshToken, deviceId } = req.body || {};
  if (!refreshToken || !deviceId) {
    return fail(res, 422, 'VALIDATION_FAILED', 'refreshToken and deviceId are required.');
  }
  const { rows } = await query('SELECT * FROM refresh_tokens WHERE token_id = $1', [refreshToken]);
  const token = rows[0];
  if (!token || token.revoked || new Date(token.expires_at) < new Date() || token.device_id !== deviceId) {
    return fail(res, 401, 'UNAUTHENTICATED', 'Session expired. Sign in again.');
  }
  if (token.used) {
    // A used refresh token being replayed means it was stolen. Burn the whole family.
    await query('UPDATE refresh_tokens SET revoked = TRUE WHERE family_id = $1', [token.family_id]);
    return fail(res, 401, 'UNAUTHENTICATED', 'Session revoked. Sign in again.');
  }

  const emp = (await query('SELECT * FROM employees WHERE employee_id = $1', [token.employee_id])).rows[0];
  if (!emp || !emp.active) return fail(res, 401, 'UNAUTHENTICATED', 'Account is no longer active.');

  const next = newId('rt');
  await tx(async (c) => {
    await c.query('UPDATE refresh_tokens SET used = TRUE WHERE token_id = $1', [refreshToken]);
    await c.query(
      `INSERT INTO refresh_tokens (token_id, family_id, employee_id, device_id, expires_at)
       VALUES ($1,$2,$3,$4, now() + interval '30 days')`,
      [next, token.family_id, emp.employee_id, deviceId],
    );
  });

  res.json({
    accessToken: issueAccessToken(emp, deviceId),
    refreshToken: next,
    expiresInSeconds: TTL,
    employee: S.employee(emp),
  });
}));

app.post('/auth/logout', requireAuth, asyncRoute(async (req, res) => {
  await query(
    `UPDATE refresh_tokens SET revoked = TRUE
     WHERE employee_id = $1 AND device_id = $2`,
    [req.employee.employee_id, req.deviceId],
  );
  res.status(204).end();
}));

app.get('/auth/session', requireAuth, (req, res) => res.json(S.employee(req.employee)));

// ==================================================================================== Clients ===
app.get('/clients', requireAuth, asyncRoute(async (req, res) => {
  const limit = Math.min(parseInt(req.query.limit || '25', 10), 100);
  const filters = ['assigned_employee = $1'];
  const params = [req.employee.employee_id];

  if (req.query.status) { params.push(req.query.status); filters.push(`process_status = $${params.length}`); }
  if (req.query.needsFollowUp === 'true') filters.push('follow_up_at IS NOT NULL AND follow_up_at <= now()');
  if (req.query.query) {
    params.push(`%${req.query.query}%`);
    // Search covers name and id only — never contact details, which the server holds but never exposes.
    filters.push(`(name ILIKE $${params.length} OR client_id ILIKE $${params.length})`);
  }
  params.push(limit);

  const { rows } = await query(
    `SELECT * FROM clients WHERE ${filters.join(' AND ')}
     ORDER BY COALESCE(follow_up_at, last_interaction_at, created_at) DESC
     LIMIT $${params.length}`,
    params,
  );
  const now = (await query('SELECT now() AS now')).rows[0].now;
  res.json({ items: rows.map(S.clientSummary), serverTime: now.toISOString() });
}));

async function loadOwnedClient(req, res) {
  const { rows } = await query('SELECT * FROM clients WHERE client_id = $1', [req.params.clientId]);
  if (rows.length === 0) {
    fail(res, 404, 'NOT_FOUND', 'No such client.');
    return null;
  }
  // Authorisation is decided here, on the server. The app filtering its own list is not a control.
  if (rows[0].assigned_employee !== req.employee.employee_id) {
    fail(res, 403, 'FORBIDDEN', 'This client is not assigned to you.');
    return null;
  }
  return rows[0];
}

app.get('/clients/:clientId', requireAuth, asyncRoute(async (req, res) => {
  const client = await loadOwnedClient(req, res);
  if (!client) return;
  const [remarks, documents] = await Promise.all([
    query('SELECT * FROM remarks WHERE client_id = $1 ORDER BY created_at DESC LIMIT 20', [client.client_id]),
    query('SELECT * FROM documents WHERE client_id = $1 ORDER BY kind', [client.client_id]),
  ]);
  res.json(S.clientDetail(client, { remarks: remarks.rows, documents: documents.rows }));
}));

const STATUS_SUMMARY = {
  DOCUMENTS_RECEIVED: 'Documents received',
  CLIENT_NOT_RESPONDING: 'Client not responding',
  PAYMENT_RECEIVED: 'Payment received',
  WAITING_GOVERNMENT_APPROVAL: 'Waiting for government approval',
  COMPLETED: 'Marked completed',
  CALLBACK_SCHEDULED: 'Callback scheduled',
  DOCUMENTS_PENDING: 'Documents pending',
  PAYMENT_PENDING: 'Payment pending',
  NEW: 'Reopened',
};

app.post('/clients/:clientId/status', requireAuth, asyncRoute(async (req, res) => {
  const client = await loadOwnedClient(req, res);
  if (!client) return;

  const { status, note, followUpAt, occurredAt, expectedVersion } = req.body || {};
  if (!status || !STATUS_SUMMARY[status]) {
    return fail(res, 422, 'VALIDATION_FAILED', 'Unknown status value.', { fieldErrors: { status: 'unknown value' } });
  }
  if (status === 'CALLBACK_SCHEDULED' && !followUpAt) {
    return fail(res, 422, 'VALIDATION_FAILED', 'A callback needs a follow-up time.', {
      fieldErrors: { followUpAt: 'required when status is CALLBACK_SCHEDULED' },
    });
  }
  if (expectedVersion != null && expectedVersion !== client.version) {
    const [remarks, documents] = await Promise.all([
      query('SELECT * FROM remarks WHERE client_id = $1 ORDER BY created_at DESC LIMIT 20', [client.client_id]),
      query('SELECT * FROM documents WHERE client_id = $1', [client.client_id]),
    ]);
    return res.status(409).json({
      code: 'CONFLICT',
      message: 'This client changed since you last loaded it.',
      current: S.clientDetail(client, { remarks: remarks.rows, documents: documents.rows }),
    });
  }

  return idempotent(req, res, async () => {
    const entry = await tx(async (c) => {
      const payment = status === 'PAYMENT_RECEIVED' ? 'RECEIVED' : client.payment_status;
      const government = status === 'WAITING_GOVERNMENT_APPROVAL' ? 'UNDER_REVIEW' : client.government_status;
      await c.query(
        `UPDATE clients SET process_status = $1, payment_status = $2, government_status = $3,
           follow_up_at = $4, last_interaction_at = now(), version = version + 1
         WHERE client_id = $5`,
        [status, payment, government, followUpAt || null, client.client_id],
      );
      const entryId = newId('tl');
      const summary = note ? `${STATUS_SUMMARY[status]} — ${note}` : STATUS_SUMMARY[status];
      const { rows } = await c.query(
        `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, occurred_at)
         VALUES ($1,$2,'STATUS_CHANGE',$3,$4,COALESCE($5::timestamptz, now())) RETURNING *`,
        [entryId, client.client_id, summary, req.employee.display_name, occurredAt || null],
      );
      return rows[0];
    });
    return { status: 200, body: S.timelineEntry(entry) };
  });
}));

app.post('/clients/:clientId/remarks', requireAuth, asyncRoute(async (req, res) => {
  const client = await loadOwnedClient(req, res);
  if (!client) return;
  const { body, recordedAt } = req.body || {};
  if (!body || !body.trim()) {
    return fail(res, 422, 'VALIDATION_FAILED', 'A remark cannot be empty.', { fieldErrors: { body: 'required' } });
  }

  return idempotent(req, res, async () => {
    const entry = await tx(async (c) => {
      await c.query(
        `INSERT INTO remarks (remark_id, client_id, body, author_id, author_name, created_at)
         VALUES ($1,$2,$3,$4,$5, COALESCE($6::timestamptz, now()))`,
        [newId('rm'), client.client_id, body.trim(), req.employee.employee_id,
          req.employee.display_name, recordedAt || null],
      );
      await c.query('UPDATE clients SET last_interaction_at = now(), version = version + 1 WHERE client_id = $1',
        [client.client_id]);
      const { rows } = await c.query(
        `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, occurred_at)
         VALUES ($1,$2,'REMARK',$3,$4, COALESCE($5::timestamptz, now())) RETURNING *`,
        [newId('tl'), client.client_id, body.trim().slice(0, 140),
          req.employee.display_name, recordedAt || null],
      );
      return rows[0];
    });
    return { status: 201, body: S.timelineEntry(entry) };
  });
}));

app.get('/clients/:clientId/timeline', requireAuth, asyncRoute(async (req, res) => {
  const client = await loadOwnedClient(req, res);
  if (!client) return;
  const limit = Math.min(parseInt(req.query.limit || '25', 10), 100);
  const { rows } = await query(
    'SELECT * FROM timeline_entries WHERE client_id = $1 ORDER BY occurred_at DESC LIMIT $2',
    [client.client_id, limit],
  );
  res.json({ items: rows.map(S.timelineEntry) });
}));

// ====================================================================================== Calls ===
const { simulateCall } = require('./telephony');

app.post('/calls', requireAuth, asyncRoute(async (req, res) => {
  const { clientId, reason } = req.body || {};
  if (!clientId) return fail(res, 422, 'VALIDATION_FAILED', 'clientId is required.');

  const { rows } = await query('SELECT * FROM clients WHERE client_id = $1', [clientId]);
  const client = rows[0];
  if (!client) return fail(res, 404, 'NOT_FOUND', 'No such client.');
  if (client.assigned_employee !== req.employee.employee_id) {
    return fail(res, 403, 'FORBIDDEN', 'This client is not assigned to you.');
  }

  const active = await query(
    `SELECT 1 FROM call_sessions WHERE employee_id = $1
       AND state IN ('REQUESTED','RINGING_AGENT','RINGING_CUSTOMER','BRIDGED') LIMIT 1`,
    [req.employee.employee_id],
  );
  if (active.rows.length > 0) {
    return fail(res, 409, 'CONFLICT', 'You already have a call in progress.');
  }

  return idempotent(req, res, async () => {
    const sessionId = newId('cs');
    const { rows: created } = await query(
      `INSERT INTO call_sessions (call_session_id, client_id, employee_id, device_id, state, reason)
       VALUES ($1,$2,$3,$4,'REQUESTED',$5) RETURNING *`,
      [sessionId, clientId, req.employee.employee_id, req.deviceId, reason || null],
    );
    // The provider owns the audio path. Here that provider is simulated; the app's view of the
    // world — request, then observe state transitions — is identical either way (ADR-0002).
    simulateCall(sessionId, clientId, req.employee, req.deviceId);
    return { status: 202, body: S.callSession(created[0]) };
  });
}));

app.get('/calls/history', requireAuth, asyncRoute(async (req, res) => {
  const limit = Math.min(parseInt(req.query.limit || '25', 10), 100);
  const params = [req.employee.employee_id];
  let where = 'r.employee_id = $1';
  if (req.query.clientId) { params.push(req.query.clientId); where += ` AND r.client_id = $${params.length}`; }
  params.push(limit);
  const { rows } = await query(
    `SELECT r.*, c.name AS client_name FROM call_records r
     JOIN clients c ON c.client_id = r.client_id
     WHERE ${where} ORDER BY r.started_at DESC LIMIT $${params.length}`,
    params,
  );
  res.json({ items: rows.map(S.callRecord) });
}));

app.get('/calls/:callSessionId', requireAuth, asyncRoute(async (req, res) => {
  const { rows } = await query(
    'SELECT * FROM call_sessions WHERE call_session_id = $1 AND employee_id = $2',
    [req.params.callSessionId, req.employee.employee_id],
  );
  if (rows.length === 0) return fail(res, 404, 'NOT_FOUND', 'No such call.');
  res.json(S.callSession(rows[0]));
}));

// ================================================================================= Attendance ===
app.post('/attendance', requireAuth, asyncRoute(async (req, res) => {
  const { kind, occurredAt, location, hasPhoto } = req.body || {};
  if (!['CHECK_IN', 'CHECK_OUT'].includes(kind) || !location?.latitude || !location?.longitude) {
    return fail(res, 422, 'VALIDATION_FAILED', 'kind and a valid location are required.');
  }

  const last = await query(
    `SELECT kind FROM attendance WHERE employee_id = $1
       AND occurred_at::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
     ORDER BY occurred_at DESC LIMIT 1`,
    [req.employee.employee_id],
  );
  if (last.rows[0]?.kind === kind) {
    return fail(res, 409, 'CONFLICT', kind === 'CHECK_IN'
      ? 'You are already checked in.' : 'You are already checked out.');
  }

  return idempotent(req, res, async () => {
    const { rows } = await query(
      `INSERT INTO attendance (attendance_id, employee_id, kind, occurred_at, latitude, longitude,
                               accuracy_metres, photo_uploaded)
       VALUES ($1,$2,$3, COALESCE($4::timestamptz, now()), $5,$6,$7, FALSE) RETURNING *`,
      [newId('att'), req.employee.employee_id, kind, occurredAt || null,
        location.latitude, location.longitude, location.accuracyMetres || null],
    );
    return { status: 201, body: S.attendanceRecord(rows[0]) };
  });
}));

app.get('/attendance/today', requireAuth, asyncRoute(async (req, res) => {
  const { rows } = await query(
    `SELECT kind, occurred_at FROM attendance WHERE employee_id = $1
       AND occurred_at::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
     ORDER BY occurred_at ASC`,
    [req.employee.employee_id],
  );
  const checkIn = rows.find((r) => r.kind === 'CHECK_IN');
  const checkOut = [...rows].reverse().find((r) => r.kind === 'CHECK_OUT');
  const worked = checkIn && checkOut
    ? Math.max(0, Math.floor((new Date(checkOut.occurred_at) - new Date(checkIn.occurred_at)) / 1000))
    : undefined;
  res.json({
    checkedIn: Boolean(checkIn) && !checkOut,
    checkInAt: checkIn ? new Date(checkIn.occurred_at).toISOString() : undefined,
    checkOutAt: checkOut ? new Date(checkOut.occurred_at).toISOString() : undefined,
    workedSeconds: worked,
  });
}));

app.put('/attendance/:attendanceId/photo', requireAuth, asyncRoute(async (req, res) => {
  if (!Buffer.isBuffer(req.body) || req.body.length === 0) {
    return fail(res, 422, 'VALIDATION_FAILED', 'Expected a JPEG body.');
  }
  const { rowCount } = await query(
    `UPDATE attendance SET photo_bytes = $1, photo_uploaded = TRUE
     WHERE attendance_id = $2 AND employee_id = $3`,
    [req.body, req.params.attendanceId, req.employee.employee_id],
  );
  if (rowCount === 0) return fail(res, 404, 'NOT_FOUND', 'No such attendance record.');
  res.status(204).end();
}));

// =================================================================================== Location ===
const DAY_KEYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

/**
 * §10 working-hours gating, decided here rather than on the device.
 * The device clock and timezone are user-settable, and this is a compliance boundary — an
 * out-of-hours location sample is a DPDP problem, not a UX problem.
 */
function withinWorkingHours(emp, whenIso) {
  const when = new Date(whenIso);
  if (Number.isNaN(when.getTime())) return false;
  const local = new Date(when.toLocaleString('en-US', { timeZone: emp.timezone || 'Asia/Kolkata' }));
  if (!emp.work_days.includes(DAY_KEYS[local.getDay()])) return false;
  const minutes = local.getHours() * 60 + local.getMinutes();
  const [sh, sm] = String(emp.work_start).split(':').map(Number);
  const [eh, em] = String(emp.work_end).split(':').map(Number);
  return minutes >= sh * 60 + sm && minutes <= eh * 60 + em;
}

app.post('/location/pings', requireAuth, asyncRoute(async (req, res) => {
  const pings = req.body?.pings;
  if (!Array.isArray(pings) || pings.length === 0) {
    return fail(res, 422, 'VALIDATION_FAILED', 'pings must be a non-empty array.');
  }
  return idempotent(req, res, async () => {
    let accepted = 0;
    let rejected = 0;
    for (const p of pings) {
      if (!p?.location || !withinWorkingHours(req.employee, p.sampledAt)) { rejected += 1; continue; }
      await query(
        `INSERT INTO location_pings (employee_id, device_id, sampled_at, latitude, longitude,
                                     accuracy_metres, is_mocked, battery_percent)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
        [req.employee.employee_id, req.deviceId, p.sampledAt, p.location.latitude,
          p.location.longitude, p.location.accuracyMetres || null,
          Boolean(p.isMocked), p.batteryPercent ?? null],
      );
      accepted += 1;
    }
    return { status: 202, body: { accepted, rejectedOutsideWorkingHours: rejected } };
  });
}));

app.get('/location/policy', requireAuth, (req, res) => {
  const e = req.employee;
  res.json({
    enabled: true,
    sampleIntervalSeconds: 600, // §10: every 10 minutes
    workingHours: e.work_days.map((d) => ({
      dayOfWeek: d,
      startLocalTime: String(e.work_start).slice(0, 5),
      endLocalTime: String(e.work_end).slice(0, 5),
    })),
    timezone: e.timezone,
  });
});

// ===================================================================================== Device ===
app.put('/device/push-token', requireAuth, asyncRoute(async (req, res) => {
  if (!req.body?.token) return fail(res, 422, 'VALIDATION_FAILED', 'token is required.');
  await query('UPDATE devices SET push_token = $1, last_seen_at = now() WHERE device_id = $2',
    [req.body.token, req.deviceId]);
  res.status(204).end();
}));

app.post('/device/compliance', requireAuth, asyncRoute(async (req, res) => {
  const checks = req.body?.checks || {};
  // The server decides. Client heuristics are advisory; they are trivially bypassable and are never
  // the control (ADR-0004, §14–§20).
  const hardFail = checks.rootIndicators || checks.emulatorIndicators || checks.debuggerAttached;
  const verdict = hardFail
    ? { compliant: false, action: 'PURGE_CACHE', reason: 'Device integrity checks failed.' }
    : { compliant: true, action: 'NONE' };
  await query(
    'UPDATE devices SET compliant = $1, last_verdict = $2, last_seen_at = now() WHERE device_id = $3',
    [verdict.compliant, verdict.action, req.deviceId],
  );
  res.json(verdict);
}));

// ================================================================================ Error handler ==
app.use((err, req, res, _next) => {
  console.error(`[error] ${req.method} ${req.path}:`, err.message);
  res.status(500).json({ code: 'SERVER_ERROR', message: 'Something went wrong on the server.' });
});

const port = parseInt(process.env.PORT || '4010', 10);
app.listen(port, '0.0.0.0', () => {
  console.log(`Asktrix dev CRM listening on http://0.0.0.0:${port}`);
  console.log('Android emulator reaches this at http://10.0.2.2:' + port + '/');
});

module.exports = app;
