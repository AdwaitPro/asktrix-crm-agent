# Compliance: DPDP Act 2023, call recording, employee monitoring

Engineering compliance brief. It states what the law requires, what has been built to meet it, and
what still needs a lawyer. Items marked **`NEEDS LEGAL SIGN-OFF`** are not engineering judgements.

Primary sources are cited in `docs/research/india-telecom-legal.md`. Last updated **2026-07-27**.

---

## 1. Roles under the DPDP Act 2023

| Role | Who | Notes |
| --- | --- | --- |
| Data Fiduciary | Asktrix (the company) | Decides purpose and means |
| Data Processor | The CRM host, the telephony provider, Neon, the EMM vendor | Each needs a written data-processing agreement |
| Data Principal | **Customers *and* employees** | Employee location and attendance are personal data too — this is the part most often missed |

**`NEEDS LEGAL SIGN-OFF`:** whether Asktrix qualifies as a Significant Data Fiduciary, which would
add a DPO in India, a DPIA, and independent audits.

## 2. What the Act requires, and what was built

| Obligation | Section | What exists in the code |
| --- | --- | --- |
| Notice before consent | s.5(1) | Sign-in screen discloses masking, CRM-routed calls, and working-hours-only location. Attendance repeats the location disclosure at the point of use. |
| Notice in an Eighth Schedule language | s.5(3) | **Not built.** English only. Needs Hindi at minimum, and realistically the languages the workforce actually uses. |
| Consent: free, specific, informed, affirmative | s.6(1) | Employee acceptance is currently implicit in signing in. **Not sufficient** — see gaps below. |
| Right to withdraw consent | s.6(4) | **Not built.** Needs a CRM-side workflow. |
| Purpose limitation | s.4, s.6(1) | The API returns only what a screen needs. The client list carries no contact block at all; only the detail view does. |
| Data minimisation | s.6(1) | Masked values only. KYC document *metadata* is sent; document contents never are. |
| Storage limitation | s.8 | Every cached row carries a TTL and expired rows are filtered in the DAO query. Server retention is the matrix in §5. |
| Erasure | s.12 | Sign-out deletes the database and destroys the Keystore key. **Server-side erasure workflow is not built.** |
| Security safeguards | s.8(5) | SQLCipher, Keystore, no cleartext, `FLAG_SECURE`, rotating refresh tokens, server-side authorisation. |
| Breach notification | s.8(6) | Process, not code. **`NEEDS LEGAL SIGN-OFF`** — note CERT-In's separate 6-hour direction may also apply. |

**`NEEDS LEGAL SIGN-OFF`:** the DPDP Rules 2025 prescribe the actual **notice format** that s.5 and
s.6 defer to. Their current commencement status could not be verified — MeitY's page returns 403 and
the Rules PDF 404s. **The consent wording should not be finalised until someone reads the notified
text.**

## 3. Call recording

**There is no Indian statute or licence condition requiring a beep or a pre-call announcement.** That
was checked against the OSP Guidelines (2020 and 2021), TCCCPR 2018, the TCCCPR 2nd Amendment 2025,
and the DPDP Act. None contains one.

What actually binds is DPDP notice-and-consent. So the defensible design is:

1. A pre-call disclosure to the customer, played by the telephony provider before the legs bridge —
   the industry norm, and the practical way to satisfy "informed".
2. An auditable consent record: who, when, which purpose, which notice version, and the recording id
   it maps to.
3. A withdrawal path.

**Employee side:** the employment contract or acceptable-use policy must state that calls are
recorded, and acceptance should be versioned and timestamped server-side. The sign-in disclosure is a
reminder, not the legal artifact.

**Telephony provider:** becomes a Data Processor. Needs a written DPA and a contractual data-residency
commitment — the marketing page claim of India hosting is not a contract.

## 4. Employee monitoring

- **Legality:** GPS tracking of employees on company-owned devices is generally lawful in India with
  notice and a legitimate purpose. Post-*Puttaswamy*, proportionality matters.
- **Working hours only is not a nicety.** Tracking outside working hours is disproportionate and hard
  to defend. Enforced **server-side** (`withinWorkingHours`), because the device clock and timezone
  are user-settable. Verified: an out-of-hours batch returned `rejectedOutsideWorkingHours: 1`.
- **Visibility:** the persistent notification while tracking is active is a compliance feature, not
  an Android tax.
- **Attendance photos:** stored as plain images and **never** face-matched. Adding face recognition
  would make them biometric data and trigger substantially heavier obligations. **Do not add it**
  without accepting that.

