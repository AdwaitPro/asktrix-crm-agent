# Asktrix CRM Mobile Agent App

Secure Android employee-operations app for company-owned, Device-Owner-managed handsets, integrated
with the Asktrix CRM.

Requirements: `docs/requirements.pdf` (§1–§30). Status of every one: **`docs/TRACEABILITY.md`**.

## What it does

Field and office staff see the clients assigned to them, call those clients without ever seeing their
phone number, update case status, record attendance with GPS, and keep working when the network
drops. Everything they do reaches the CRM exactly once, whenever connectivity allows.

## The three decisions that shape everything else

**1. Customer contact details are masked on the server and never sent to the device.**
The database holds `9876543212`; the API returns `98XXXXXX12`. No DTO, cache entity or domain model
has a field for a full number, and a test fails the build if one is added. The threat model is an
employee with a company phone in hand all day — client-side masking would be defeated in minutes with
an HTTP proxy. See `docs/adr/0003-server-side-pii-masking.md`.

**2. Calls are bridged by a telephony provider, not dialled by the phone.**
On-device call recording is impossible on modern Android, and building our own SIP-to-PSTN path is
unlawful in India. So the app asks the CRM to place a call by `clientId`; the provider dials both
legs and records server-side. The APK therefore declares **no `CALL_PHONE`, `READ_CALL_LOG`,
`RECORD_AUDIO` or `READ_PHONE_STATE`** — which also keeps it out of Google Play's sensitive-permission
review entirely. See `docs/adr/0002-telephony-architecture.md`.

**3. This app is not the device management agent.**
Google restricts the Android Management API to commercial EMM vendors, and Play Protect has blocked
non-approved custom DPCs at provisioning since 2026. The app is a normal managed app; restrictions
come from EMM policy (`mdm/policy.json`). See `docs/adr/0004-device-management.md`.

## Running it

### Prerequisites

JDK 21 and the Android SDK. On this machine both are keg-only Homebrew installs, so source the
environment first:

```bash
source scripts/env.sh
```

### 1. Start the development CRM

The real Asktrix CRM exposes no APIs yet, so `server/` implements `api/openapi.yaml` against
Postgres. It is a genuine backend, not a fixture server — offline sync, conflicts and idempotency are
exercised for real.

```bash
cd server
cp .env.example .env       # then fill in DATABASE_URL and JWT_SECRET
npm install
npm run seed               # applies the schema and seeds 3 employees, 12 clients
npm start                  # http://0.0.0.0:4010
```

Sign in with **`EMP001`**, **`EMP002`** or **`EMP003`** / password **`asktrix123`**.

### 2. Build and install the app

```bash
source scripts/env.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build points at `http://10.0.2.2:4010/` — the host machine as seen from the Android
emulator. Override with `CRM_BASE_URL_DEV` in `local.properties`.

> **Screenshots are blocked by default.** `FLAG_SECURE` is on, so `adb shell screencap` returns a
> black frame — that is §14–§20 working. For QA capture, set `asktrix.allowScreenshots=true` in
> `local.properties`. It has **no effect on release builds**, where the flag is hardcoded false.

### Verification gate

A change is not done until this passes:

```bash
source scripts/env.sh
./gradlew assembleDebug lintDebug testDebugUnitTest detekt
```

## Architecture

Dependencies point inward. `:feature:*` depends on `:core:*`; `:core:*` never depends on a feature.

```
:app                  Application, navigation, DI root, FCM, boot receiver
:core:common          Result and error model, dispatchers, time source, logging
:core:data            Offline-first repositories and the domain model
:core:designsystem    Compose M3 theme, components, masked-field primitives
:core:network         Retrofit, auth interceptor, token refresh, error mapping
:core:database        Encrypted Room (SQLCipher), DAOs, outbox tables, cache purge
:core:datastore       Encrypted session storage
:core:security        Keystore crypto, device identity
:core:sync            Outbox engine, WorkManager, connectivity observer
:core:telephony       Call session state
:core:location        GPS sampling, working-hours foreground service
:core:mdm             Managed-config consumer and compliance reporter
:feature:auth         Login and device binding
:feature:dashboard    Assigned clients, filters (§12)
:feature:client       Client detail, masked contact, timeline, status actions (§4, §8, §13)
:feature:calls        Call history (§7)
:feature:attendance   Check-in/out with GPS (§11)
:feature:settings     Device status and sign-out (§26)
```

`:core:data` was added to the original plan; the reasoning is in its build file and
`docs/TRACEABILITY.md`.

## Invariants — do not break these

1. **No unmasked customer phone number or email may reach the device.** Not in a DTO, not in the
   cache, not in a log, not in a push payload.
2. **No permanent customer database on the device.** The cache is encrypted, TTL'd, and purged on
   logout, integrity failure and remote wipe.
3. **Every write goes through the outbox** with an idempotency key generated once at enqueue time.
4. **Nothing sensitive reaches logcat.** Use `AsktrixLog`; raw `android.util.Log` fails the build via
   Detekt and is stripped by R8 in release.
5. **The device never dials a customer number.**

## Documentation

| File | What it is |
| --- | --- |
| `docs/TRACEABILITY.md` | Every requirement §1–§30 and how it was verified |
| `docs/OPEN_QUESTIONS.md` | What is blocked, on whom, and what it costs |
| `docs/ZERO_COST_SETUP.md` | Running the whole thing for ₹0 |
| `docs/ENROLLMENT_RUNBOOK.md` | Enrolling a handset, including per-OEM battery settings |
| `docs/FIREBASE_SETUP.md` | Creating the Firebase project (free) |
| `docs/COMPLIANCE.md` | DPDP Act, recording consent, retention matrix |
| `docs/adr/` | The decisions and why |
| `docs/research/` | Citation-backed research behind those decisions |
| `api/openapi.yaml` | The CRM contract — **a proposal until the CRM team confirms** |
| `mdm/policy.json` | EMM policy set for §14–§21, §25–§27 |

## Security posture

- **At rest:** SQLCipher with a 32-byte random passphrase sealed under a hardware-backed Keystore key
  (StrongBox when available). Cache purge destroys the key, so residue cannot be decrypted.
- **In transit:** cleartext refused by network security config; the debug variant permits it only for
  loopback and only in debug.
- **Session:** short-lived access token plus single-use rotating refresh token, bound to a device. A
  replayed refresh token revokes the whole family.
- **On screen:** `FLAG_SECURE` on the only window, applied before first composition.
- **Integrity:** Keystore key attestation verified server-side. Client-side root and emulator checks
  are reported as signals, never trusted as controls.
- **Logging:** no OkHttp logging interceptor at any build type — request bodies carry client data.
