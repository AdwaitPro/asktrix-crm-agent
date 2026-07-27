# CRM integration guide

**Audience:** a backend developer on the Asktrix CRM team who has never seen this repo.

**What you have to build:** the API in `api/openapi.yaml`. The mobile app is already written against
it and is fully working, so when your implementation matches the contract, the app works — nothing on
the mobile side needs to change beyond a base URL.

There is a **reference implementation** in `server/` (Node + Express + Postgres, ~900 lines). It is
not a mock; it implements the whole contract and the app runs against it end to end today. Read it
when the spec is ambiguous — it is the tiebreaker.

---

## 1. Start here

```bash
cd server
cp .env.example .env      # fill in DATABASE_URL and JWT_SECRET
npm install && npm run seed && npm start
```

Then exercise it:

```bash
curl -s localhost:4010/health

TOKEN=$(curl -s -X POST localhost:4010/auth/login \
  -H 'content-type: application/json' \
  -d '{"employeeCode":"EMP001","password":"asktrix123",
       "device":{"deviceId":"dev-1","manufacturer":"Google","model":"Pixel 7",
                 "osVersion":"16","appVersion":"0.1.0"}}' | jq -r .accessToken)

curl -s localhost:4010/clients -H "authorization: Bearer $TOKEN" | jq
curl -s localhost:4010/clients/CLI-10240 -H "authorization: Bearer $TOKEN" | jq .contact
```

That last response is the one to internalise:

```json
{ "phoneMasked": "98XXXXXX12", "emailMasked": "siv****@gmail.com", "callable": true }
```

The database row behind it holds `9876543212` and `sivakumar@gmail.com`.

---

## 2. The five rules that are not negotiable

### Rule 1 — Never send an unmasked phone number or email to the device

This is the whole point of §4, and the reason the app can be trusted on a phone an employee carries
all day. Masking on the client would be defeated in minutes with an HTTP proxy.

Your API must return **only** `phoneMasked` and `emailMasked`. There is no field anywhere in the
contract for a full value, and there must never be one — the mobile DTOs have a test that fails the
build if someone adds one.

Reference: `server/src/mask.js`, and the response serialiser in `server/src/serialize.js` which is a
deliberate allow-list rather than a spread of the database row. `SELECT *` plus object spread is
exactly how `phone_full` ends up on a handset one day.

There is also a response-scanning tripwire in `server/src/index.js` that blocks any JSON response
containing something that looks like a full number. Worth copying.

### Rule 2 — Authorisation is yours, not the app's

`GET /clients` must return only the caller's assigned clients, and `GET /clients/{id}` must return
**403** when the client belongs to someone else. Do not rely on the app filtering — an app can be
modified; your query cannot.

### Rule 3 — Every mutating endpoint must honour `Idempotency-Key`

The app is offline-first. Actions are queued on the device and retried until they land, so **the same
request will arrive more than once**. Store the key with the response and replay it:

```sql
CREATE TABLE idempotency_keys (
    key TEXT, employee_id TEXT, endpoint TEXT,
    status_code INTEGER, response_body JSONB, created_at TIMESTAMPTZ,
    PRIMARY KEY (key, employee_id)
);
```

Without this, a customer gets marked "Payment received" twice, or gets called twice. See
`idempotent()` in `server/src/index.js`.

### Rule 4 — Optimistic concurrency on client writes

Each client carries a `version`. The app sends back the version it displayed as `expectedVersion`.
On mismatch return **409 with the current record attached**, so the app can resolve without a second
round trip:

```json
{ "code": "CONFLICT", "message": "This client changed since you last loaded it.",
  "current": { "...full ClientDetail..." } }
```

### Rule 5 — Working hours are decided by you

`POST /location/pings` must discard samples outside the employee's working hours and report how many
it dropped:

```json
{ "accepted": 11, "rejectedOutsideWorkingHours": 1 }
```

The device clock and timezone are user-settable, and tracking outside working hours is a DPDP
problem. The app gates client-side as an optimisation only; **your check is the one that counts.**

---

## 3. Telephony — the part that needs the most care

The device never dials and never sees a number. `POST /calls` receives a `clientId`; **you** resolve
the number and ask the telephony provider to bridge the legs. Recommended provider and reasoning:
`docs/research/telephony-cpaas.md`.

Flow to implement:

1. App → `POST /calls {clientId}` → you create a session, return **202** with `callSessionId` and
   `state: "REQUESTED"`.
2. You → provider's click-to-call API with the agent's number and the customer's number, passing your
   `callSessionId` as the provider's correlation field (`custom_identifier` on Acefone).
