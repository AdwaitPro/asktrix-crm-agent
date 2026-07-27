# Open questions and external dependencies

Everything here blocks on someone outside this repo. Each item says who can answer it and what
happens until they do. Nothing here is a reason the build is not progressing — the app runs end to
end against the development CRM regardless.

Last updated **2026-07-27**.

## Blocking — needed before production

### 1. Does the Asktrix CRM expose APIs, and who owns implementing them?
**Answer so far: no APIs exist.** `api/openapi.yaml` is written as a **proposal**, and `server/`
implements it against Neon Postgres so the app is built against something real.

**Needed:** confirmation that the CRM team will implement this contract, a base URL, and a target
date. If they intend a different shape, I need their spec now — not after the app is finished.

**Until then:** the app works fully against `server/`. Switching to the real CRM is one URL change.

### 2. Telephony provider — commercial decision
Recommended **Acefone** (₹1,599/user/month, minimum 6 seats). See
`docs/research/telephony-cpaas.md` for why, and why Twilio is disqualified for India.

**Needed before contract:**
- The `Authorization` header format — Acefone's own docs contradict themselves (`Bearer <token>` on
  the reference page, raw token in the working sample).
- Per-minute India outbound rate — not published anywhere.
- Setup fee — not published.
- Whether "Phone Number Masking" has an API, or must be built via their API Dialplan.
- Their DoT licence category and number.

**Until then:** a simulated provider walks the identical state machine, so nothing downstream is
blocked.

### 3. OSP registration for remote/field agents — **the biggest India-specific unknown**
DoT's 2021 revised guidelines removed OSP *registration*, but the CDR obligations survive: one-year
tamper-proof call records including **the identity of the device used**, IST-synchronised timestamps,
and on-demand access for DoT/law enforcement.

The app already stores `device_id` on every call record for exactly this. What is unresolved is
**who bears the obligation** — Asktrix, or the telephony provider. Neither vendor documents it.

**Needs:** legal counsel, plus a written answer from the chosen provider.

### 4. Do any Asktrix clients fall under the 1600-series mandate?
TRAI Directions of 19 Nov 2025 and 16 Dec 2025 require RBI, SEBI and IRDAI-regulated senders to make
service and transactional voice calls **only** from 1600-series numbers, *"even with the explicit or
inferred consent of customers"*. Phased deadlines ran **1 Jan – 15 Mar 2026 and have already passed.**

**If Asktrix serves BFSI customers, non-compliant dialling is already treated as unregistered
telemarketing.** This must be checked per client sector before any real call is placed.

### 5. EMM vendor choice
Recommended: pilot on self-hosted **Fleet** (free, MIT). If kiosk/lock-task turns out to be
mandatory, **ManageEngine MDM Plus Standard** (~$1.08–1.20/device/month, Mumbai + Chennai data
centres). See `docs/research/device-management-emm.md`.

**Needed:** a decision, plus one factory-reset handset to validate enrolment against.

### 6. Who owns the §25–§27 admin dashboard?
Device monitoring belongs in the EMM console. Employee and productivity monitoring belongs in the
existing CRM web app. **Neither is in the mobile app's scope**, but the requirements read as though
one product should do both.

**Needed:** confirmation that the CRM web team owns the employee-facing dashboard.

## Non-blocking, but decide before launch

### 7. Working hours per employee
Currently seeded as 09:30–18:30 IST, Mon–Sat, and enforced server-side. Real values must come from
HR, and probably vary by role.

### 8. Cache TTL
Currently 60 minutes for client detail, 15 for the list. Shorter is more private and more
network-hungry; longer is friendlier on patchy coverage. `cacheTtlSeconds` is server-controlled, so
this is tunable without an app release.

### 9. Data retention periods
`docs/COMPLIANCE.md` proposes a matrix. It needs sign-off, particularly for call recordings — where
the telephony plan's own retention tier (3/6/9/12 months) must not be shorter than what is promised
to customers in the DPDP notice.

### 10. DPDP Rules 2025 — current commencement status
Could not be verified: MeitY's data-protection page returns 403 and the Rules PDF path 404s. The
Rules prescribe the **notice format** that DPDP §5/§6 defer to, so the exact consent wording used in
the app should not be finalised until someone reads the notified text.

**Until then:** the sign-in screen carries a plain-language disclosure of masking, CRM-routed calls
and working-hours location. That is defensible but is not a substitute for the prescribed format.

## Answered

| Question | Answer | Date |
| --- | --- | --- |
| Should the app be the Device Policy Controller? | **No.** Google restricts AMAPI to commercial EMM vendors, and Play Protect has blocked non-approved custom DPCs since 2026. | 2026-07-26 |
| Can §6 on-device recording be done? | **No** on modern Android, and an in-app SIP alternative is separately unlawful in India. Recording moves server-side. | 2026-07-26 |
| Twilio for India? | **No.** Twilio's own docs mark India domestic voice `N/A` both directions, and an Indian entity cannot buy an Indian Twilio number. | 2026-07-26 |
| Firebase project | Created by the client; `com.asktrix.agent.dev` wired and building. A second registration is needed for the production package. | 2026-07-27 |
| Database for the dev CRM | Neon Postgres 18.4, Singapore region. Connected and seeded. | 2026-07-27 |
