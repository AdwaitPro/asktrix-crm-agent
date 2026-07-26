# Research: Cloud telephony / CPaaS for click-to-call, masking and recording (§4, §5, §6, §7)

Research date **2026-07-26**. All prices as published on that date. Sources are listed at the end.

> **Methodology warning.** `support.myoperator.com` and `developers.myoperator.co` are JavaScript SPAs
> that return empty HTML bodies. Plain HTTP fetches against them produced *fabricated* API content
> (invented base URLs and endpoints), which was caught and discarded. Every MyOperator fact below
> comes from a browser-rendered page or the official Postman collection JSON. Treat any MyOperator
> endpoint not listed here as unverified.

## Decision summary

**Twilio is disqualified for India-domestic voice. Acefone is the recommended provider.**
See `docs/adr/0002-telephony-architecture.md`.

---

## 1. Twilio — disqualified for India domestic

From Twilio's own India voice guidelines page:

| Reachability | Inbound | Outbound |
| --- | --- | --- |
| **Domestic (within India)** | **N/A** | **N/A** |
| International | N/A | Yes |
| Toll-Free | Yes | No |

Verbatim: *"Outbound calls to India can only be made from international (non-Indian) numbers."*

From the India regulatory page: the **only** Indian number type Twilio offers is toll-free `+91800`,
and for both businesses and individuals the address *"Must be outside of the country"*. Toll-free
outbound is `No`.

**Consequences:**
1. A two-leg agent↔customer bridge where both parties are Indian mobiles is **not supported**.
2. Calls to Indian mobiles are only possible as *international* traffic with a foreign caller ID —
   poor answer rates and squarely inside TRAI UCC enforcement.
3. An Indian entity **cannot buy an Indian Twilio number**.
4. **Number masking is therefore structurally impossible on Twilio in India** — masking requires an
   in-country number pool Twilio does not sell to Indian entities.
5. Twilio Proxy (the masking product) has been **Public Beta since 2017** and carries **no SLA**.
   No EOL notice found on any official page; the widely-repeated "closed to new customers" claim is
   **UNVERIFIED**.

India pricing for reference: local `$0.0699/min`, mobile `$0.0496/min` (outbound, international
only — the "receive calls" cells are blank, corroborating that no Indian inbound DID exists).
Recording `$0.0025/min` + storage `$0.0005/min/month`. WebRTC/SIP legs `$0.0040/min`.

**Residual use:** international outbound from a foreign entity, WebRTC/SIP legs, global fallback.

---

## 2. Acefone (Servetel is fully absorbed — `servetel.in` 301s to `acefone.com`)

Docs: `https://docs.acefone.in` (Readme.io). Append `.md` to any page for raw markdown;
`https://docs.acefone.in/llms.txt` is a complete page index.

### 2.1 Click-to-call — a real, purpose-built endpoint

```
POST https://api.acefone.in/v1/click_to_call
Header: Authorization
```

| Param | Type | Required | Documented meaning |
| --- | --- | --- | --- |
| `agent_number` | String | **Yes** | ID of the Acefone agent who will receive the call |
| `destination_number` | String | **Yes** | Number of the client to be called |
| `async` | String | **Yes** | `1` = asynchronous (default), `0` = synchronous |
| `caller_id` | String | No | Caller ID shown to the called party |
| `call_timeout` | Integer | No | Auto-disconnect after N seconds |
| `custom_identifier` | String | No | **Echoed back in webhooks — this is our correlation key** |

Response: `Success` (Boolean), `Message` (String). Status 200 / 400. Asynchronous by default:
returns "Call originated successfully" immediately; lifecycle arrives by webhook.

Second variant `POST /v1/click_to_call_support` (designed for CRM-initiated calls without portal
login): required `customer_number`, `api_key`, `async`; optional `customer_ring_timeout` (10–30s,
default 30), `caller_id`, `custom_identifier`, `call_timeout`.

DID rotation, verbatim: the system *"will randomly select a DID from your available DIDs to make the
call… to mitigate the risk of the same DID being used repeatedly"* — available *"on a request basis
and only through APIs."*

**Auth:** `POST /v1/auth/login` `{email, password}` → `success`, `access_token`, `token_type`,
`expires_in`. Refresh at `POST /v1/auth/refresh`. Tokens are 1-hour by default; unlimited tokens
require contacting support.

