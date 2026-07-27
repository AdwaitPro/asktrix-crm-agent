# Requirement traceability — §1 to §30

Source: `docs/requirements.pdf`. Last audited **2026-07-27**.

**Status meanings**

| Status | Meaning |
| --- | --- |
| `DONE` | Implemented and verified running |
| `PARTIAL` | Implemented against the dev CRM; needs the real CRM or a paid service to be complete |
| `BLOCKED` | Cannot progress without something external (a purchase, a credential, a CRM API) |
| `PLATFORM-IMPOSSIBLE` | Cannot be done as literally written on modern Android; the business intent is met another way |

Verification below means **observed running**, not "the code looks right".

---

| § | Requirement | Where | Verified how | Status |
| --- | --- | --- | --- | --- |
| **1** | Secure employee ops app; never expose customer info | Whole app; `docs/adr/0003` | Masking, no-cleartext, FLAG_SECURE, encrypted cache all verified below | `DONE` |
| **2** | Six user roles | `EmployeeDto.role`, `employees.role` in the CRM schema | 3 roles seeded; login returns role + permissions | `DONE` |
| **3** | Fetch from CRM APIs; **no permanent customer DB**; temporary encrypted cache | `:core:database` (Room + SQLCipher), `CachePurger` | Every row carries `expiresAtMillis`; expired rows filtered *in the DAO query*. Purge deletes the DB **and destroys the Keystore key**, so residue is undecryptable. Sign-out wired to purge. | `DONE` |
| **4** | **Mask phone `98XXXXXX12` and email `siv****@gmail.com`** | Server-side `server/src/mask.js`; `MaskedContactDto`; `docs/adr/0003` | **DB holds `9876543212` / `sivakumar@gmail.com`; API returns `98XXXXXX12` / `siv****@gmail.com`.** No DTO, entity or domain model has a field for a full value. `DtoPrivacyTest` fails the build if one is added. A response-scanning tripwire blocks any leak. | `DONE` |
| **5** | Click-to-call only; no dial pad, no number copying, no visible number | `CallRepository`, `MaskedContactCard`; `docs/adr/0002` | Call placed from the emulator by `clientId` alone. **APK declares no `CALL_PHONE`.** No dial pad exists; contact fields have no copy affordance or selection. | `DONE` |
| **6** | Record, encrypt, upload, delete locally | Server-side; `docs/adr/0002` | Recording happens at the telephony provider. Device holds no audio and has **no `RECORD_AUDIO` permission**. `recording_uri` is deliberately not projected to the device. | `PLATFORM-IMPOSSIBLE` as written → met server-side. See below. |
| **7** | Sync in/out/missed calls, duration, timestamps, employee + client id | `CallRepository.history`, `call_records` | Call history screen shows real records with duration and recorded flag. **`READ_CALL_LOG` not declared** — the CRM is authoritative, so no Play sensitive-permission review. | `DONE` |
| **8** | CRM timeline auto-updates on every interaction | `timeline_entries`; `ClientRepository.observeTimeline` | Status change and completed call both wrote timeline entries, observed on device | `DONE` |
| **9** | Detect connectivity; sync without manual intervention | `:core:sync` — `Outbox`, `OutboxWorker`, `ConnectivityObserver` | Observer requires `NET_CAPABILITY_VALIDATED`, so a captive portal is not mistaken for connectivity. Backoff is exponential with full jitter, capped at 30 min; `OutboxBackoffTest` pins all of it. | `DONE` |
| **10** | GPS every 10 minutes during working hours | `LocationTrackingService`; server-side gating | Foreground service, `foregroundServiceType="location"`, 10-minute cadence. WorkManager cannot do this (15-min floor) — reasoning in the service KDoc. **Working hours enforced server-side**; verified: a two-ping batch returned `accepted:1, rejectedOutsideWorkingHours:1`. | `DONE` |
| **11** | Check-in/out with GPS, timestamp, optional photo | `:feature:attendance`, `AttendanceRepository` | Screen runs; requires a fix before recording; permission gate blocks check-in until granted. **Photo upload endpoint exists (`PUT /attendance/{id}/photo`); the camera UI is not built yet.** | `PARTIAL` |
| **12** | Dashboard: assigned clients, pending work, follow-ups | `:feature:dashboard` | Live on device: 6 assigned clients, three filters with counts, overdue follow-up highlighted | `DONE` |
| **13** | Six quick status buttons | `QUICK_STATUSES`, `ClientDetailScreen` | All six render; current status disabled rather than hidden; writes go through the outbox with `expectedVersion` | `DONE` |
| **14–20** | Encrypt data, block screenshots, detect root/debug/emulator, disable copy/share/export, restrict browsers, Play Store, unknown APKs, settings, uninstall | App: `FLAG_SECURE`, SQLCipher, Keystore, `DeviceComplianceReporter`. EMM: `mdm/policy.json` | **FLAG_SECURE verified — `screencap` returned a fully black frame.** Cleartext refused by network security config. No copy affordance on contact fields. Store/browser/uninstall/settings restrictions are EMM policy keys, listed individually in `policy.json`. | `PARTIAL` — app side `DONE`; policy side needs an EMM subscription |
| **21** | Factory Reset Protection via Device Owner | `mdm/policy.json` → `factoryResetDisabled` | Policy authored; requires an enrolled device to demonstrate | `BLOCKED` — needs EMM |
| **22** | Auto-start after boot | `BootReceiver` | Enqueues WorkManager only. Deliberately does **not** start the location service: Android 15 restricts FGS types startable from `BOOT_COMPLETED`, and tracking must resume at check-in so an overnight reboot does not track someone off-shift. | `DONE` |
| **23** | Encrypted offline storage with background sync | `:core:database`, `:core:sync` | SQLCipher passphrase is 32 random bytes sealed under a Keystore key. Outbox recovers `IN_FLIGHT` items after process death; every request carries a stable idempotency key. | `DONE` |
| **24** | FCM push notifications | `AsktrixMessagingService` | Firebase project created and wired; build works with or without `google-services.json`. **Push payloads carry identifiers only, never customer data** — a push lands on a lock screen. Not yet verified with a real push send. | `PARTIAL` |
| **25–27** | Admin dashboard: monitor status, GPS, recordings, attendance, productivity; enforce USB, Bluetooth, Nearby Share, file access, software installation | `mdm/policy.json` + `statusReportingSettings`; CRM web app | USB, Bluetooth and install restrictions map to documented policy keys. **Nearby Share has no policy key — documented, not faked.** The admin *dashboard* is the existing CRM web app plus the EMM console, not the mobile app. | `BLOCKED` — needs EMM; dashboard ownership needs client confirmation |
| **28** | Kotlin, Compose, WorkManager, encrypted Room, Retrofit, Keystore, REST, JWT, HTTPS/TLS, RBAC, FCM, Play Location, Device Owner, Managed Play, MDM | Whole app | Every item present. HTTPS enforced in release by network security config; RBAC is server-issued permissions; Device Owner and Managed Play are EMM-side by design. | `DONE` |
| **29** | Workflow: CRM assigns → app receives → open client → click-to-call → recorded → uploaded → status updated → timeline updated | End to end | **Walked the whole chain on the emulator against Neon**, ending with a timeline entry reading "Call completed — 1m 59s (recorded)". | `DONE` |
| **30** | FRP, install blocking, Settings blocking, uninstall prevention require Device Owner + MDM | `docs/adr/0004` | Confirmed and extended: we also found that Google restricts AMAPI to commercial EMM vendors and that **Play Protect has blocked non-approved custom DPCs since 2026**. So the app must not be the DPC. | `DONE` (as analysis) |

