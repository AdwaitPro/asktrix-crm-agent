# 0002 — Telephony architecture: CPaaS bridge, not on-device recording

Status: **Accepted** · 2026-07-26 · Closes §5, §6, §7 and enables §4

## Context

The requirements document asks for automatic on-device call recording (§6), click-to-call with no
visible number and no dial pad (§5), masked customer contact details (§4), and call-log sync (§7).

As literally written, §6 is not implementable by a third-party APK on modern Android, and §4+§5 cannot
be satisfied by a device-initiated PSTN dial — the number would appear in the system call log and
notification shade regardless of what our UI shows.

Full research: `docs/research/telephony-cpaas.md`, `docs/research/india-telecom-legal.md`.

## Options considered

**(A) Cloud telephony / CPaaS bridge** — click-to-call is an API call to the CRM; the provider dials
the agent leg, then bridges to the customer; recording happens server-side; the customer's real number
never reaches the device.

**(B) In-app SIP/VoIP softphone** — the app owns the audio path, so it could record locally.
**Legally dead in India for PSTN.** The UL Internet Service authorisation prohibits *"Voice
communication to and from a telephone connected to PSTN/PLMN/GMPCS"* and prohibits E.164→IP
translation, and carrying inter-city domestic voice over our own network is squarely the OSP
toll-bypass definition. Separately, `android.net.sip` is dead (deprecated API 31, platform service
removed, `newInstance()` returns `null`), so we would need Linphone (AGPLv3) or PJSIP (GPLv2) — both
requiring an unpublished-price commercial licence for a closed-source app.

**(C) System/privileged app on OEM-provisioned hardware** — needs an OEM/ODM partnership or AOSP
build. Cost and lead time are out of proportion to this project.

**(D) Default-dialer role (`ROLE_DIALER`) + Device Owner** — does not grant call-audio access.
Device Owner status does not unlock reserved audio sources. Rejected.

## Decision

**Option (A). Acefone as the CPaaS provider.**

- Click-to-call: the app calls the Asktrix CRM, which calls
  `POST https://api.acefone.in/v1/click_to_call` with `agent_number`, `destination_number`, `async`,
  `caller_id`, and our `custom_identifier` as the correlation key.
- **The app never receives the customer's real number.** It sends a `clientId`; the CRM resolves the
  number server-side. This is what makes §4 real rather than cosmetic.
- Recording is server-side. The app never records, never holds audio, and needs no `RECORD_AUDIO`
  permission for calls.
- Call outcome, duration, and recording URL arrive at the CRM by webhook; the app reads them back from
  the CRM. **`READ_CALL_LOG` is therefore not required at all** — which removes a Google Play
  sensitive-permission review from the critical path.

Acefone over MyOperator because: a real purpose-built click-to-call endpoint (MyOperator has none —
only the OBD Anonymous Dialer, and `click_to_call` appears zero times in its official Postman
collection); documented recording retention (3/6/9/12 months vs unpublished); and ₹1,599/user/month vs
₹15,000/month just to unlock API access. Twilio is disqualified — its own docs mark India domestic
voice `N/A` in both directions, and an Indian entity cannot buy an Indian Twilio number.

## Consequences

- **§6 as written ("record on device, upload, delete locally") is PLATFORM-IMPOSSIBLE and is delivered
  server-side instead.** The business intent — every call recorded, encrypted, attached to the CRM
  timeline — is fully met. `docs/TRACEABILITY.md` records this honestly.
- The CRM backend becomes responsible for: the Acefone integration, **idempotent webhook handlers**
  (Acefone retries only twice — 30s then 10s timeout), and a **reconciliation sweep** against
  `GET /v1/call/records` to catch dropped webhooks. This is a firm requirement, not an optimisation.
- The app's `:core:telephony` shrinks to: request a call, observe call state, render history. No audio
  code, no recording pipeline, no telephony permissions.
- Recording is *"only for the initial leg and first transfer of a call"* — multi-leg transfers are not
  fully recorded. Must be disclosed to the client.
- Vendor lock-in is real but contained: the app talks only to the CRM, so swapping CPaaS is a backend
  change with no APK release.
- **Open items before contract:** Acefone `Authorization` header format (docs contradict themselves —
  `Bearer` vs raw token); per-minute India outbound rate (unpublished); setup fee; whether a masking
  API exists or masking must be built via API Dialplan; DoT licence category.

## Sources

`docs.acefone.in/reference/initiate-click-to-call`, `/reference/call-detail-records-2`,
`/docs/web-hooks`, `/docs/api-dialplan`, `/docs/data-retention-policy`;
`acefone.com/pricing/contact-center/`; `twilio.com/en-us/guidelines/in/voice` and
`/in/regulatory`; official MyOperator Postman collection
`documenter.getpostman.com/api/collections/38426694/2sAXqy3evq`;
TRAI `Recommendations_24_10_2017_0.pdf`; DoT Revised OSP Guidelines 23.06.2021.
