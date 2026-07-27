# Zero-cost build and pilot plan

**Constraint:** the Asktrix CRM exposes no APIs yet, and no paid subscription is being taken today.
**Conclusion: the entire app can be built, run, tested and demonstrated for ₹0.** Four of the thirty
requirements cannot be *proven against live infrastructure* without money, and they are named
precisely at the bottom. Everything else works free.

The rule applied throughout: **every paid service sits behind an interface with a free implementation
selected by build variant.** Swapping to the paid one later is a config change, not a rewrite.

## The substitution table

| Need | Paid option (later) | **Free option (now)** | Cost |
| --- | --- | --- | --- |
| CRM backend API | Real Asktrix CRM | **`api/openapi.yaml` + Prism mock server** (MIT) serving realistic seeded data | ₹0 |
| Click-to-call, recording | Acefone ₹1,599/user/mo, min 6 seats | **Mock telephony provider** — full call lifecycle, states, durations, recording URL | ₹0 |
| Device Owner validation | EMM subscription | **TestDPC** (Apache-2.0) via `adb shell dpm set-device-owner` on one factory-reset handset | ₹0 |
| Fleet management | ManageEngine ~$1.08/device/mo | **Fleet** (MIT, self-hosted, AMAPI-backed) free tier | ₹0 + a VM |
| Push notifications (§24) | — | **Firebase Cloud Messaging, Spark plan** | ₹0 |
| Device integrity (§14–§20) | Play Integrity (needs Play distribution) | **Android Keystore hardware key attestation** — no Play account required | ₹0 |
| App distribution | Play Console $25 one-time + managed Google Play | **Sideload the APK** for pilot; Fleet hosts private APKs | ₹0 |
| Crash reporting | — | Firebase Crashlytics free tier | ₹0 |
| Device to test on | — | **Android emulator** (SDK, already installed) | ₹0 |

## 1. CRM — contract-first against a free mock

The CRM has no API, so `api/openapi.yaml` is written as a **proposal** — clearly labelled as such, and
the artifact the CRM team implements against. It is not a guess dressed up as documentation.

**Prism** (Stoplight, MIT licence) serves that OpenAPI file as a live HTTP mock: real status codes,
schema-validated responses, and every documented error path. The app is built entirely against it.

```bash
npx --yes @stoplight/prism-cli mock api/openapi.yaml --port 4010 --host 0.0.0.0
```

The debug build already points at it: `CRM_BASE_URL_DEV` defaults to `http://10.0.2.2:4010/`
(`10.0.2.2` is the host machine as seen from the Android emulator). Override in `local.properties`
without touching code.

**What this buys:** the entire app — auth, dashboard, client detail, masked fields, status updates,
attendance, the offline outbox, sync and conflict handling — is fully exercisable and testable today.
When the real CRM appears, one URL changes.

**Critically, the mock enforces the §4 privacy invariant.** The schema has no field anywhere carrying
a full phone number or email, only `phoneMasked` / `emailMasked`. The app therefore *cannot* leak what
it never receives, and that property is locked in from the first line of code rather than retrofitted.

## 2. Telephony — a mock provider behind the real interface

Per `docs/adr/0002-telephony-architecture.md` the app never dials, never records, and never reads the
call log. It asks the CRM to place a call and observes the outcome. That indirection is exactly what
makes a free mock viable.

`:core:telephony` defines one interface. Two implementations:

- **`MockCallProvider`** (debug) — accepts a `clientId`, walks a realistic state machine
  (`REQUESTED → RINGING_AGENT → BRIDGED → COMPLETED`, plus `BUSY` / `NO_ANSWER` / `FAILED`), emits a
  duration, and returns a recording URL pointing at a locally served audio file. Selected by
  `BuildConfig.USE_MOCK_TELEPHONY`.
- **`AcefoneCallProvider`** (release) — the real integration, activated when the subscription exists.

