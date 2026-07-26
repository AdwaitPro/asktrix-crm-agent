# 0004 — The Asktrix app is NOT the Device Policy Controller

Status: **Accepted** · 2026-07-26 · Reshapes §14–§21, §25–§27

## Context

§30 correctly notes that FRP, uninstall-blocking, Settings-blocking, and install-blocking need Android
Enterprise Device Owner mode with an MDM. The open question was *how* we obtain Device Owner: build our
own DPC, use Google's Android Management API directly, or buy a third-party EMM.

Full research: `docs/research/device-management-emm.md`.

## Options considered

**(1) Custom DPC** — our app implements `DeviceAdminReceiver` and calls `DevicePolicyManager` owner
APIs. **Blocked by Google since 2026:** *"Only DPCs verified and approved by Android Enterprise are
permitted to install apps during enterprise device enrollment provisioning."* Non-allowlisted DPCs show
**"Harmful app blocked"** during QR/NFC/`afw#` enrollment. Headwind MDM confirms this on its own site.

**(2) Android Management API directly** — Google's Permissible Usage terms restrict AMAPI *"solely to
commercial Enterprise Mobility Management (EMM) developers, Device Trust… solution providers, and
OEMs"* and explicitly prohibit *"Solutions developed and used exclusively for first party in-house
applications."* Using it for our own fleet is a terms violation. Quota is also capped at 500 devices.

**(3) Third-party EMM** — an Android-Enterprise-validated vendor provides Device Owner via Google's own
Android Device Policy agent. Neither problem above applies.

## Decision

**Option (3). The Asktrix agent app is a normal managed application, not a DPC.**

- It is deployed as a **managed Google Play private app** through the EMM.
- It receives configuration via **managed configurations**, and **reports compliance**; it does not
  enforce device restrictions itself.
- `:core:mdm` is therefore a **managed-configuration consumer and compliance reporter**. It contains no
  `DeviceAdminReceiver` and no `DevicePolicyManager` owner calls.
- Device restrictions (§14–§21, §25–§27) are delivered as **EMM policy configuration plus an
  enrollment runbook**, which are the deliverables for those requirements — not Kotlin code.

**Recommended vendor:** pilot on self-hosted **Fleet** (MIT, free tier, AMAPI-backed, private APKs,
managed configs). If kiosk/lock-task proves mandatory — the one capability I could not confirm on Fleet
— buy **ManageEngine MDM Plus Standard** (~$1.08–1.20/device/month at 50–100 devices, Indian vendor,
Mumbai + Chennai data centres, on-prem option). Second choice **Hexnode Pro** ($2.20), but get the
Mumbai region confirmed in writing.

## Consequences

- **Weeks of doomed custom-DPC work avoided**, along with the ongoing maintenance surface.
- App-side security work is now scoped to what an app can actually do: `FLAG_SECURE`, encrypted
  storage, cert pinning, Play Integrity with server-side verdict verification, no-copy UI affordances.
- **Device Owner can only be established on a factory-reset device.** Phones already in service must be
  wiped. Every runbook states this first.
- Arrange **zero-touch enrollment at purchase** through an Indian enterprise reseller — free, and it
  removes manual provisioning.
- An EMM subscription (or a Fleet VM) is a **hard external dependency**. §14–§21 and §25–§27 cannot be
  demonstrated without it, and that is recorded as `BLOCKED` in `docs/TRACEABILITY.md` until procured.
- Prefer Google-hosted private apps over self-hosted APKs; self-hosted is an "advanced" feature with
  production-track-only restrictions and no Google scanning.
- §25–§27 admin dashboard: device monitoring belongs to the EMM console. Employee/productivity
  monitoring belongs to the existing CRM web app. **Neither is in the mobile app's scope** — pending
  client confirmation, logged in `docs/OPEN_QUESTIONS.md`.

## Sources

`developers.google.com/android/management/permissible-usage`;
`support.google.com/work/android/answer/16694822` (DPC allowlist);
`h-mdm.com/open-source/` (vendor confirming Play Protect blocking);
`fleetdm.com/lp/android-mdm`; `manageengine.com/mobile-device-management/pricing.html`;
`support.google.com/work/android/answer/6145182` (externally hosted apps).