> **UNVERIFIED — resolve before coding the auth interceptor.** The reference page lists
> `Authorization: Bearer <token>`, but the only working curl sample sends the **raw token with no
> `Bearer` prefix**. Confirm empirically against a real account.

Call control: `POST /v1/call/options` — `type` (1 Monitor, 2 Whisper, 3 Barge, 4 Transfer),
`call_id`, `agent_id`, `intercom`. Also hangup, disconnect, fetch-active-calls.

### 2.2 Recording retrieval

`GET https://api.acefone.in/v1/call/records` — the real CDR API. (`/reference/call-detail-records`
is a UI guide, **not** the API; the API is `/reference/call-detail-records-2`.)

Query params: `from_date`, `to_date` (`Y-m-d H:i:s`), `page`, `limit`, `agents`, `department`, `ivr`,
`call_type` (`c` answered / `m` missed), `callerid`, `destination`, `direction`, `duration`,
`operator` (`>`, `<`, `>=`, `<=`, `!=`), `services`, `broadcast`, `did_number`, `call_id`.

Response: `count`, `limit`, `size`, `page`, `results[]` each carrying **`recording_url`**, `id`,
`call_id`, `uuid`, `direction`, `status`, `date`, `time`, `end_stamp`, `call_duration`,
`answered_seconds`, `agent_name`, `agent_number`, `client_number`, `did_number`, `reason`,
`hangup_cause`, `call_flow[]`.

**Retention is plan-tiered and documented:** 3 / 6 / 9 / 12 months. After expiry, *"permanently
deleted… cannot be retrieved or restored"*. Beyond 12 months is not supported. SFTP/S3/Azure export
carries one-time charges based on transfer size.

**Two material gotchas:**
- Recordings older than 3 months require CDR Archive → "Fetch Old Recording".
- Recording is captured *"only for the initial leg and first transfer of a call"* — later transfers
  are **not** recorded. Relevant to any multi-leg masked flow.
- Enabling recording is **not** a documented API toggle; it appears to be plan/panel level.
  `UNVERIFIED — confirm how recording is switched on per DID/campaign.`

### 2.3 Webhooks

~16–18 documented triggers, including the two that matter here: **"Call answered by Customer (Click
to Call)"** and **"Call missed by Customer (Click to Call)"**, plus "Call answered by Agent",
"Call hangup (Missed)", "Call hangup (Answered)", "Call hangup (Missed or Answered)", "DTMF Option".

Payload variables (`$`-prefixed): `$uuid`, `$call_id`, `$call_to_number`, `$caller_id_number`,
`$start_stamp`, `$answer_stamp`, `$end_stamp`, `$duration` (seconds), `$billsec`, `$hangup_cause`,
`$call_status` (`Answered` / `Missed`), `$recording_url`, `$customer_no_with_prefix`, `$ref_id`,
`$direction`, `$custom_identifier`.

Transport: POST or GET; form-encoded or JSON; custom headers; timestamps Default / Epoch-Unix /
ISO 8601; timezone IST or UTC. Hangup causes follow Q.850.

> **Delivery is weak: *"up to two attempts if there is no response from your endpoint"*** (first
> waits up to 30s, second 10s timeout). **The CRM backend therefore needs idempotent webhook
> handlers plus a reconciliation sweep against `/v1/call/records`.** This is a firm requirement on
> the CRM side, not an optimisation.

### 2.4 Number masking

Named product "Phone Number Masking". Verbatim: *"Two-Way Number Masking — Agent and customer
numbers are masked in both directions. Neither party can identify the other, before or after the
call."* Also "Dynamic Caller IDs — rotate a pool of virtual numbers" and "DPDPA-Aligned Data
Handling — calls route through India-hosted infrastructure" (marketing page, not contractual).

**There is no masking REST resource** — `llms.txt` contains zero entries matching "mask". The
buildable mechanism is the **API Dialplan**: Acefone calls *our* HTTP endpoint mid-call with
`$uuid`, `$call_to_number`, `$caller_id_number`, `$start_stamp`, `$last_dtmf`, `$call_flow`,
`$call_id`, and we return JSON actions, e.g.
`{"transfer":{"type":"number","data":["98XXXXXXXX"],"ring_type":"order_by","skip_active":true}}`.
Documented use case, verbatim: *"Allow two parties to communicate with each other with enhanced
security without disclosing their numbers."* Up to 3 nested API Dialplans.

Masking carries **no separate fee** — included from ₹1,599/user/month.

