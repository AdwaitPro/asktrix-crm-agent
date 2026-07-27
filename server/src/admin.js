'use strict';
const jwt = require('jsonwebtoken');
const fs = require('fs');
const nodePath = require('path');
const { query } = require('./db');
const { maskPhone, maskEmail } = require('./mask');
const { newId } = require('./auth');
const S = require('./serialize');

/**
 * Demo data filter.
 *
 * Seeded history is flagged is_demo. The console can show demonstration data, real activity, or
 * both, without ever deleting anything - so a demo can be given on realistic history and then
 * switched to show only what actually happened on the devices.
 */
function demoFilter(req, alias = '') {
  const col = alias ? `${alias}.is_demo` : 'is_demo';
  switch (req.query.data) {
    case 'demo': return ` AND ${col} = TRUE`;
    case 'real': return ` AND ${col} = FALSE`;
    default: return '';
  }
}

/**
 * Admin API (§25-§27).
 *
 * Serves the manager-facing dashboard: employee status, live location, attendance, call records with
 * recordings, the client pipeline, and device compliance.
 *
 * **On masking:** §4 says *employees* must never see full contact details. A manager working a
 * customer escalation legitimately may. So the admin API masks by default and exposes an explicit,
 * **audited** reveal - every unmask is written to `pii_access_log` with who, what and when. That is
 * both the safer default and what a DPDP audit will ask for.
 */

const SECRET = process.env.JWT_SECRET;
const ADMIN_TTL = 3600;

function issueAdminToken(admin) {
  return jwt.sign({ sub: admin.employee_id, role: admin.role, admin: true }, SECRET, {
    expiresIn: ADMIN_TTL,
  });
}

/** Only TEAM_LEADER may use the admin surface. Role is checked server-side, never in the UI. */
async function requireAdmin(req, res, next) {
  const header = req.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return res.status(401).json({ code: 'UNAUTHENTICATED', message: 'Sign in.' });

  let claims;
  try {
    claims = jwt.verify(token, SECRET);
  } catch {
    return res.status(401).json({ code: 'UNAUTHENTICATED', message: 'Session expired.' });
  }
  if (!claims.admin) {
    return res.status(403).json({ code: 'FORBIDDEN', message: 'Admin access required.' });
  }
  const { rows } = await query('SELECT * FROM employees WHERE employee_id = $1', [claims.sub]);
  if (!rows.length || rows[0].role !== 'TEAM_LEADER') {
    return res.status(403).json({ code: 'FORBIDDEN', message: 'Admin access required.' });
  }
  req.admin = rows[0];
  return next();
}

