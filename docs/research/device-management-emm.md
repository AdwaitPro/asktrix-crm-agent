# Research: Device Owner / MDM path for a self-managed fleet (§14–§21, §25–§27, §30)

Research date **2026-07-26**. India, company-owned fleet, ~10–200 devices.

## Three findings that reshape the architecture

### 1. We cannot use the Android Management API directly for our own fleet

Google's AMAPI Permissible Usage page states verbatim:

> *"The use of Google's Android Management API and associated SDK ("Service") are restricted solely
> to commercial Enterprise Mobility Management (EMM) developers, Device Trust from Android Enterprise
> (Device Trust) solution providers, and Original Equipment Manufacturers of Android devices (OEMs)."*

and explicitly prohibits:

> *"Solutions developed and used exclusively for first party in-house applications."*

The API is free and the quickstart works with a plain Gmail account, but internal-only use is a
**terms violation**. Initial quota is also capped at 500 devices and raising it requires "a full
business justification".

Source: `developers.google.com/android/management/permissible-usage`

### 2. Google now blocks non-approved custom DPCs at provisioning time

> *"Only DPCs verified and approved by Android Enterprise are permitted to install apps during
> enterprise device enrollment provisioning."*

Non-allowlisted DPCs show **"Harmful app blocked"** during QR / NFC / `afw#` enrollment. Headwind MDM
states this on its own site: *"Since 2026, provisioning of Android devices by custom builds of
Headwind MDM launcher is limited by Google for security reasons. Such attempts are currently blocked
by Play Protect."*

**This kills the roll-your-own-DPC route.** AMAPI-based products are unaffected, because they use
Google's own Android Device Policy agent.

Sources: `support.google.com/work/android/answer/16694822`, `h-mdm.com/open-source/`

### 3. Therefore: the Asktrix app must NOT be the DPC

**Architectural consequence — this is the important one.** The Asktrix agent app is a *normal managed
application*, deployed through an EMM as a **managed Google Play private app**, receiving policy via
**managed configurations**. It does not implement `DeviceAdminReceiver`, does not call
`DevicePolicyManager` owner APIs, and does not enforce device restrictions itself.

`:core:mdm` therefore becomes a **managed-configuration consumer and compliance reporter**, not a
policy enforcer. Device restrictions (§14–§21, §25–§27) are delivered by the EMM's policy set, and
our deliverable for them is the **policy configuration + enrollment runbook**, not Kotlin code.

See `docs/adr/0004-device-management.md`.

---

## Vendor options (prices per device / month unless marked)

No vendor except Microsoft and Google publishes INR; all others are USD list, with INR + GST
invoicing quote-only.

| Vendor | Tier needed | Price | Private in-house app | Kiosk (COSU) | India data residency |
| --- | --- | --- | --- | --- | --- |
| **Fleet** (MIT, self-host) | Free tier | **$0** + VM cost | Yes — private APKs via managed Google Play | **Not documented — UNVERIFIED** | Self-host anywhere incl. Mumbai. Needs a work-domain email (not Gmail) |
| **ManageEngine MDM Plus** (Zoho) | Standard | 50 dev **$645/yr** (~$1.08); 100 dev **$1,195/yr** | Yes — APK upload **and** publish to managed Google Play from console | Single + multi-app | **Best: Zoho DCs Mumbai (primary) + Chennai**; on-prem option |
| **Hexnode** | Pro | **$2.20**, min 15 devices | **Strongest** — true managed Play private apps, no Play dev account needed | Single + multi-app, silent install | Security page says **US + EU only**; a *blog* claims Mumbai — get it in writing |
| **Scalefusion** (Pune) | Growth | **$3.50**, annual only, min 10 | Own Enterprise Store only; **no documented managed-Play private-app publishing** | Single + multi-app | **DCs IE/NL/DE/US — no India option** |
| **Esper** | Bridge | **$4**, **25-device contract minimum** | Yes (APK ≤500MB, V1 signature required) | All tiers | **Not published — UNVERIFIED** |
| **Microsoft Intune** | Plan 1 | **$8 /user/mo** (device-only SKU exists but **price unpublished**) | **Best documented** — managed Play private app or direct LOB `.apk` | Dedicated single + multi-app | **Yes, India geo** — but geo is fixed by the Entra tenant's country at creation and cannot be changed later. **Create the tenant with country = India from day one.** |
| **Miradore** (GoTo) | Premium | $2.75 annual / $3.30 monthly | Yes | **Premium only** | **Not stated — UNVERIFIED** |
| Google Workspace endpoint mgmt | Business Plus+ | India ₹99–₹1,080 /user | Yes, APK from Admin console | **No kiosk mode at all** | No India-specific commitment |

**Also worth quoting, India-relevant, published pricing:** CubiLock $2.50/$3.50/$5.50 (Standard covers
fully-managed + dedicated); 42Gears SureMDM (Bengaluru) $3.99/$5.49/$7.99, kiosk in all tiers;
AirDroid Business $1.00/$1.75/$2.75 annual, min 10 devices, but kiosk is a paid add-on below
Enterprise; TinyMDM ~$2.40/device/mo (pricing page unfetchable — **UNVERIFIED**).

