'use strict';
const crypto = require('crypto');
const { query } = require('./db');

/**
 * WebRTC call setup over HTTP (§5, §6).
 *
 * Why WebRTC at all: routing voice onto India's public telephone network requires a licensed
 * carrier, and building our own PSTN gateway is prohibited. Voice **between our own endpoints over
 * data** is not. The legal research is explicit that app-to-app / closed-user-group voice over data
 * carries no licence issue.
 *
 * That has a consequence worth stating plainly: because the app owns the media path, **recording
 * becomes both lawful and technically possible on the device** - which is exactly what §6 asked
 * for and what the carrier route could never deliver.
 *
 * Why HTTP rather than a WebSocket: call setup is a handful of small messages. Polling for a few
 * seconds is entirely adequate, and it means the service runs on any host, including serverless
 * platforms where a socket cannot be held open. The audio never passes through here either way;
 * it flows peer to peer.
 */

const newId = (p) => `${p}_${crypto.randomBytes(10).toString('hex')}`;

async function createRoom({ callSessionId, clientId, employeeId, deviceId }) {
  const roomId = newId('room');
  const customerToken = crypto.randomBytes(16).toString('hex');
  await query(
    `INSERT INTO rtc_rooms (room_id, call_session_id, client_id, employee_id, device_id, customer_token)
     VALUES ($1,$2,$3,$4,$5,$6)`,
    [roomId, callSessionId, clientId, employeeId, deviceId || null, customerToken],
  );
  return { roomId, customerToken };
}

const loadRoom = async (roomId) =>
  (await query('SELECT * FROM rtc_rooms WHERE room_id = $1', [roomId])).rows[0] || null;

function register(app, asyncRoute) {
  /** Announces a side has joined and reports whether the peer is already present. */
  app.post('/rtc/:roomId/join', asyncRoute(async (req, res) => {
    const room = await loadRoom(req.params.roomId);
    if (!room) return res.status(404).json({ code: 'NOT_FOUND', message: 'This call link is not valid.' });
    if (room.finished_at) return res.status(410).json({ code: 'NOT_FOUND', message: 'This call has ended.' });

    const role = req.body?.role;
    if (!['agent', 'customer'].includes(role)) {
      return res.status(422).json({ code: 'VALIDATION_FAILED', message: 'role is required.' });
    }
    // The customer's link carries a single-use token; a forwarded link will not match.
    if (role === 'customer' && req.body?.token !== room.customer_token) {
      return res.status(403).json({ code: 'FORBIDDEN', message: 'This call link is not valid.' });
    }

    const column = role === 'agent' ? 'agent_joined' : 'customer_joined';
    await query(`UPDATE rtc_rooms SET ${column} = TRUE WHERE room_id = $1`, [room.room_id]);

    const peerPresent = role === 'agent' ? room.customer_joined : room.agent_joined;
    res.json({ ok: true, peerPresent, clientName: req.body?.name || null });
  }));

  /** Queues a signalling message for the other side. The server never inspects SDP or candidates. */
  app.post('/rtc/:roomId/signal', asyncRoute(async (req, res) => {
    const room = await loadRoom(req.params.roomId);
    if (!room) return res.status(404).json({ code: 'NOT_FOUND', message: 'No such call.' });

    const { from, message } = req.body || {};
    if (!['agent', 'customer'].includes(from) || !message?.type) {
      return res.status(422).json({ code: 'VALIDATION_FAILED', message: 'from and message are required.' });
    }

    const forRole = from === 'agent' ? 'customer' : 'agent';
    await query(
      'INSERT INTO rtc_signals (room_id, for_role, payload) VALUES ($1,$2,$3)',
      [room.room_id, forRole, JSON.stringify(message)],
    );

    if (message.type === 'connected' && !room.connected_at) {
      await query('UPDATE rtc_rooms SET connected_at = now() WHERE room_id = $1', [room.room_id]);
      await query(
        "UPDATE call_sessions SET state = 'BRIDGED', connected_at = now() WHERE call_session_id = $1",
        [room.call_session_id],
      );
    }
    if (message.type === 'hangup') {
      await finish(room.room_id, message.durationSeconds || 0);
    }
    res.json({ ok: true });
  }));

  /** Returns messages queued for this side since the given id, plus whether the peer is present. */
  app.get('/rtc/:roomId/poll', asyncRoute(async (req, res) => {
    const room = await loadRoom(req.params.roomId);
    if (!room) return res.status(404).json({ code: 'NOT_FOUND', message: 'No such call.' });

    const role = req.query.role;
    const since = parseInt(req.query.since || '0', 10);
    if (!['agent', 'customer'].includes(role)) {
      return res.status(422).json({ code: 'VALIDATION_FAILED', message: 'role is required.' });
    }

    const { rows } = await query(
      'SELECT id, payload FROM rtc_signals WHERE room_id = $1 AND for_role = $2 AND id > $3 ORDER BY id',
      [room.room_id, role, since],
    );

    res.json({
      messages: rows.map((r) => ({ id: Number(r.id), ...r.payload })),
      peerPresent: role === 'agent' ? room.customer_joined : room.agent_joined,
      finished: Boolean(room.finished_at),
    });
  }));

  /** Receives the recording captured on the agent side (§6). */
  app.post('/rtc/:roomId/recording', express_raw(), asyncRoute(async (req, res) => {
    const room = await loadRoom(req.params.roomId);
    if (!room) return res.status(404).json({ code: 'NOT_FOUND', message: 'No such call.' });
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) {
      return res.status(422).json({ code: 'VALIDATION_FAILED', message: 'Expected audio bytes.' });
    }

    const record = (await query(
      'SELECT call_record_id FROM call_records WHERE call_session_id = $1 ORDER BY started_at DESC LIMIT 1',
      [room.call_session_id],
    )).rows[0];
    if (!record) return res.status(409).json({ code: 'CONFLICT', message: 'Call record not written yet.' });

    await query(
      `INSERT INTO call_recordings (call_record_id, mime_type, bytes)
       VALUES ($1,$2,$3) ON CONFLICT (call_record_id) DO UPDATE SET bytes = EXCLUDED.bytes`,
      [record.call_record_id, req.get('content-type') || 'audio/webm', req.body],
    );
    await query(
      'UPDATE call_records SET recording_available = TRUE WHERE call_record_id = $1',
      [record.call_record_id],
    );
    res.status(204).end();
  }));
}