## 5. Data retention and deletion matrix

Proposed. **`NEEDS LEGAL SIGN-OFF`**, particularly the recording row.

| Data class | PII? | Stored where | Legal basis | Retention | Deletion | Audit log |
| --- | --- | --- | --- | --- | --- | --- |
| Call recordings | Yes | Telephony provider / CRM | Consent + legitimate business | **Match the provider tier (3/6/9/12 mo).** Must not be shorter than what the notice promises | Provider auto-delete + CRM job | Required |
| Call metadata / CDR | Yes | CRM | Legal obligation | **1 year minimum** — OSP security conditions | Scheduled job after 1 year | Required, tamper-evident |
| GPS pings | Yes (employee) | CRM | Employment purpose | **90 days** — enough for payroll disputes, no more | Scheduled job | Required |
| Attendance records | Yes (employee) | CRM | Employment / statutory | **3 years** (payroll records) | Scheduled job | Required |
| Attendance photos | Yes (employee) | CRM | Employment purpose | **90 days** — shorter than the record itself; the photo proves presence at the time, not forever | Scheduled job | Required |
| Cached client data | Yes | **Device** | Consent | **≤60 min TTL**, purged on logout | Automatic + purge | No |
| KYC documents | Yes (sensitive) | CRM only, **never the device** | Legal obligation (KYC rules) | Per sectoral rules | Manual, with approval | Required |
| Internal remarks | Possibly | CRM | Legitimate business | Life of the case + 1 year | Case closure job | Required |
| Session tokens | No | Device (encrypted) | Necessary | 30 days, rotating | Sign-out / expiry | No |
| Device compliance reports | No | CRM | Security | 90 days | Scheduled job | No |
| App / audit logs | Ids only | CRM | Security, legal | 1 year | Scheduled job | Self |

**Design note:** the app holds only two rows of this table, both ephemeral. That is deliberate — a
lost or stolen handset should be a hardware loss, not a data breach.

## 6. Engineering checklist

Built:
- [x] Server-side masking, enforced by schema and by a build-failing test
- [x] Device never receives an unmasked number, so it cannot leak one
- [x] Purpose-scoped API responses; KYC contents never sent to the device
- [x] Encrypted cache with TTL, purged on sign-out and on integrity failure
- [x] Working-hours gating enforced server-side
- [x] Tracking visible to the employee whenever it is active
- [x] Device identity on every call record (OSP)
- [x] IST as the reporting timezone (OSP)
- [x] No PII in logs; no logging interceptor; R8 strips logging in release
- [x] Plain-language disclosure at sign-in

Not built — required before production:
- [ ] Versioned consent/notice records with timestamped employee acceptance
- [ ] Notice in Hindi and other workforce languages (s.5(3))
- [ ] Consent-withdrawal workflow (s.6(4))
- [ ] Customer erasure workflow reaching CRM, recordings and backups (s.12)
- [ ] Audit log of every employee access to customer PII
- [ ] Grievance-officer contact surfaced in the app
- [ ] Retention jobs implementing the matrix above
- [ ] Data-processing agreements with CRM host, telephony provider, Neon and EMM vendor
- [ ] Pre-call disclosure configured at the telephony provider

## 7. Requirements that are legally risky as written

| Requirement | Risk | Resolution |
| --- | --- | --- |
| §4 masking | Client-side masking would be a straightforward data leak | Moved server-side, enforced by schema (ADR-0003) |
| §6 "record… delete locally" | Impossible on Android; an in-app SIP path is separately unlawful in India | Recording moved server-side (ADR-0002) |
| §10 GPS tracking | Unbounded tracking is disproportionate | Working-hours only, enforced server-side, visible to the employee |
| §25–§27 admin sees recordings | Broad access to recordings is a purpose-limitation problem | Access should be role-limited and audit-logged in the CRM. **`NEEDS LEGAL SIGN-OFF`** |
| Indefinite retention | Contradicts storage limitation | Retention matrix in §5 |

## 8. One item that may already be non-compliant

TRAI Directions of 19 Nov 2025 and 16 Dec 2025 require RBI, SEBI and IRDAI-regulated senders to make
service and transactional voice calls **only** from 1600-series numbers, *"even with the explicit or
inferred consent of customers"*. The phased deadlines ran **1 January – 15 March 2026 and have
passed.**

**If Asktrix serves BFSI clients, calls from a non-1600 number are already treated as unregistered
telemarketing**, with suspension or disconnection of telecom resources as the stated consequence.

This must be checked per client sector **before the first real call is placed**. It is the single
most urgent compliance item in this document.