**Avoid:** DIY AMAPI console (terms violation + quota gate), Headwind custom DPC (Play-Protect
blocked per their own site), **Flyve MDM (dead — GitHub archived, agent removed from Play)**,
Miradore Free (50-device cap, excludes apps/kiosk/configs), Esper below 25 devices (contract minimum),
Entgra (quote-only, custom DPC → allowlist problem).

### Google Workspace — the definitive answer

Workspace endpoint management **can** do fully managed / Device Owner. Verbatim:
*"New (or factory reset) devices in your organization's company-owned inventory are automatically set
up in fully managed mode during setup, and work profile is not available."*

Advanced mobile management requires: Frontline Starter/Standard/Plus; **Business Plus**; Enterprise
Standard/Plus; Education Standard/Plus; Enterprise Essentials/Plus; G Suite Basic/Business; Cloud
Identity Premium. **Not** Business Starter, Business Standard, or Cloud Identity Free.

Three disqualifying caveats for our use case:
- **No kiosk / dedicated (COSU) mode** documented anywhere in Workspace endpoint management.
- **No QR / NFC / `afw#` enrollment** documented — only serial-in-inventory + factory reset + managed
  account sign-in. Zero-touch *is* supported and free.
- Official guidance: **no more than ~5 active managed Android devices per user, 25 total.**

## Provisioning and distribution notes

- **Device Owner can only be established on a factory-reset device.** Every runbook must state this
  up front.
- **Zero-touch enrollment** is free but requires devices bought from an enterprise reseller / Google
  partner, plus an EMM that supports company-owned devices. Worth arranging at purchase time.
- **Play Console developer account: US$25 one-time**, if we publish the private app ourselves.
  Avoidable — Hexnode and Intune both publish private apps into managed Google Play from their own
  console with no developer account.
- **Prefer Google-hosted private apps over self-hosted APKs.** Self-hosted (externally hosted) APKs
  are an "advanced" Android Enterprise feature with restrictions: production track only, no closed
  releases, not publishable via the managed Play iframe, not scanned by Google, and the Play account
  must be an organisation admin account.
- Google's closed-testing gate (12 opted-in testers for 14 days) applies to *"developers with
  personal accounts created after November 13, 2023"*. Organisation accounts appear exempt but the
  official page does not say so — **UNVERIFIED. Register as an organisation to be safe.**
- **TestDPC** (Apache-2.0, `adb shell dpm set-device-owner`, `afw#testdpc`) is the right tool to
  validate our app's behaviour under Device Owner on one handset. Useless for a fleet.
- **Samsung Knox Suite** Base is free with Knox-supported Galaxy purchases; Essentials/Enterprise are
  quote-only. Only relevant if the fleet is all-Samsung.

## Recommendation

1. **Pilot on self-hosted Fleet (free tier)** — ₹0 licences, MIT, AMAPI-backed real Device Owner,
   private APKs, managed configs, no terms or allowlist exposure, no price cliff at 200 devices.
   **Verify kiosk/lock-task in the trial** — that is the one capability I could not confirm.
2. **If kiosk is mandatory, buy ManageEngine MDM Plus Standard** (~$1.08–1.20/device/mo at 50–100
   devices; Indian vendor; Mumbai + Chennai DCs; on-prem option; private-app publishing from
   console). Second choice **Hexnode Pro at $2.20** — best private-app and managed-config story, but
   get the Mumbai region confirmed in writing.
3. **Arrange zero-touch at purchase** through an Indian enterprise reseller — free, and removes
   manual provisioning entirely.

## Sources

AMAPI / Android Enterprise: `developers.google.com/android/management/permissible-usage`,
`/introduction`, `/quickstart`, `/create-enterprise`, `/policies/dedicated-devices`,
`developers.google.com/android/work/requirements`, `/play/custom-app-api`,
`support.google.com/work/android/answer/16694822` (DPC allowlist).

Play private apps: `support.google.com/googleplay/work/answer/6145139`,
`support.google.com/work/android/answer/6145182` (externally hosted),
`support.google.com/googleplay/android-developer/answer/6112435` ($25 fee),
`…/answer/14151465` (12 testers / 14 days).

Workspace: `support.google.com/work/android/answer/9412115`,
`knowledge.workspace.google.com/admin/devices/*` (edition list, company-owned inventory, device
requirements, zero-touch, managed configurations, private apps),
`workspace.google.com/intl/en_in/pricing.html`.

Vendors: `fleetdm.com/pricing`, `/lp/android-mdm`, `/guides/android-mdm-setup`,
`manageengine.com/mobile-device-management/pricing.html` + edition matrix + `know-your-datacenter.html`,
`hexnode.com/pricing/uem/` + help pages + `security-and-compliance/`,
`scalefusion.com/pricing/` + `/plan-comparison/` + `/security/data-storage-security/`,
`esper.io/pricing` + help.esper.io articles, `miradore.com/plans-pricing/`,
`microsoft.com/en-us/security/business/microsoft-intune-pricing`,
`learn.microsoft.com/en-us/intune/…/licenses`, `…/android-dedicated-devices-fully-managed-enroll`,
`…/privacy/data-handling/data-storage-processing` (India geo),
`h-mdm.com/pricing/` + `/open-source/`, `github.com/flyve-mdm` (archived),
`github.com/googlesamples/android-testdpc`, `cubilock.com/pricing/`,
`42gears.com/pricing/mobile-device-management/`, `airdroid.com/pricing/airdroid-business/`,
`samsungknox.com/en/blog/new-knox-suite-plans`.