3. Provider → your webhook as the call progresses. Map their states onto ours: `REQUESTED`,
   `RINGING_AGENT`, `RINGING_CUSTOMER`, `BRIDGED`, `COMPLETED`, `BUSY`, `NO_ANSWER`, `FAILED`.
4. On completion, write a `call_records` row **and** a `CALL` timeline entry.
5. App learns the outcome by FCM push, or by polling `GET /calls/{callSessionId}`.

### Two things that will bite you

**Webhook retries are weak.** Acefone attempts delivery at most twice (30s then 10s timeouts). A
brief blip loses the call outcome permanently. You need:
- **Idempotent webhook handlers** — the same event may arrive twice.
- **A reconciliation sweep** — poll `GET /v1/call/records` periodically and backfill anything the
  webhooks missed. This is not optional.

**Recording covers only the first leg and first transfer.** Documented provider behaviour. If your
flow transfers calls more than once, later segments are not recorded. Disclose that.

### Legal obligations that land on your side

- Store the **device identity** on every call record. The app sends it at login and it is on the
  session; OSP security conditions require the identity of the device used for each call.
- Timestamps **synchronised to IST**.
- **One year** retention for CDRs, tamper-evident, available to DoT/law enforcement on demand.
- `docs/research/india-telecom-legal.md` has the citations. **Read section 3 before going live** —
  TRAI's 1600-series mandate for BFSI senders has deadlines that already passed.

---

## 4. Auth model

- **Access token**: short-lived JWT (900s in the reference). Claims: `sub` (employee id), `role`,
  `did` (device id).
- **Refresh token**: opaque, **single-use, rotating**. Return a new one on every refresh.
- **Reuse detection**: if a refresh token that was already used is presented, that means it was
  stolen — **revoke the entire token family**, not just that token. See `/auth/refresh` in the
  reference.
- **Device binding**: `deviceId` is app-generated and Keystore-backed. Bind the session to it and
  reject refreshes from a different device.
- On `POST /auth/logout`, revoke the family. The app purges its encrypted cache at the same moment.

`attestationStatement` on login is a base64 Keystore key-attestation chain. Verifying it server-side
is the authoritative integrity check; the `checks` block in `POST /device/compliance` is advisory
only and trivially forged.

---

## 5. Push notifications (§24)

Data-only messages, identifiers only:

```json
{ "message": { "token": "<fcm token>",
               "data": { "type": "call_outcome", "callSessionId": "cs_..." },
               "android": { "priority": "HIGH" } } }
```

**No `notification` block, and no customer data.** A `notification` block makes Android render text
on the lock screen outside your control; data-only lets the app fetch detail over an authenticated
channel and decide what to show.

Types the app handles: `call_outcome`, `client_assigned`, `follow_up`. An unknown type is ignored
safely. Every push is only a nudge to sync, so a lost or duplicated push is harmless.

Reference: `server/src/push.js`, including minting the OAuth token from a service-account key.

---

## 6. Checklist before the app points at you

- [ ] `GET /clients` returns only the caller's clients
- [ ] `GET /clients/{id}` returns 403 for someone else's client
- [ ] No response anywhere contains a full phone number or email — grep your own output
- [ ] Replaying an `Idempotency-Key` returns the stored result and does not repeat the action
- [ ] A stale `expectedVersion` returns 409 with `current` attached
- [ ] Out-of-hours location pings are rejected and counted
- [ ] `POST /calls` accepts a `clientId` and never echoes a number back
- [ ] Call records carry device identity and IST timestamps
- [ ] Webhook handlers are idempotent and a reconciliation sweep exists
- [ ] Refresh tokens rotate and reuse revokes the family
- [ ] Error responses use the documented `code` values — the app maps them to retry behaviour, and a
      wrong code means work is either dropped or retried forever

Then set `CRM_BASE_URL_PROD` in the app's `local.properties` and build a release.

## 7. Error codes matter more than you would expect

The app's entire offline engine keys off these. A `503` returned as a permanent error silently drops
an employee's work; a `403` returned as retryable makes devices hammer you forever.

| Code | HTTP | App behaviour |
| --- | --- | --- |
| `UNAUTHENTICATED` | 401 | Refresh once, then sign out |
| `FORBIDDEN` | 403 | **Permanent** — stop, tell the user |
| `NOT_FOUND` | 404 | **Permanent** |
| `VALIDATION_FAILED` | 422 | **Permanent** — show `fieldErrors` |
| `CONFLICT` | 409 | Resolve against `current` |
| `RATE_LIMITED` | 429 | **Retry** with backoff |
| `SERVER_ERROR` | 5xx | **Retry** with backoff |

Anything unrecognised falls back to the HTTP status, so unknown codes degrade safely — but the
specific code is what gives the user a useful message.
