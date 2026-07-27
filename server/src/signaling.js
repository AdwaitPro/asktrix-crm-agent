'use strict';
const crypto = require('crypto');
const { WebSocketServer } = require('ws');
const { query } = require('./db');

/**
 * WebRTC signalling for app-to-app calling (§5, §6).
 *
 * Why this exists alongside the CPaaS path: routing voice onto India's public telephone network
 * requires a licensed carrier, and building our own PSTN gateway is prohibited. Voice **between our
 * own endpoints over data** is not — the legal research is explicit that app-to-app / closed-user-
 * group voice over data carries no licence issue.
 *
 * That distinction has a consequence worth stating plainly: because the app owns the media path,
 * **recording becomes both lawful and technically possible**, which is what §6 asked for in the
 * first place and what the PSTN route could never deliver on-device.
 *
 * This server only relays signalling messages. The audio itself flows peer to peer, so it costs
 * nothing to run and no media ever transits our infrastructure unless we choose to record it.
 *
 * The customer never installs anything: they open a one-time link.
 */

// In-memory: a call is a live thing, and a signalling session that outlives a process restart is
// meaningless anyway. Durable call *records* live in Postgres.
const rooms = new Map();

const newId = (p) => `${p}_${crypto.randomBytes(10).toString('hex')}`;

function attach(server) {
  const wss = new WebSocketServer({ server, path: '/rtc' });

  wss.on('connection', (socket, request) => {
    const url = new URL(request.url, 'http://localhost');
    const roomId = url.searchParams.get('room');
    const role = url.searchParams.get('role');

    if (!roomId || !['agent', 'customer'].includes(role)) {
      socket.close(4000, 'room and role are required');
      return;
    }

    const room = rooms.get(roomId);
    if (!room) {
      socket.close(4004, 'no such call');
      return;
    }
    if (room[role]) {
      socket.close(4009, 'that side of the call is already connected');
      return;
    }

    room[role] = socket;
    socket.roomId = roomId;
    socket.role = role;

    const peerRole = role === 'agent' ? 'customer' : 'agent';

    // Tell each side whether the other is already present, so the agent knows when to make the offer.
    socket.send(JSON.stringify({ type: 'joined', role, peerPresent: Boolean(room[peerRole]) }));
    if (room[peerRole]) {
      room[peerRole].send(JSON.stringify({ type: 'peer-joined', role }));
      socket.send(JSON.stringify({ type: 'peer-joined', role: peerRole }));
    }

    socket.on('message', (raw) => {
      let message;
      try {
        message = JSON.parse(raw.toString());
      } catch {
        return;
      }

      // Relay only. The server never inspects or stores SDP or ICE candidates.
      if (['offer', 'answer', 'ice', 'hangup'].includes(message.type)) {
        const peer = rooms.get(socket.roomId)?.[peerRole];
        if (peer && peer.readyState === peer.OPEN) peer.send(JSON.stringify(message));
      }

      if (message.type === 'connected') markConnected(socket.roomId).catch(() => {});
      if (message.type === 'hangup') finish(socket.roomId, message.durationSeconds || 0).catch(() => {});
    });

    socket.on('close', () => {
      const current = rooms.get(socket.roomId);
      if (!current) return;
      current[socket.role] = null;
      const peer = current[peerRole];
      if (peer && peer.readyState === peer.OPEN) {
        peer.send(JSON.stringify({ type: 'peer-left' }));
      }
    });
  });

  return wss;
}

/** Creates a room and returns the ids both sides need. */
function createRoom({ callSessionId, clientId, employeeId, deviceId }) {
  const roomId = newId('room');
  // A single-use token in the customer link, so a forwarded link cannot join someone else's call.
  const customerToken = crypto.randomBytes(16).toString('hex');
  rooms.set(roomId, {
    roomId,
    customerToken,
    callSessionId,
    clientId,
    employeeId,
    deviceId,
    agent: null,
    customer: null,
    createdAt: Date.now(),
  });
  return { roomId, customerToken };
}

const getRoom = (roomId) => rooms.get(roomId);

async function markConnected(roomId) {
  const room = rooms.get(roomId);
  if (!room || room.connectedAt) return;
  room.connectedAt = Date.now();
  await query(
    "UPDATE call_sessions SET state = 'BRIDGED', connected_at = now() WHERE call_session_id = $1",
    [room.callSessionId],
  );
}

/** Closes out the call: session state, durable call record, and the CRM timeline entry (§7, §8). */
async function finish(roomId, durationSeconds) {
  const room = rooms.get(roomId);
  if (!room || room.finished) return;
  room.finished = true;

  const connected = Boolean(room.connectedAt);
  const state = connected ? 'COMPLETED' : 'NO_ANSWER';
  const duration = connected ? Math.max(0, Math.round(durationSeconds)) : 0;

  await query(
    `UPDATE call_sessions SET state = $1, ended_at = now(), duration_seconds = $2
     WHERE call_session_id = $3`,
    [state, duration, room.callSessionId],
  );

  const recordId = newId('cr');
  await query(
    `INSERT INTO call_records (call_record_id, call_session_id, client_id, employee_id, device_id,
                               direction, state, started_at, duration_seconds,
                               recording_available, recording_uri)
     VALUES ($1,$2,$3,$4,$5,'OUTBOUND',$6, now() - make_interval(secs => $7::int), $7::int, $8, $9)`,
    [recordId, room.callSessionId, room.clientId, room.employeeId, room.deviceId, state,
      duration, Boolean(room.recordingPath), room.recordingPath || null],
  );

  const employee = (await query('SELECT display_name FROM employees WHERE employee_id = $1',
    [room.employeeId])).rows[0];

  await query(
    `INSERT INTO timeline_entries (entry_id, client_id, kind, summary, actor_name, call_record_id)
     VALUES ($1,$2,'CALL',$3,$4,$5)`,
    [newId('tl'), room.clientId,
      connected
        ? `Call completed, ${Math.floor(duration / 60)}m ${duration % 60}s`
          + `${room.recordingPath ? ' (recorded)' : ''}`
        : 'Call not answered',
      employee?.display_name || 'Agent', recordId],
  );

  await query('UPDATE clients SET last_interaction_at = now() WHERE client_id = $1', [room.clientId]);

  // Keep the room briefly so a late recording upload can still attach itself.
  setTimeout(() => rooms.delete(roomId), 120_000);
  return recordId;
}

module.exports = { attach, createRoom, getRoom, finish };
