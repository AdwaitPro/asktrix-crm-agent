'use strict';
require('dotenv').config();
const crypto = require('crypto');
const { query, pool } = require('./db');

/**
 * Layers realistic history on top of the base seed so the admin dashboard has something to show.
 *
 * Generated rather than hand-written because a demo with three rows looks like a prototype; a demo
 * with a week of plausible movement, call outcomes and attendance looks like the product.
 *
 * Coordinates trace real routes across Bengaluru so the location view looks like fieldwork rather
 * than random noise.
 */
const id = (p) => `${p}_${crypto.randomBytes(8).toString('hex')}`;

// Indiranagar -> MG Road -> Koramangala -> HSR, roughly the shape of a day of client visits.
const ROUTE = [
  [12.9784, 77.6408], [12.9719, 77.6412], [12.9698, 77.6205], [12.9756, 77.6050],
  [12.9698, 77.5980], [12.9611, 77.6046], [12.9352, 77.6245], [12.9279, 77.6271],
  [12.9141, 77.6383], [12.9121, 77.6446], [12.9010, 77.6480], [12.8998, 77.6520],
];

const OUTCOMES = ['COMPLETED', 'COMPLETED', 'COMPLETED', 'NO_ANSWER', 'BUSY', 'COMPLETED'];

async function main() {
  console.log('Layering demo history…');

  const employees = (await query(
    "SELECT employee_id, display_name FROM employees WHERE role <> 'TEAM_LEADER' ORDER BY employee_code",
  )).rows;
  const clients = (await query('SELECT client_id, assigned_employee FROM clients')).rows;

  let pings = 0;
  let calls = 0;
  let attendance = 0;

  for (let dayAgo = 6; dayAgo >= 0; dayAgo -= 1) {
    for (const emp of employees) {
      // --- attendance: in around 09:35, out around 18:20, with natural variation ---
      const checkIn = `now() - interval '${dayAgo} days' - interval '${9 + Math.random()} hours'`;
      await query(
        `INSERT INTO attendance (attendance_id, employee_id, kind, occurred_at, recorded_at,
                                 latitude, longitude, accuracy_metres, photo_uploaded, is_demo)
         VALUES ($1,$2,'CHECK_IN', ${checkIn}, ${checkIn}, $3,$4,$5, $6, TRUE)`,
        [id('att'), emp.employee_id, ROUTE[0][0], ROUTE[0][1], 8 + Math.random() * 20,
          Math.random() > 0.5],
      );
      attendance += 1;

      if (dayAgo > 0) {
        const checkOut = `now() - interval '${dayAgo} days' - interval '${0.5 + Math.random()} hours'`;
        await query(
          `INSERT INTO attendance (attendance_id, employee_id, kind, occurred_at, recorded_at,
                                   latitude, longitude, accuracy_metres, photo_uploaded, is_demo)
           VALUES ($1,$2,'CHECK_OUT', ${checkOut}, ${checkOut}, $3,$4,$5, FALSE, TRUE)`,
          [id('att'), emp.employee_id, ROUTE[ROUTE.length - 1][0], ROUTE[ROUTE.length - 1][1],
            10 + Math.random() * 25],
        );
        attendance += 1;
      }

      // --- GPS: one sample per 10 minutes along the route, only within working hours ---
      for (let step = 0; step < ROUTE.length; step += 1) {
        const [lat, lng] = ROUTE[step];
        const hoursBack = 9.5 - step * 0.75;
        if (hoursBack < 0.5) continue;
        await query(
          `INSERT INTO location_pings (employee_id, device_id, sampled_at, received_at,
                                       latitude, longitude, accuracy_metres, is_mocked, battery_percent,
                                       is_demo)
           VALUES ($1,'demo-device', now() - interval '${dayAgo} days' - interval '${hoursBack} hours',
                   now() - interval '${dayAgo} days' - interval '${hoursBack} hours',
                   $2,$3,$4, FALSE, $5, TRUE)`,
          [emp.employee_id,
            lat + (Math.random() - 0.5) * 0.002,
            lng + (Math.random() - 0.5) * 0.002,
            6 + Math.random() * 25,
            Math.max(12, 95 - step * 5 - dayAgo * 2)],
        );
        pings += 1;
      }

      // --- calls: two to four per employee per day, against their own clients ---
      const mine = clients.filter((c) => c.assigned_employee === emp.employee_id);
      const count = 2 + Math.floor(Math.random() * 3);
      for (let n = 0; n < count && mine.length; n += 1) {
        const client = mine[Math.floor(Math.random() * mine.length)];
        const state = OUTCOMES[Math.floor(Math.random() * OUTCOMES.length)];
        const duration = state === 'COMPLETED' ? 30 + Math.floor(Math.random() * 400) : 0;
        const hoursBack = 9 - n * 2;
        const recordId = id('cr');

        await query(
          `INSERT INTO call_records (call_record_id, client_id, employee_id, device_id, direction,
                                     state, started_at, duration_seconds, recording_available, recording_uri, is_demo)
           VALUES ($1,$2,$3,'demo-device','OUTBOUND',$4,
                   now() - interval '${dayAgo} days' - interval '${hoursBack} hours',
                   $5,$6,$7, TRUE)`,
          [recordId, client.client_id, emp.employee_id, state, duration,
            state === 'COMPLETED', state === 'COMPLETED' ? `crm://recordings/${recordId}` : null],
        );
        await query(
          `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, call_record_id,
                                         occurred_at, is_demo)
           VALUES ($1,$2,'CALL',$3,$4,$5,
                   now() - interval '${dayAgo} days' - interval '${hoursBack} hours', TRUE)`,
          [id('tl'), client.client_id,
            state === 'COMPLETED'
              ? `Call completed - ${Math.floor(duration / 60)}m ${duration % 60}s (recorded)`
              : `Call ${state.toLowerCase().replace('_', ' ')}`,
            emp.display_name, recordId],
        );
        calls += 1;
      }
    }
  }

  console.log(`Done: ${attendance} attendance records, ${pings} GPS pings, ${calls} calls.`);
  await pool.end();
}

main().catch((e) => { console.error('Demo seed failed:', e.message); process.exit(1); });
