'use strict';
const jwt = require('jsonwebtoken');
const fs = require('fs');
const nodePath = require('path');
const { query } = require('./db');
const { maskPhone, maskEmail } = require('./mask');

/**
 * Admin API (§25–§27).
 *
 * Serves the manager-facing dashboard: employee status, live location, attendance, call records with
 * recordings, the client pipeline, and device compliance.
 *
 * **On masking:** §4 says *employees* must never see full contact details. A manager working a
 * customer escalation legitimately may. So the admin API masks by default and exposes an explicit,
 * **audited** reveal — every unmask is written to `pii_access_log` with who, what and when. That is
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
  app.get('/admin/overview', requireAdmin, asyncRoute(async (_req, res) => {
    const { rows } = await query(`
      SELECT
        (SELECT count(*) FROM employees WHERE active) AS employees,
        (SELECT count(*) FROM clients) AS clients,
        (SELECT count(*) FROM clients WHERE process_status = 'COMPLETED') AS completed,
        (SELECT count(*) FROM clients WHERE follow_up_at IS NOT NULL AND follow_up_at <= now())
          AS follow_ups_due,
        (SELECT count(*) FROM call_records WHERE (started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date)
          AS calls_today,
        (SELECT count(*) FROM call_records
          WHERE (started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date AND state = 'COMPLETED')
          AS calls_connected_today,
        (SELECT coalesce(sum(duration_seconds), 0) FROM call_records
          WHERE (started_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date) AS talk_seconds_today,
        (SELECT count(*) FROM call_records WHERE recording_available) AS recordings,
        (SELECT count(DISTINCT employee_id) FROM attendance
          WHERE kind = 'CHECK_IN' AND (occurred_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date)
          AS checked_in_today,
        (SELECT count(*) FROM location_pings
          WHERE (sampled_at AT TIME ZONE 'Asia/Kolkata')::date = (now() AT TIME ZONE 'Asia/Kolkata')::date) AS pings_today,
        (SELECT count(*) FROM devices) AS devices,
        (SELECT count(*) FROM devices WHERE NOT compliant) AS devices_noncompliant
    `);
    res.json(rows[0]);
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
    res.json({ items: rows });
  }));

  app.get('/admin/employees/:id/locations', requireAdmin, asyncRoute(async (req, res) => {
    const { rows } = await query(
      `SELECT latitude, longitude, accuracy_metres, sampled_at, received_at, battery_percent, is_mocked
       FROM location_pings WHERE employee_id = $1
       ORDER BY sampled_at DESC LIMIT 200`,
      [req.params.id],
    );
    res.json({ items: rows });
  }));

  app.get('/admin/employees/:id/attendance', requireAdmin, asyncRoute(async (req, res) => {
    const { rows } = await query(
      `SELECT attendance_id, kind, occurred_at, recorded_at, latitude, longitude, photo_uploaded
       FROM attendance WHERE employee_id = $1 ORDER BY occurred_at DESC LIMIT 60`,
      [req.params.id],
    );
    res.json({ items: rows });
  }));

  // ------------------------------------------------------------------ calls --
  app.get('/admin/calls', requireAdmin, asyncRoute(async (req, res) => {
    const params = [];
    let where = '1=1';
    if (req.query.employeeId) { params.push(req.query.employeeId); where += ` AND r.employee_id = $${params.length}`; }
    const { rows } = await query(
      `SELECT r.call_record_id, r.client_id, c.name AS client_name, e.display_name AS employee_name,
              r.direction, r.state, r.started_at, r.duration_seconds, r.recording_available,
              r.recording_uri, r.device_id
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
   * mobile app — the handset is only ever told that a recording *exists*; it can never fetch one.
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
  app.get('/admin/clients', requireAdmin, asyncRoute(async (_req, res) => {
    const { rows } = await query(`
      SELECT c.client_id, c.name, c.service_id, c.process_status, c.payment_status,
             c.government_status, c.documents_pending, c.follow_up_at, c.last_interaction_at,
             c.version, c.phone_full, c.email_full, e.display_name AS assigned_to
      FROM clients c LEFT JOIN employees e ON e.employee_id = c.assigned_employee
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
      })),
    });
  }));

  /**
   * Reveals a customer's real contact details to an admin — and records that it happened.
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

  app.get('/admin/audit', requireAdmin, asyncRoute(async (_req, res) => {
    const { rows } = await query(
      `SELECT admin_name, client_id, reason, accessed_at FROM pii_access_log
       ORDER BY accessed_at DESC LIMIT 100`,
    );
    res.json({ items: rows });
  }));

  // ---------------------------------------------------------------- devices --
  app.get('/admin/devices', requireAdmin, asyncRoute(async (_req, res) => {
    const { rows } = await query(`
      SELECT d.device_id, d.manufacturer, d.model, d.os_version, d.app_version,
             d.compliant, d.last_verdict, d.last_seen_at, d.push_token IS NOT NULL AS push_registered,
             e.display_name AS employee_name
      FROM devices d LEFT JOIN employees e ON e.employee_id = d.employee_id
      ORDER BY d.last_seen_at DESC
    `);
    res.json({ items: rows });
  }));
}

module.exports = { register, requireAdmin };