/** Closes the call out: session state, durable record, and the CRM timeline entry (§7, §8). */
async function finish(roomId, durationSeconds) {
  const room = await loadRoom(roomId);
  if (!room || room.finished_at) return null;

  await query('UPDATE rtc_rooms SET finished_at = now() WHERE room_id = $1', [roomId]);

  const connected = Boolean(room.connected_at);
  const state = connected ? 'COMPLETED' : 'NO_ANSWER';
  const duration = connected ? Math.max(0, Math.round(durationSeconds)) : 0;

  await query(
    `UPDATE call_sessions SET state = $1, ended_at = now(), duration_seconds = $2
     WHERE call_session_id = $3`,
    [state, duration, room.call_session_id],
  );

  const recordId = newId('cr');
  await query(
    `INSERT INTO call_records (call_record_id, call_session_id, client_id, employee_id, device_id,
                               direction, state, started_at, duration_seconds, recording_available)
     VALUES ($1,$2,$3,$4,$5,'OUTBOUND',$6, now() - make_interval(secs => $7::int), $7::int, FALSE)`,
    [recordId, room.call_session_id, room.client_id, room.employee_id, room.device_id, state, duration],
  );

  const employee = (await query('SELECT display_name FROM employees WHERE employee_id = $1',
    [room.employee_id])).rows[0];

  await query(
    `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, call_record_id)
     VALUES ($1,$2,'CALL',$3,$4,$5)`,
    [newId('tl'), room.client_id,
      connected
        ? `Call completed, ${Math.floor(duration / 60)}m ${duration % 60}s`
        : 'Call not answered',
      employee?.display_name || 'Agent', recordId],
  );
  await query('UPDATE clients SET last_interaction_at = now() WHERE client_id = $1', [room.client_id]);
  return recordId;
}

// Raw body parser for the audio upload, kept local so the main app keeps its JSON parser.
function express_raw() {
  const express = require('express');
  return express.raw({ type: ['audio/*', 'application/octet-stream'], limit: '25mb' });
}

module.exports = { register, createRoom, loadRoom, finish };
