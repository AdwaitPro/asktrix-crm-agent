# Asktrix CRM Mobile Agent App — Repository Guide

Secure Android employee-operations app for **company-owned, Device-Owner-managed** devices, integrated
with the Asktrix CRM. Source requirements: `docs/requirements.pdf` (§1–§30). Build contract:
`docs/MASTER_PROMPT.md`.

## Local toolchain

This machine's JDK and Android SDK are keg-only Homebrew installs. Source the env before any Gradle command:

```bash
source scripts/env.sh
./gradlew assembleDebug
```

`scripts/env.sh` exports `JAVA_HOME` (OpenJDK 21) and `ANDROID_HOME`
(`/opt/homebrew/share/android-commandlinetools`). `local.properties` carries `sdk.dir` and is gitignored.

## Architecture invariants — do not violate these

1. **The unmasked customer phone number and email never reach the device.** Masking happens server-side
   in the CRM API response. The DTOs have no field for a full number. A client-side mask is a fake mask.
   See `docs/adr/0003-server-side-pii-masking.md`.
2. **No permanent customer database on device.** The local store is an ephemeral, encrypted, TTL'd cache,
   purged on logout, integrity failure, and remote wipe (§3).
3. **Every write action goes through the outbox** with an idempotency key. Nothing lost offline, nothing
   duplicated on retry (§9, §23).
4. **Nothing sensitive is ever written to logcat.** Use the `AsktrixLog` wrapper; raw `android.util.Log`
   calls fail the build via a Detekt rule and are stripped by R8 in release.
5. **Calls are never dialled from the device with a real customer number.** Click-to-call is a CRM API
   call; the telephony provider bridges the legs. See `docs/adr/0002-telephony-architecture.md`.

## Module graph

Dependencies point inward. `:feature:*` may depend on `:core:*`; `:core:*` never depends on `:feature:*`.

```
:app                  Application, nav host, DI root
:core:common          Result types, dispatchers, error model, clock
:core:designsystem    Compose M3 theme, components, masked-field primitives
:core:network         Retrofit, OkHttp, cert pinning, auth interceptor, token refresh
:core:database        Encrypted Room, DAOs, migrations, outbox tables
:core:datastore       Encrypted preferences, session store
:core:security        Keystore, crypto, integrity, PII masking, FLAG_SECURE
:core:sync            Outbox engine, WorkManager orchestration, conflict policy
:core:telephony       Click-to-call, call session state
:core:location        Location sampling, working-hours gating
:core:mdm             Device-owner policy application, restriction enforcement
:feature:auth         Login, device binding, biometric unlock
:feature:dashboard    Assigned clients, pending work, follow-ups (§12)
:feature:client       Client detail, masked contact, timeline, status actions (§4, §8, §13)
:feature:calls        Call flow, history (§5, §6, §7)
:feature:attendance   Check-in/out with GPS + optional photo (§11)
:feature:settings     Minimal, locked down
```

## Working rules

- **Requirement traceability is mandatory.** Every change updates `docs/TRACEABILITY.md`
  (§ → module → files → tests → status).
- **No stubs, no `TODO`, no placeholder logic.** If something is blocked, it goes in
  `docs/OPEN_QUESTIONS.md` and gets a test marked `@Ignore("BLOCKED: <reason>")`.
- **Zero hallucinated APIs.** Any Android API, policy constant, or dependency coordinate must be
  verifiable against primary docs. Record the API level introduced and any behaviour-change history.
- **Non-obvious decisions become ADRs** in `docs/adr/NNNN-title.md`
  (Context / Options / Decision / Consequences / Sources).
- **Contract-first.** `api/openapi.yaml` is the single CRM contract. Feature code targets it and the
  mock server, never a hand-written guess at an endpoint.
- **Secrets never enter the repo.** `local.properties`, Gradle properties, or CI secrets only.

## Verification gate

A change is not done until this passes:

```bash
source scripts/env.sh
./gradlew assembleDebug lintDebug testDebugUnitTest detekt
```

## Key documents

| File | Purpose |
| --- | --- |
| `docs/requirements.pdf` | Stakeholder requirements, §1–§30 |
| `docs/FEASIBILITY.md` | Phase 0 audit: what is possible, what is not, with citations |
| `docs/COMPLIANCE.md` | DPDP Act 2023, call-recording consent, retention matrix |
| `docs/TRACEABILITY.md` | §1–§30 → implementation status |
| `docs/OPEN_QUESTIONS.md` | Blocked items and external dependencies |
| `docs/adr/` | Architecture decision records |
| `api/openapi.yaml` | CRM API contract (proposal until the CRM team confirms) |
