'use strict';
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { query, pool } = require('./db');
const { hashPassword } = require('./auth');

const id = (p) => `${p}_${crypto.randomBytes(8).toString('hex')}`;
const daysFromNow = (n) => new Date(Date.now() + n * 86_400_000).toISOString();

// Full contact details live ONLY in the database. The API masks them on the way out, which is the
// whole point of ADR-0003 — seeding realistic values is what proves the masking actually works.
const CLIENTS = [
  ['Sivakumar Ramanathan', 'SVC-GST-2291', '9876543212', 'sivakumar@gmail.com', 'DOCUMENTS_PENDING', 'PENDING', 'NOT_SUBMITTED', 3, 0],
  ['Priya Nair',           'SVC-ITR-1180', '9845012345', 'priya.nair@outlook.com', 'PAYMENT_PENDING', 'PARTIAL', 'NOT_APPLICABLE', 1, 1],
  ['Rajesh Kumar Gupta',   'SVC-GST-2310', '9812345678', 'rajesh.gupta@yahoo.in', 'WAITING_GOVERNMENT_APPROVAL', 'RECEIVED', 'UNDER_REVIEW', 0, 4],
  ['Anitha Krishnan',      'SVC-FSSAI-887','9900112233', 'anitha.k@gmail.com', 'CLIENT_NOT_RESPONDING', 'PENDING', 'NOT_SUBMITTED', 2, -1],
  ['Mohammed Irfan',       'SVC-MSME-441', '9663311447', 'irfan.m@gmail.com', 'DOCUMENTS_RECEIVED', 'PENDING', 'NOT_SUBMITTED', 0, 2],
  ['Deepa Venkatesh',      'SVC-TM-9021',  '9741122334', 'deepa.v@gmail.com', 'CALLBACK_SCHEDULED', 'NOT_DUE', 'NOT_APPLICABLE', 1, 0],
  ['Suresh Babu',          'SVC-GST-2377', '9448899001', 'suresh.babu@rediffmail.com', 'COMPLETED', 'RECEIVED', 'APPROVED', 0, null],
  ['Kavitha Sundaram',     'SVC-ITR-1204', '9535566778', 'kavitha.s@gmail.com', 'NEW', 'NOT_DUE', 'NOT_APPLICABLE', 4, 1],
  ['Arun Prakash',         'SVC-MSME-459', '9886677889', 'arun.prakash@gmail.com', 'DOCUMENTS_PENDING', 'PENDING', 'NOT_SUBMITTED', 2, 3],
  ['Lakshmi Narayanan',    'SVC-FSSAI-903','9964455667', 'lakshmi.n@gmail.com', 'PAYMENT_RECEIVED', 'RECEIVED', 'SUBMITTED', 0, 5],
  ['Vikram Shetty',        'SVC-TM-9044',  '9880011223', 'vikram.shetty@gmail.com', 'CLIENT_NOT_RESPONDING', 'PENDING', 'NOT_APPLICABLE', 1, -2],
  ['Fatima Begum',         'SVC-GST-2402', '9739988776', 'fatima.b@gmail.com', 'DOCUMENTS_RECEIVED', 'PARTIAL', 'NOT_SUBMITTED', 0, 2],
];

const DOC_KINDS = ['PAN', 'AADHAAR', 'BANK_STATEMENT', 'ADDRESS_PROOF', 'PHOTOGRAPH'];

async function main() {
  console.log('Applying schema…');
  await query(fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8'));

  console.log('Seeding employees…');
  const employees = [
    ['EMP001', 'Aarav Sharma', 'RELATIONSHIP_MANAGER'],
    ['EMP002', 'Meera Iyer', 'CUSTOMER_SUPPORT'],
    ['EMP003', 'Rohit Desai', 'TEAM_LEADER'],
  ];
  const ids = [];
  for (const [code, name, role] of employees) {
    const { hash, salt } = hashPassword('asktrix123');
    const empId = id('emp');
    ids.push(empId);
    await query(
      `INSERT INTO employees (employee_id, employee_code, display_name, password_hash, password_salt,
                              role, permissions)
       VALUES ($1,$2,$3,$4,$5,$6,$7)`,
      [empId, code, name, hash, salt, role,
        JSON.stringify(['clients:read', 'clients:status', 'calls:place', 'attendance:write'])],
    );
  }

  console.log('Seeding clients…');
  let n = 0;
  for (const [name, svc, phone, email, status, pay, gov, docsPending, followUpDays] of CLIENTS) {
    const clientId = `CLI-${10240 + n}`;
    // Spread across the first two employees so the assignment filter is genuinely exercised.
    const owner = ids[n % 2];
    await query(
      `INSERT INTO clients (client_id, name, service_id, phone_full, email_full, assigned_employee,
                            process_status, payment_status, government_status, documents_pending,
                            follow_up_at, last_interaction_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11, now() - ($12 || ' hours')::interval)`,
      [clientId, name, svc, phone, email, owner, status, pay, gov, docsPending,
        followUpDays === null ? null : daysFromNow(followUpDays), String(n * 7 + 2)],
    );

    for (let d = 0; d < DOC_KINDS.length; d += 1) {
      const pending = d < docsPending;
      await query(
        `INSERT INTO documents (document_id, client_id, kind, status, received_at)
         VALUES ($1,$2,$3,$4,$5)`,
        [id('doc'), clientId, DOC_KINDS[d], pending ? 'PENDING' : 'VERIFIED',
          pending ? null : daysFromNow(-(d + 2))],
      );
    }

    await query(
      `INSERT INTO remarks (remark_id, client_id, body, author_name, created_at)
       VALUES ($1,$2,$3,$4, now() - interval '1 day')`,
      [id('rm'), clientId,
        docsPending > 0
          ? `Awaiting ${docsPending} document(s). Followed up by phone; client asked for more time.`
          : 'All documents verified. Proceeding with filing.',
        'Aarav Sharma'],
    );

    await query(
      `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, occurred_at)
       VALUES ($1,$2,'STATUS_CHANGE',$3,'Aarav Sharma', now() - interval '2 days')`,
      [id('tl'), clientId, `Case opened — ${svc}`],
    );
    n += 1;
  }

  const counts = await query(`
    SELECT (SELECT count(*) FROM employees) AS employees,
           (SELECT count(*) FROM clients)   AS clients,
           (SELECT count(*) FROM documents) AS documents,
           (SELECT count(*) FROM timeline_entries) AS timeline`);
  console.log('Seed complete:', counts.rows[0]);
  console.log('Login with employee code EMP001 / EMP002 / EMP003, password: asktrix123');
  await pool.end();
}

main().catch((e) => { console.error('Seed failed:', e.message); process.exit(1); });