function register(app, asyncRoute, verifyPassword) {
  // ------------------------------------------------------------------ auth --
  app.post('/admin/login', asyncRoute(async (req, res) => {
    const { employeeCode, password } = req.body || {};
    const { rows } = await query(
      "SELECT * FROM employees WHERE employee_code = $1 AND active = TRUE AND role = 'TEAM_LEADER'",
      [employeeCode],
    );
    const admin = rows[0];
    if (!admin || !verifyPassword(password, admin.password_hash, admin.password_salt)) {
      return res.status(401).json({ code: 'UNAUTHENTICATED', message: 'Incorrect code or password.' });
    }
    res.json({
      accessToken: issueAdminToken(admin),
      expiresInSeconds: ADMIN_TTL,
      admin: { name: admin.display_name, code: admin.employee_code, role: admin.role },
    });
  }));

  // -------------------------------------------------------------- overview --
  app.get('/admin/overview', requireAdmin, asyncRoute(async (req, res) => {
    const F = demoFilter(req);
    const { rows } = await query(`
      SELECT
        (SELECT count(*) FROM employees WHERE active) AS employees,
        (SELECT count(*) FROM clients WHERE TRUE ${F}) AS clients,
        (SELECT count(*) FROM clients WHERE process_status = 'COMPLETED' ${F}) AS completed,
        (SELECT count(*) FROM clients WHERE follow_up_at IS NOT NULL AND follow_up_at <= now() ${F})
          AS follow_ups_due,
        (SELECT count(*) FROM call_records WHERE (started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date ${F})
          AS calls_today,
        (SELECT count(*) FROM call_records
          WHERE (started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date AND state = 'COMPLETED' ${F})
          AS calls_connected_today,
        (SELECT coalesce(sum(duration_seconds), 0) FROM call_records
          WHERE (started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date ${F}) AS talk_seconds_today,
        (SELECT count(*) FROM call_records WHERE recording_available ${F}) AS recordings,
        (SELECT count(DISTINCT employee_id) FROM attendance
          WHERE kind = 'CHECK_IN' AND (occurred_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date ${F})
          AS checked_in_today,
        (SELECT count(*) FROM location_pings
          WHERE (sampled_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date ${F}) AS pings_today,
        (SELECT count(*) FROM devices WHERE TRUE ${F}) AS devices,
        (SELECT count(*) FROM devices WHERE NOT compliant ${F}) AS devices_noncompliant
    `);
    res.json({ ...rows[0], dataMode: req.query.data || 'all' });
  }));

  // ------------------------------------------------------------- employees --
  app.get('/admin/employees', requireAdmin, asyncRoute(async (_req, res) => {
    const { rows } = await query(`
      SELECT e.employee_id, e.employee_code, e.display_name, e.role,
             e.work_start, e.work_end, e.timezone,
             (SELECT count(*) FROM clients c WHERE c.assigned_employee = e.employee_id) AS assigned,
             (SELECT count(*) FROM call_records r WHERE r.employee_id = e.employee_id
                AND (r.started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date) AS calls_today,
             (SELECT coalesce(sum(duration_seconds),0) FROM call_records r
                WHERE r.employee_id = e.employee_id
                AND (r.started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date) AS talk_seconds_today,
             (SELECT kind FROM attendance a WHERE a.employee_id = e.employee_id
                AND (a.occurred_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
                ORDER BY a.occurred_at DESC LIMIT 1) AS last_attendance,
             (SELECT occurred_at FROM attendance a WHERE a.employee_id = e.employee_id
                AND a.kind='CHECK_IN' AND (a.occurred_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date
                ORDER BY a.occurred_at ASC LIMIT 1) AS check_in_at,
             (SELECT json_build_object('lat', p.latitude, 'lng', p.longitude,
                                       'at', p.sampled_at, 'battery', p.battery_percent)
                FROM location_pings p WHERE p.employee_id = e.employee_id
                ORDER BY p.sampled_at DESC LIMIT 1) AS last_location,
             (SELECT count(*) FROM devices d WHERE d.employee_id = e.employee_id) AS devices
      FROM employees e WHERE e.active ORDER BY e.employee_code
    `);
    const { permissionsFor, statusesFor } = require('./roles');
    res.json({
      items: rows.map((r) => ({
        ...r,
        permissions: permissionsFor(r.role),
        allowedStatuses: statusesFor(r.role),
      })),
    });
  }));

  app.get('/admin/employees/:id/locations', requireAdmin, asyncRoute(async (req, res) => {
    const { rows } = await query(
      `SELECT latitude, longitude, accuracy_metres, sampled_at, received_at, battery_percent, is_mocked
       FROM location_pings WHERE employee_id = $1 ${demoFilter(req)}
       ORDER BY sampled_at DESC LIMIT 200`,
      [req.params.id],
    );
    res.json({ items: rows });
  }));

  app.get('/admin/employees/:id/attendance', requireAdmin, asyncRoute(async (req, res) => {
    const { rows } = await query(
      `SELECT attendance_id, kind, occurred_at, recorded_at, latitude, longitude, photo_uploaded
       FROM attendance WHERE employee_id = $1 ${demoFilter(req)}
       ORDER BY occurred_at DESC LIMIT 60`,
      [req.params.id],
    );
    res.json({ items: rows });
  }));

  // ------------------------------------------------------------------ calls --
  app.get('/admin/calls', requireAdmin, asyncRoute(async (req, res) => {
    const params = [];
    let where = '1=1';
    if (req.query.employeeId) { params.push(req.query.employeeId); where += ` AND r.employee_id = $${params.length}`; }
    where += demoFilter(req, 'r');
    const { rows } = await query(
      `SELECT r.call_record_id, r.client_id, c.name AS client_name, e.display_name AS employee_name,
              r.direction, r.state, r.started_at, r.duration_seconds, r.recording_available,
              r.recording_uri, r.device_id, r.is_demo
       FROM call_records r
       JOIN clients c ON c.client_id = r.client_id
       JOIN employees e ON e.employee_id = r.employee_id
       WHERE ${where} ORDER BY r.started_at DESC LIMIT 100`,
      params,
    );
    res.json({ items: rows });
  }));

  /**
   * Streams a call recording to an administrator (§6).
   *
   * Audited like the contact reveal: a recording of a customer conversation is personal data, and
   * "who listened to this call" is a question a DPDP audit will ask. Note the asymmetry with the
   * mobile app - the handset is only ever told that a recording *exists*; it can never fetch one.
   *
   * In production this proxies the telephony provider's short-lived recording URL rather than
   * serving a local file. Here it serves the generated demo audio.
   */
  app.get('/admin/recordings/:callRecordId', requireAdmin, asyncRoute(async (req, res) => {
    const { rows } = await query(
      'SELECT recording_available, client_id FROM call_records WHERE call_record_id = $1',
      [req.params.callRecordId],
    );
    if (!rows.length || !rows[0].recording_available) {
      return res.status(404).json({ code: 'NOT_FOUND', message: 'No recording for that call.' });
    }

    await query(
      `INSERT INTO pii_access_log (admin_id, admin_name, client_id, reason, accessed_at)
       VALUES ($1,$2,$3,$4, now())`,
      [req.admin.employee_id, req.admin.display_name, rows[0].client_id,
        `played recording ${req.params.callRecordId}`],
    );

    // Real recordings live in Postgres so the service runs anywhere, including a read-only
    // serverless filesystem. Seeded demo calls fall back to the generated sample.
    const stored = await query(
      'SELECT mime_type, bytes FROM call_recordings WHERE call_record_id = $1',
      [req.params.callRecordId],
    );

    res.setHeader('cache-control', 'no-store');

    if (stored.rows.length) {
      res.setHeader('content-type', stored.rows[0].mime_type);
      return res.send(stored.rows[0].bytes);
    }

    const file = nodePath.join(__dirname, '..', 'recordings', 'sample.wav');
    if (!fs.existsSync(file)) {
      return res.status(404).json({ code: 'NOT_FOUND', message: 'Recording not stored.' });
    }
    res.setHeader('content-type', 'audio/wav');
    return fs.createReadStream(file).pipe(res);
  }));

  // ---------------------------------------------------------------- clients --
  app.get('/admin/clients', requireAdmin, asyncRoute(async (req, res) => {
    const F = demoFilter(req, 'c');
    const { rows } = await query(`
      SELECT c.client_id, c.name, c.service_id, c.process_status, c.payment_status,
             c.government_status, c.documents_pending, c.follow_up_at, c.last_interaction_at,
             c.version, c.phone_full, c.email_full, c.is_demo, e.display_name AS assigned_to
      FROM clients c LEFT JOIN employees e ON e.employee_id = c.assigned_employee
      WHERE TRUE ${F}
      ORDER BY c.client_id
    `);
    // Masked by default, exactly like the mobile app. Revealing requires the audited endpoint below.
    res.json({
      items: rows.map((r) => ({
        clientId: r.client_id,
        name: r.name,
        serviceId: r.service_id,
        processStatus: r.process_status,
        paymentStatus: r.payment_status,
        governmentStatus: r.government_status,
        documentsPending: r.documents_pending,
        followUpAt: r.follow_up_at,
        lastInteractionAt: r.last_interaction_at,
        assignedTo: r.assigned_to,
        phoneMasked: maskPhone(r.phone_full),
        emailMasked: maskEmail(r.email_full),
        isDemo: r.is_demo,
      })),
    });
  }));

  /**
   * Reveals a customer's real contact details to an admin - and records that it happened.
   *
   * This is the only endpoint in the entire system that returns an unmasked value, it is restricted
   * to TEAM_LEADER, and it is always audited. A DPDP audit asks "who looked at this customer's data
   * and when"; `pii_access_log` is the answer.
   */
  app.post('/admin/clients/:clientId/reveal', requireAdmin, asyncRoute(async (req, res) => {
    const { rows } = await query(
      'SELECT phone_full, email_full FROM clients WHERE client_id = $1',
      [req.params.clientId],
    );
    if (!rows.length) return res.status(404).json({ code: 'NOT_FOUND', message: 'No such client.' });

    await query(
      `INSERT INTO pii_access_log (admin_id, admin_name, client_id, reason, accessed_at)
       VALUES ($1,$2,$3,$4, now())`,
      [req.admin.employee_id, req.admin.display_name, req.params.clientId,
        req.body?.reason || 'not stated'],
    );

    res.json({ phone: rows[0].phone_full, email: rows[0].email_full, audited: true });
  }));

  // ------------------------------------------------- create and assign --

  /** Indian mobile numbers, with or without the country code. */
  const PHONE = /^(?:\+?91)?[6-9]\d{9}$/;
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

  /**
   * Create a real client and hand it to an employee.
   *
   * This is the one place a full phone number legitimately enters the system, so it is also the one
   * place that has to be careful: the number is stored, never echoed. The response is the same
   * masked projection everything else returns, which the privacy tripwire then re-checks.
   *
   * Created rows are is_demo = FALSE by design. They are real work, so they belong in the Real view
   * alongside genuine activity rather than mixed into the seeded sample.
   */
  app.post('/admin/clients', requireAdmin, asyncRoute(async (req, res) => {
    const { name, phone, email, serviceId, assignedEmployeeId, followUpAt, documentsPending } =
      req.body || {};

    const fieldErrors = {};
    const cleanName = String(name || '').trim();
    const cleanPhone = String(phone || '').replace(/[\s-]/g, '');
    const cleanEmail = String(email || '').trim();

    if (cleanName.length < 2) fieldErrors.name = 'Enter the client name.';
    if (!PHONE.test(cleanPhone)) fieldErrors.phone = 'Enter a 10 digit Indian mobile number.';
    if (cleanEmail && !EMAIL.test(cleanEmail)) fieldErrors.email = 'That email address is not valid.';

    let employeeId = null;
    if (assignedEmployeeId) {
      const owner = await query(
        'SELECT employee_id FROM employees WHERE employee_id = $1 AND active = TRUE',
        [assignedEmployeeId],
      );
      if (owner.rows.length === 0) fieldErrors.assignedEmployeeId = 'No such employee.';
      else employeeId = owner.rows[0].employee_id;
    }

    if (Object.keys(fieldErrors).length > 0) {
      return res.status(422).json({
        code: 'VALIDATION_FAILED',
        message: 'Check the highlighted fields.',
        fieldErrors,
      });
    }

    const clientId = `CLI-${Math.floor(100000 + Math.random() * 899999)}`;
    const { rows } = await query(
      `INSERT INTO clients (client_id, name, service_id, phone_full, email_full,
                            assigned_employee, process_status, documents_pending, follow_up_at,
                            is_demo)
       VALUES ($1,$2,$3,$4,$5,$6,'NEW',$7,$8, FALSE)
       RETURNING *`,
      [
        clientId,
        cleanName,
        String(serviceId || '').trim() || null,
        cleanPhone.replace(/^\+?91/, ''),
        cleanEmail || null,
        employeeId,
        Number.isFinite(Number(documentsPending)) ? Math.max(0, Number(documentsPending)) : 0,
        followUpAt || null,
      ],
    );

    await query(
      `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name)
       VALUES ($1,$2,'STATUS_CHANGE',$3,$4)`,
      [newId('tl'), clientId, 'Client added to the CRM', req.admin.display_name],
    );

    // Masked on the way out, exactly like every other read.
    res.status(201).json(S.clientDetail(rows[0]));
  }));

  /** Move a client to a different employee, or unassign by sending null. */
  app.post('/admin/clients/:clientId/assign', requireAdmin, asyncRoute(async (req, res) => {
    const { assignedEmployeeId } = req.body || {};

    let employee = null;
    if (assignedEmployeeId) {
      const owner = await query(
        'SELECT employee_id, display_name FROM employees WHERE employee_id = $1 AND active = TRUE',
        [assignedEmployeeId],
      );
      if (owner.rows.length === 0) {
        return res.status(422).json({
          code: 'VALIDATION_FAILED',
          message: 'No such employee.',
          fieldErrors: { assignedEmployeeId: 'unknown' },
        });
      }
      employee = owner.rows[0];
    }

    const { rows } = await query(
      `UPDATE clients SET assigned_employee = $1, version = version + 1
       WHERE client_id = $2 RETURNING *`,
      [employee ? employee.employee_id : null, req.params.clientId],
    );
    if (rows.length === 0) {
      return res.status(404).json({ code: 'NOT_FOUND', message: 'No such client.' });
    }

    // A reassignment is a fact about the client's history, not just a column change.
    await query(
      `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name)
       VALUES ($1,$2,'STATUS_CHANGE',$3,$4)`,
      [
        newId('tl'),
        req.params.clientId,
        employee ? `Assigned to ${employee.display_name}` : 'Unassigned',
        req.admin.display_name,
      ],
    );

    res.json(S.clientDetail(rows[0]));
  }));

  app.get('/admin/audit', requireAdmin, asyncRoute(async (_req, res) => {
    const { rows } = await query(
      `SELECT admin_name, client_id, reason, accessed_at FROM pii_access_log
       ORDER BY accessed_at DESC LIMIT 100`,
    );
    res.json({ items: rows });
  }));

  // ---------------------------------------------------------------- devices --
  app.get('/admin/devices', requireAdmin, asyncRoute(async (req, res) => {
    const F = demoFilter(req, 'd');
    const { rows } = await query(`
      SELECT d.device_id, d.manufacturer, d.model, d.os_version, d.app_version,
             d.compliant, d.last_verdict, d.last_seen_at, d.push_token IS NOT NULL AS push_registered,
             e.display_name AS employee_name
      FROM devices d LEFT JOIN employees e ON e.employee_id = d.employee_id
      WHERE TRUE ${F}
      ORDER BY d.last_seen_at DESC
    `);
    res.json({ items: rows });
  }));
}

module.exports = { register, requireAdmin };
