'use strict';
const crypto = require('crypto');
const { query } = require('./db');

/**
 * Simulated telephony provider.
 *
 * Per ADR-0002 the real provider (Acefone) bridges the two legs server-side: it dials the agent,
 * then the customer, records the call, and reports the outcome by webhook. The device only requests
 * a call and observes state.
 *
 * That indirection is exactly why this simulation is faithful rather than a shortcut — the app sees
 * the same state machine, the same timings and the same failure modes it will see in production.
 * Swapping in the real provider replaces this file and changes nothing above it.
 *
 * Deliberately absent: any phone number. The API never emits one, so neither does the simulator.
 */

const newId = (p) => `${p}_${crypto.randomBytes(12).toString('hex')}`;

// Weighted so most calls connect, but busy / no-answer / failure paths occur often enough that the
// app's error handling is genuinely exercised rather than theoretical.
const OUTCOMES = [
  { state: 'COMPLETED', weight: 70 },
  { state: 'NO_ANSWER', weight: 14 },
  { state: 'BUSY', weight: 10 },
  { state: 'FAILED', weight: 6 },
];

function pickOutcome() {
  const total = OUTCOMES.reduce((sum, o) => sum + o.weight, 0);
  let roll = Math.random() * total;
  for (const o of OUTCOMES) {
    roll -= o.weight;
    if (roll <= 0) return o.state;
  }
  return 'COMPLETED';
}

const setState = (id, state, extra = '') =>
  query(`UPDATE call_sessions SET state = $1 ${extra} WHERE call_session_id = $2`, [state, id]);

const later = (ms, fn) => setTimeout(() => { fn().catch((e) => console.error('[telephony]', e.message)); }, ms);

/**
 * Drives one call through its lifecycle. Timings approximate a real PSTN bridge: the agent leg rings
 * first, then the customer leg, then the call is bridged.
 */
function simulateCall(sessionId, clientId, employee, deviceId) {
  later(800, () => setState(sessionId, 'RINGING_AGENT'));
  later(2600, () => setState(sessionId, 'RINGING_CUSTOMER'));

  later(5200, async () => {
    const outcome = pickOutcome();

    if (outcome !== 'COMPLETED') {
      const reasons = {
        NO_ANSWER: 'Customer did not answer',
        BUSY: 'Customer line busy',
        FAILED: 'Provider could not complete the call',
      };
      await query(
        `UPDATE call_sessions SET state = $1, ended_at = now(), duration_seconds = 0, failure_reason = $2
         WHERE call_session_id = $3`,
        [outcome, reasons[outcome], sessionId],
      );
      await recordAndTimeline(sessionId, clientId, employee, deviceId, outcome, 0, false);
      return;
    }

    await setState(sessionId, 'BRIDGED', ', connected_at = now()');

    // A realistic conversation length: 25s to ~4min.
    const durationSeconds = 25 + Math.floor(Math.random() * 215);
    later(3000, async () => {
      await query(
        `UPDATE call_sessions SET state = 'COMPLETED', ended_at = now(), duration_seconds = $1
         WHERE call_session_id = $2`,
        [durationSeconds, sessionId],
      );
      await recordAndTimeline(sessionId, clientId, employee, deviceId, 'COMPLETED', durationSeconds, true);
    });
  });
}

/**
 * Writes the authoritative call record and the CRM timeline entry (§7, §8).
 *
 * `device_id` is stored because the OSP security conditions require the identity of the device used
 * to make each call, retained for one year (docs/research/india-telecom-legal.md).
 */
async function recordAndTimeline(sessionId, clientId, employee, deviceId, state, durationSeconds, recorded) {
  const recordId = newId('cr');
  await query(
    `INSERT INTO call_records (call_record_id, call_session_id, client_id, employee_id, device_id,
                               direction, state, started_at, duration_seconds,
                               recording_available, recording_uri)
     VALUES ($1,$2,$3,$4,$5,'OUTBOUND',$6, now() - make_interval(secs => $7::int), $7::int, $8, $9)`,
    [recordId, sessionId, clientId, employee.employee_id, deviceId, state,
      durationSeconds, recorded, recorded ? `crm://recordings/${recordId}` : null],
  );

  const summary = state === 'COMPLETED'
    ? `Call completed — ${Math.floor(durationSeconds / 60)}m ${durationSeconds % 60}s${recorded ? ' (recorded)' : ''}`
    : `Call ${state.toLowerCase().replace('_', ' ')}`;

  await query(
    `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, call_record_id)
     VALUES ($1,$2,'CALL',$3,$4,$5)`,
    [newId('tl'), clientId, summary, employee.display_name, recordId],
  );
  await query('UPDATE clients SET last_interaction_at = now() WHERE client_id = $1', [clientId]);
}

module.exports = { simulateCall };
