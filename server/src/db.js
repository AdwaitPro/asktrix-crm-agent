'use strict';
const { Pool } = require('pg');

if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is not set. Copy server/.env.example to server/.env and fill it in.');
}

// Neon pooled endpoint. Keep the pool small — Neon's pooler multiplexes, and a large local pool
// just holds idle connections open.
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  // Neon's free tier auto-suspends an idle database and cold-starts it on the next connection,
  // which regularly takes longer than a typical 10s timeout. A short timeout here surfaces as a
  // 500 that looks like a bug in the query. Generous connect, aggressive idle.
  connectionTimeoutMillis: 30_000,
  idleTimeoutMillis: 20_000,
  keepAlive: true,
  // A statement that has not returned in 20s is wedged, not slow.
  statement_timeout: 20_000,
});

pool.on('error', (err) => {
  console.error('[db] idle client error:', err.message);
});

const query = (text, params) => pool.query(text, params);

async function tx(fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

module.exports = { pool, query, tx };
