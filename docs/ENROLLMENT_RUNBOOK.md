# Device enrollment runbook

For the IT administrator enrolling an Asktrix agent handset. Read the two facts below before you
touch a device — both of them cost people entire afternoons when discovered late.

> **1. The device must be factory reset.** Device Owner can only be established during initial setup.
> A phone that is already signed in and in use **cannot** be converted. If it is in service, back up
> what matters and wipe it.
>
> **2. The Asktrix app is not the management agent.** It is a normal managed app that the EMM installs
> and configures. The management agent is the EMM's own DPC. See
> `docs/adr/0004-device-management.md`.

Time per device once you are set up: **8–12 minutes**, most of it waiting on downloads.

---

## Part A — one-time setup (do this once, not per device)

1. **Choose and provision an EMM.** See `docs/research/device-management-emm.md` for the comparison.
   - Free path: self-hosted **Fleet** (MIT licence, free tier covers fully managed Android, private
     APKs and managed configurations). Needs a work-domain email — a Gmail address is rejected.
   - If kiosk/lock-task turns out to be mandatory: **ManageEngine MDM Plus Standard**
     (~$1.08–1.20/device/month at 50–100 devices, Indian vendor, Mumbai + Chennai data centres).

2. **Create the enterprise** in the EMM console and bind it to your Google account.

3. **Upload the policy.** Import `mdm/policy.json`. Read the `_comment_notEnforceable` block at the
   end — four requested restrictions have no supported policy key, and the file says exactly what is
   done instead.

4. **Publish the app** as a **managed Google Play private app** targeted at your organisation.
   Prefer Google-hosted over self-hosted: self-hosted APKs are an "advanced" Android Enterprise
   feature restricted to the production track and are not scanned by Google.

5. **Generate the enrollment QR** with the values your EMM console gives you:

   ```bash
   python3 scripts/generate_provisioning_qr.py \
     --dpc-package <from your EMM> \
     --dpc-signature-checksum <from your EMM> \
     --dpc-download-url <from your EMM> \
     --enrollment-token <from your EMM> \
     --wifi-ssid "Asktrix-Office" --wifi-password "<office wifi password>" \
     --out build/provisioning-qr.png
   ```

   Print it. Laminate it. A scuffed QR on a phone camera in a badly-lit store room is a real failure
   mode.

6. **Optional but worth it — zero-touch.** If you are buying handsets, buy them through an Indian
   enterprise reseller enrolled in Android zero-touch. Devices then enrol themselves on first boot
   and Part B disappears entirely. It is free; it only has to be arranged at purchase time.

---

## Part B — per device

| # | Step | What you should see |
| --- | --- | --- |
| 1 | Factory reset the device. Settings → System → Reset → Erase all data. | Device reboots to the welcome screen |
| 2 | On the **very first** setup screen, tap the same spot **6 times**. | A QR scanner opens |
| 3 | Scan `provisioning-qr.png`. | "Setting up your device…" |
| 4 | Wait. The device joins Wi-Fi and downloads the DPC. | 3–8 minutes depending on the network |
| 5 | The DPC applies `policy.json` and installs the Asktrix app. | Asktrix appears on the home screen |
| 6 | Open Asktrix and sign in with the employee's code. | Dashboard with their assigned clients |
| 7 | Grant location when prompted, then grant **"Allow all the time"** when asked a second time. | Attendance screen no longer shows the permission prompt |
| 8 | Check in once, confirm the persistent "Location tracking active" notification appears, then check out. | Confirms §10 works on this handset |

### Step 7 needs explaining to the agent

Android deliberately asks for background location **separately**, and will not show both requests in
one dialog. The second prompt is the one that matters — without "Allow all the time", location stops
the moment the screen turns off, and the gap will not be obvious until someone audits the data.

---

## Part C — per-OEM battery settings (do not skip this)

This is the single largest cause of "the app stopped tracking" in the Indian market. Several OEM
skins freeze background work regardless of what Android's own policy says, and **no API and no EMM
policy reliably overrides them**. They have to be set by hand, per device.

| Manufacturer | Where to go | What to set |
| --- | --- | --- |
| **Xiaomi / Redmi / POCO** (MIUI, HyperOS) | Settings → Apps → Asktrix → **Battery saver** → **No restrictions**; Settings → Apps → Asktrix → **Autostart** → on | Both, or background work stops when the screen locks |
| **Samsung** (One UI) | Settings → Battery → **Background usage limits** → **Never sleeping apps** → add Asktrix | Samsung puts unused apps to sleep after ~3 days otherwise |
| **Oppo / Realme / OnePlus** (ColorOS) | Settings → Battery → App battery management → Asktrix → allow **auto-launch**, **background activity**; disable **battery optimisation** | ColorOS kills background services on screen-off by default |
| **Vivo / iQOO** (Funtouch, OriginOS) | Settings → Battery → **High background power consumption** → allow Asktrix; Settings → More settings → Applications → **Autostart** → on | |
| **Tecno / Infinix / itel** (HiOS, XOS) | Settings → **Battery Lab** → disable power-saving for Asktrix; Phone Master → **Auto-start management** → allow | Note the App Booster hard cap of 4 apps |
| **All of the above** | Open the recents/task switcher, long-press the Asktrix card, tap the **padlock** | The only mitigation common to every vendor, and on OnePlus it is what stops the battery setting reverting overnight |

**Verify rather than trust.** After setting these, check in on the device, lock it, leave it 30
minutes, then confirm in the CRM that location pings arrived. Some OEMs revert these settings on
their own; if a device keeps going quiet, that is what is happening.

---

## Part D — verification checklist

Run this on the first device of each new handset model before rolling out to that model.

- [ ] Asktrix is installed and cannot be uninstalled (long-press → no uninstall option)
- [ ] Play Store shows only approved apps
- [ ] Screenshot attempt fails — **this is a pass, not a bug** (§14–§20)
- [ ] Settings → Developer options is unavailable
- [ ] Factory reset is unavailable from Settings (§21)
- [ ] USB file transfer is refused when plugged into a computer
- [ ] Sign-in succeeds and the assigned-client list loads
- [ ] Client detail shows a masked number in `98XXXXXX12` form — **never a full number** (§4)
- [ ] There is no dial pad anywhere in the app (§5)
- [ ] Check-in starts the persistent location notification; check-out stops it (§10, §11)
- [ ] With aeroplane mode on, a status change still saves and shows "Waiting to sync"; it syncs when
      connectivity returns (§9, §23)
- [ ] After 30 minutes locked and checked in, location pings are visible in the CRM

---

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| "Harmful app blocked" during QR scan | The DPC is not Android-Enterprise approved. Google has blocked non-approved custom DPCs since 2026. | Use a validated EMM's DPC. A custom DPC will not work. |
| QR scanner does not open on 6 taps | Not the first setup screen, or the device is already set up | Factory reset and retry from the welcome screen |
| Provisioning stalls at "Setting up your device" | Wi-Fi credentials in the payload are wrong, or the network has a captive portal | Regenerate the QR with correct credentials; captive-portal networks cannot be used for provisioning |
| App installs but sign-in fails | The CRM base URL is unreachable from the device network | Confirm the device can reach the CRM host; check the firewall |
| Location stops after the screen locks | Background location is set to "while using the app" | Settings → Apps → Asktrix → Permissions → Location → **Allow all the time** |
| Location works, then silently stops after a day | OEM battery manager | Redo Part C, and lock the app in recents |
| Screenshots work | This is a **defect**. Release builds hardcode `FLAG_SECURE`. | Confirm it is a release build, not a debug build with `asktrix.allowScreenshots=true` |