### 2.5 Published pricing (INR, `acefone.com/pricing/contact-center/`)

| Item | Price |
| --- | --- |
| **Professional** | **₹1,599 / user / month**, min **6 seats**; recording retained **3 months** |
| **Ultra** | **₹1,999 / user / month**, min **5 seats**; recording **6 months**; 24×7 support |
| Enterprise | Contact for pricing |
| Additional DID | ₹100 / month (1 standard DID included) |
| VMN / toll-free number | ₹500 / month |
| Additional channels | ₹600 / channel (min 2) |
| Extended recording (12+ mo) | ₹100 / seat / month |
| Toll-free **inbound** minutes | ₹1.25 – ₹1.60 / min (tiered) |

**Per-minute India outbound rate is NOT published — requires a sales quote.** Plans say only
"Unlimited Calling within India". **Setup fee not published.**

---

## 3. MyOperator — viable but weaker

**There is no click-to-call endpoint.** Verified by string search of the entire official Postman
collection: `click_to_call` = 0 occurrences, `click-to-call` = 0, `mask` = 0. "Click to Call" and
"Call Masking" are panel/marketing features, not published REST resources.

The only outbound API is the OBD (Outbound Dialer):

```
POST https://obd-api.myoperator.co/obd-api-v1
Header: x-api-key: <calling-x-api-key>
```

Two-number bridging is available via **Anonymous Dialer** mode — verbatim *"Bridges two external
numbers"*. Required fields: `company_id`, `secret_token`, `type` (`"1"`), `number` (customer),
`number_2` (**agent**), `public_ivr_id`. Optional: `caller_id`, `std_code`, `reference_id`,
`call_hold`, `max_call_duration` (1–5400s), `custom_*`. `type=3` is deprecated.

`reference_id` must be unique within a configured window — *"duplicate reference_id results in
request rejection"*, which makes it usable as an idempotency key.

Success: `{"details":"Request accepted successfully","status":"success","code":"200","unique_id":…}`.

> **UNVERIFIED auth discrepancy:** the KB says OBD requires **`x-api-key` + `secret-key` headers**,
> but the Postman request sends only `x-api-key` with `secret_token` in the body.

**Recording:** included from the **Sedan (₹5,000/mo)** plan and above — **not** in Compact
(₹2,500/mo). Enabled in the panel, not by API. Two-step retrieval: webhooks deliver
`recording_filename`, then
`GET https://developers.myoperator.co/search/recordings/link?token=…&file=…`. Verbatim:
**"Recording links are valid for 24 hours only."** Panel limits: no bulk download, 20 latest logs
per page, `.mp3` only, connected calls only.

> **Retention: NOT PUBLISHED — `UNVERIFIED`.** The browser-rendered recording KB article contains no
> retention period at all. A commonly-repeated "6 months" figure came from a hallucinated fetch and
> is **not** in any official article.

**Webhooks v2** events: `call.initiated`, `call.ivr_started`, `call.dial_begin`, `call.answered`,
`call.end`, `call.summary`, `disposition`. Envelope: `company_id`, `event_id`, `event_type`,
**`event_sequence`** (useful for ordering — MyOperator has this, Acefone does not),
`event_version`, `timestamp`, `channel`, `direction`, `session_id`, `customer_identifier`, `payload`.
Payload: `id`, `did`, `category`, `customer_number`, `status` (`bridged`/`missed`/`voicemail`),
`started_at`, `ended_at`, `duration`, `billable`, `ref_id`, `recording_filename`, `legs[]`.
Per-leg: `leg_index`, `type` (`agent`/`customer`), `phone_number`, `result`, `ring_duration`,
`talk_duration`, `dial_status` (`ANSWER`/`NO ANSWER`/`BUSY`/`NO CARRIER`/`CANCEL`).

**Pricing (INR, billed yearly, GST extra):** Compact ₹2,500/mo (3 users, **no recording**);
Sedan ₹5,000/mo (10 users, recording); **SUV ₹15,000/mo (API access & webhooks)**; Enterprise
₹2,00,000/mo. Extra users ₹2,000 each. DID: **₹1,000 one-time + ₹200/month**.
**API access requires SUV — ₹15,000/mo.** Per-minute rates not published.