Every downstream requirement is genuinely testable this way: call history (§7), CRM timeline updates
(§8), the offline recording queue (§23), and status transitions (§13). The only thing the mock cannot
prove is that Acefone's servers behave as documented.

> **A real call cannot be placed for ₹0.** Some Indian CPaaS vendors offer trials — confirm terms at
> signup, I have not verified any trial. The one genuinely free way to place a real call is dialling
> from the device's own SIM, and **I have deliberately not built that**: it puts the customer's real
> number in the system call log and notification shade, which breaks §4 and §5 outright. If you want
> it as a stopgap, say so and I will add it behind an explicit flag with the tradeoff documented —
> but it should not be the default.

## 3. Device Owner — free, and testable today

**TestDPC** (Google's own sample, Apache-2.0) makes any factory-reset device a fully managed Device
Owner at zero cost. That validates our app's behaviour under Device Owner — `FLAG_SECURE`, managed
configurations, restriction compliance — before a rupee is spent.

```bash
# On a FACTORY-RESET device with no accounts added:
adb install TestDPC.apk
adb shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver
```

**Device Owner can only be established on a factory-reset device.** A phone already in use must be
wiped. This is a platform rule, not a configuration choice.

For the actual fleet, **Fleet** (MIT, self-hosted) has a free tier that explicitly covers fully
managed employee-issued Android, private APK deployment via managed Google Play, and managed
configurations — ₹0 in licences, cost is one small VM. Two caveats from the research: it needs a
work-domain email (not Gmail), and **kiosk/lock-task support is undocumented — verify in the trial**
if you need it. If kiosk turns out to be mandatory, ManageEngine MDM Plus at ~$1.08–1.20/device/month
is the cheapest verified fit and is an Indian vendor with Mumbai and Chennai data centres.

## 4. Integrity — Keystore attestation instead of Play Integrity

Play Integrity is free but requires the app to be Play-distributed, which means a $25 Play Console
account. So the primary integrity control for now is **Android Keystore hardware-backed key
attestation**: generate a key with an attestation challenge, and verify the certificate chain
server-side. It needs no Play account, no fee, and is hardware-rooted.

Play Integrity goes in later behind a feature flag, as defence-in-depth. Client-side root and emulator
heuristics stay exactly what the research says they are — bypassable, and never the control on their
own.

## 5. What ₹0 genuinely cannot prove

Honest list. These stay `BLOCKED` in `docs/TRACEABILITY.md` until funded, and no amount of code
changes that:

| # | Requirement | Why money is required | Cost to unblock |
| --- | --- | --- | --- |
| 1 | **§6 recording, §5 real masked calls** | Server-side recording and two-way masking need a licensed Indian carrier. Owning the PSTN path ourselves is prohibited — see `docs/research/india-telecom-legal.md`. | Acefone ₹1,599/user/mo, min 6 seats |
| 2 | **§14–§21, §25–§27 across a fleet** | One device via TestDPC proves the app. Enrolling and policing many devices needs an EMM. | ₹0 on Fleet self-hosted, or ~$1.08/device/mo |
| 3 | **§3, §4, §8 against real data** | Requires the Asktrix CRM to actually expose APIs. | ₹0 — CRM team effort, not a purchase |
| 4 | Managed Google Play distribution | Private-app publishing needs a Play Console account. Sideloading covers the pilot. | $25 one-time, avoidable |

**Item 3 is the real blocker, and it is free.** It needs the CRM team to implement
`api/openapi.yaml`, nothing more. I will hand them that file plus an integration guide.

## 6. Two free things I need from you

1. **A Firebase project** (free Spark plan, ~5 minutes) → send me `google-services.json`. Until then
   FCM (§24) is flag-gated off and the build does not require the file, so nothing is blocked.
2. **One spare Android phone you can factory-reset** → unblocks real Device Owner testing. An
   emulator covers everything else.

Neither is urgent. I am continuing on the mock path now.
