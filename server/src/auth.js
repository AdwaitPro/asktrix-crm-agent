'use strict';
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { query } = require('./db');

const SECRET = process.env.JWT_SECRET;
const TTL = parseInt(process.env.ACCESS_TOKEN_TTL_SECONDS || '900', 10);

if (!SECRET || SECRET.length < 16) {
  throw new Error('JWT_SECRET is missing or too short.');
}

function hashPassword(password, salt = crypto.randomBytes(16).toString('hex')) {
  const hash = crypto.scryptSync(password, salt, 64).toString('hex');
  return { hash, salt };
}

function verifyPassword(password, hash, salt) {
  const candidate = crypto.scryptSync(password, salt, 64);
  const expected = Buffer.from(hash, 'hex');
  // Constant-time compare; lengths must match first or timingSafeEqual throws.
  return candidate.length === expected.length && crypto.timingSafeEqual(candidate, expected);
}

function issueAccessToken(employee, deviceId) {
  return jwt.sign(
    { sub: employee.employee_id, role: employee.role, did: deviceId },
    SECRET,
    { expiresIn: TTL },
  );
}

const newId = (prefix) => `${prefix}_${crypto.randomBytes(12).toString('hex')}`;

/** Bearer-token middleware. Rejects with the API's own error shape, never a stack trace. */
async function requireAuth(req, res, next) {
  const header = req.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ code: 'UNAUTHENTICATED', message: 'Sign in to continue.' });
  }
  let claims;
  try {
    claims = jwt.verify(token, SECRET);
  } catch {
    return res.status(401).json({ code: 'UNAUTHENTICATED', message: 'Session expired. Sign in again.' });
  }
  const { rows } = await query(
    'SELECT * FROM employees WHERE employee_id = $1 AND active = TRUE',
    [claims.sub],
  );
  if (rows.length === 0) {
    return res.status(401).json({ code: 'UNAUTHENTICATED', message: 'Account is no longer active.' });
  }
  req.employee = rows[0];
  req.deviceId = claims.did;
  return next();
}

module.exports = { hashPassword, verifyPassword, issueAccessToken, requireAuth, newId, TTL };