---

## §6 — the one requirement that cannot be met as written

**Requirement:** *"Automatically record, encrypt, upload to CRM, then delete locally after successful upload."*

**Why not, on any modern Android:**
- The official call-recording API was removed; capturing the call stream was blocked at Android 10;
  the AccessibilityService workaround was banned by Play policy at Android 11.
- Device Owner status does **not** grant call-audio access.
- `ROLE_DIALER` does not grant it either.

**And in India specifically, an in-app SIP path is also closed:** the Unified Licence Internet
Service authorisation prohibits *"Voice communication to and from a telephone connected to
PSTN/PLMN"* and prohibits E.164→IP translation, and carrying inter-city domestic voice over our own
network is squarely the OSP toll-bypass definition. `android.net.sip` is dead as well — deprecated at
API 31 and its platform service removed, so `newInstance()` returns `null`.

**How the business intent is met:** the telephony provider bridges both legs and records
server-side. Every call is recorded, encrypted and attached to the CRM timeline. What changes is
*where* — and as a side effect the customer's number never reaches the device at all, which makes §4
and §5 genuinely enforceable rather than cosmetic.

**What is lost:** nothing the requirement was actually asking for. Full detail in
`docs/adr/0002-telephony-architecture.md`.

---

## What is blocked, and on what

| Blocker | Blocks | Cost |
| --- | --- | --- |
| **Asktrix CRM has no APIs** | §3, §4, §8 against real customer data | ₹0 — CRM team implements `api/openapi.yaml` |
| **No telephony subscription** | §5, §6, §7 with real calls | Acefone ₹1,599/user/mo, min 6 seats |
| **No EMM subscription / enrolled device** | §14–§21, §25–§27 enforcement | ₹0 on self-hosted Fleet, or ~$1.08/device/mo |
| **Attendance photo UI** | §11 fully | ₹0 — remaining work on my side |

Until then the app runs end to end against the development CRM in `server/`, backed by Neon
Postgres, with a simulated telephony provider that walks the same state machine the real one will.

## Verification commands

```bash
source scripts/env.sh
./gradlew assembleDebug lintDebug testDebugUnitTest detekt
```

Last run: **all four pass. 23 tests, 0 failures.**