**KYC is fully documented and is a hard gate:** PAN mandatory for every applicant, plus one ID and
one address proof. Aadhaar eKYC (minutes) or manual upload (up to 6h), both ending in CAF e-signature.
Pvt Ltd / LLP need PAN + Certificate of Incorporation + utility bill or stamped rent agreement.
Numbers are **not portable** — *"cloud-based landline numbers bound to the platform."*

---

## 4. Side-by-side

| Capability | Acefone | MyOperator | Twilio (India) |
| --- | --- | --- | --- |
| Purpose-built click-to-call endpoint | **Yes** (2 variants) | **No** (OBD only, needs `public_ivr_id`) | N/A |
| Two-number bridge | `agent_number` + `destination_number` | OBD Anonymous Dialer (`number` + `number_2`) | **Not possible domestically** |
| Recording retrieval | `recording_url` direct in CDR + webhook | filename → link API, **24h validity** | Persistent REST |
| Recording retention | **3/6/9/12 mo, documented** | **Not published** | Own storage |
| Webhook retry | **Only 2 attempts** | Not documented (has `event_sequence`) | Documented retries |
| Masking | Two-way product, no API (use API Dialplan) | Product exists, no API published | Proxy: beta, no SLA, unusable in India |
| India DID for an Indian entity | Yes, ₹100–₹500/mo | Yes, ₹1,000 + ₹200/mo | **No** |
| API tier gate | ₹1,599/user/mo | **₹15,000/mo (SUV)** | Pay-as-you-go |
| Docs quality | **Good** (Readme.io, `llms.txt`) | **Poor** (Postman only) | Excellent |

**Acefone wins** on API quality, a real click-to-call endpoint, documented retention, and ~10× lower
entry cost for API access. Its weaknesses — 2-attempt webhook retry, auth-header ambiguity,
first-leg-only recording, no true masking API — are all manageable on the CRM backend.

---

## 5. Open items to get in writing before contract

1. **Acefone:** `Authorization` header format (`Bearer` or raw); whether a masking API exists;
   per-minute India outbound rate; setup fee; DoT licence category and number.
2. **MyOperator:** recording retention window; whether a `secret-key` header is required alongside
   the body `secret_token`; whether Call Masking has an API; confirm API access truly needs SUV.
3. **Both — the biggest India-specific unknown:** who bears **OSP registration** for remote/WFH
   agents. Neither vendor documents this. Neither publishes a DoT licence number.
4. **Both:** a written Data Processing Agreement (the provider is a Data Processor under DPDP) and a
   contractual data-residency commitment, not a marketing claim.

## 6. Sources

**Twilio:** `twilio.com/docs/voice/api/call-resource`, `/docs/voice/twiml/dial`,
`/docs/voice/twiml/record`, `/docs/voice/api/recording`, `/docs/voice/twiml`,
`/en-us/guidelines/in/voice`, `/en-us/guidelines/in/regulatory`, `/en-us/voice/pricing/in`,
`/docs/proxy`, `/docs/proxy/api`, `/docs/glossary/what-are-masked-phone-numbers`,
support article 360011435554 (recording consent).

**Acefone:** `docs.acefone.in/llms.txt`, `/reference/initiate-click-to-call`,
`/reference/initiate-click-to-call-support`, `/reference/call-detail-records-2`,
`/reference/generate-a-token`, `/reference/authentication-using-tokens`,
`/reference/call-operations-1`, `/docs/web-hooks`, `/docs/api-dialplan`,
`/docs/data-retention-policy`, `/docs/call-detail-records-cdr-1`, `/docs/my-numbers`,
`acefone.com/number-masking`, `/pricing/contact-center/`, `/blog/140-number-series/` (DLT, blog).

**MyOperator:** `support.myoperator.com/portal/en/kb/articles/myoperator-api-reference-postman-documentation-links`,
`documenter.getpostman.com/api/collections/38426694/2sAXqy3evq` (official collection JSON, 418KB —
the primary source), `…/what-are-the-charges-for-buying-a-did-in-myoperator`,
`…/what-is-a-virtual-number-1-9-2025`, `…/how-to-check-call-recording-on-the-panel-1-9-2025`,
`myoperator.com/pricing`, `/call-masking`, `/click-to-call`.

**Dead / misleading:** `myoperator.com/number-planning` and `/plans` → 404.
`developers.myoperator.co` → HTTP 200 with empty body; all doc paths 404.
`servetel.in` → 301 to `acefone.com`; `docs.servetel.in` and `api.servetel.in` → 403.
