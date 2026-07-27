'use strict';
const crypto = require('crypto');
const fs = require('fs');
const nodePath = require('path');
const { query } = require('./db');

/**
 * Sends FCM pushes via the HTTP v1 API (§24).
 *
 * **Payloads carry identifiers only.** A push travels through Google's infrastructure and lands on a
 * lock screen, so a client name or a masked number would leak in exactly the place §4 exists to
 * protect. The app receives `{type, clientId}` and fetches the detail over the authenticated API.
 *
 * Requires a Firebase service-account JSON — free, from Firebase Console → Project settings →
 * Service accounts → Generate new private key. Point FIREBASE_SERVICE_ACCOUNT at it. Without it,
 * push is skipped and the app falls back to sync-on-open, which is why nothing here throws.
 */

const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
let cachedToken = null;

/**
 * Locates the Firebase service-account key.
 *
 * Checks the env var first, then a conventional path, so dropping the downloaded file into the
 * server directory is enough — no configuration step to forget before a demo.
 */
function serviceAccount() {
  const candidates = [
    process.env.FIREBASE_SERVICE_ACCOUNT,
    nodePath.join(__dirname, '..', 'firebase-service-account.json'),
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return JSON.parse(fs.readFileSync(candidate, 'utf8'));
  }
  return null;
}

/** Mints a Google OAuth access token from the service-account key (JWT bearer grant). */
async function accessToken(account) {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;

  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = {
    iss: account.client_email,
    scope: SCOPE,
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  };
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const unsigned = `${b64(header)}.${b64(claims)}`;
  const signature = crypto.createSign('RSA-SHA256').update(unsigned).sign(account.private_key, 'base64url');

  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!response.ok) throw new Error(`token exchange failed: ${response.status}`);

  const body = await response.json();
  cachedToken = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return cachedToken.value;
}

/**
 * Notifies an employee's devices that something changed.
 *
 * `data`-only message, deliberately: a `notification` block would make Android render a system
 * notification with whatever text it contained, on the lock screen, outside our control. Data-only
 * means the app decides what (if anything) to show, after fetching over an authenticated channel.
 */
async function notifyEmployee(employeeId, type, ids = {}) {
  const account = serviceAccount();
  if (!account) return { sent: 0, skipped: 'FIREBASE_SERVICE_ACCOUNT not configured' };

  const { rows } = await query(
    'SELECT push_token FROM devices WHERE employee_id = $1 AND push_token IS NOT NULL',
    [employeeId],
  );
  if (rows.length === 0) return { sent: 0, skipped: 'no registered devices' };

  const token = await accessToken(account);
  let sent = 0;

  for (const row of rows) {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
      {
        method: 'POST',
        headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
        body: JSON.stringify({
          message: {
            token: row.push_token,
            // Identifiers only. Never a name, never a number, never a status description.
            data: { type, ...ids },
            android: { priority: 'HIGH' },
          },
        }),
      },
    );
    if (response.ok) sent += 1;
    else console.warn('[push] send failed:', response.status, await response.text());
  }
  return { sent };
}

module.exports = { notifyEmployee };
