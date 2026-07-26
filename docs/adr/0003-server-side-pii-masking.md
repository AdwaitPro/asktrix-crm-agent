# 0003 — Customer PII masking is server-side, enforced by the schema

Status: **Accepted** · 2026-07-26 · Closes §4, supports §3

## Context

§4: *"Mask customer mobile numbers (98XXXXXX12) and email addresses (siv****@gmail.com). Employees
must never see the full contact details."* §1 adds: *"must never expose customer information, prevent
theft of customer data."*

The threat model is an insider with physical possession of a company device, all day, every day. That
is a far stronger adversary than a casual user.

## Options considered

- **Client-side masking** — the API returns the full number; the app displays a masked form. Defeated
  in minutes by anyone with the APK and an HTTP proxy, by a rooted device, by a memory dump, or by a
  crash report. It is a fake mask.
- **Server-side masking, schema-enforced** — the API never emits a full number in any response the
  device can reach.

## Decision

**The unmasked customer phone number and email never reach the device.**

1. `api/openapi.yaml` has **no field anywhere** carrying a full phone number or email. The DTOs expose
   `phoneMasked` and `emailMasked` only. There is no code path that could assemble a full number
   because the data is not present.
2. Calling is by `clientId`, never by number (see `docs/adr/0002-telephony-architecture.md`). The CRM
   resolves the number server-side and hands it to the CPaaS, not to the app.
3. `:core:security` provides defence-in-depth only: log redaction, and a test-enforced assertion that
   no DTO field ever contains a value matching a full phone/email pattern.
4. `FLAG_SECURE` on every window that can display client data (§14–§20).

## Consequences

- **§4 is met structurally, not cosmetically.** Proxying the app yields masked data because that is all
  the server sends.
- This is a **hard requirement on the CRM backend**. If the CRM cannot mask server-side, §4 cannot be
  delivered and that becomes a `BLOCKED` line in `docs/TRACEABILITY.md` — not something the app fakes.
- Any future feature needing a real number (SMS, WhatsApp, email) must execute server-side.
- Screenshot blocking cannot stop a second phone photographing the screen. That is precisely why the
  on-screen data is minimised and masked at source rather than relying on `FLAG_SECURE`.

## Sources

Requirements §1, §4. Threat model: insider with sustained physical device access.
