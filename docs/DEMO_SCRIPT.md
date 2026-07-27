# Demo script

A 12-minute walkthrough that shows the product working end to end. Written to be read while
presenting: the **bold line** is what you say, the indented line is what you do.

Everything here runs against a live Postgres database and a real Android build. Nothing is faked.

---

## Before you start (2 minutes, once)

```bash
cd ~/Desktop/astrix
./scripts/demo.sh
```

That starts the CRM, seeds a week of data, builds the app, and installs it on any attached device.
If no device is attached it tells you the command to start the emulator.

Have two windows open:

| Window | What | Login |
| --- | --- | --- |
| Browser | `http://localhost:4010/admin/` | `EMP003` / `asktrix123` |
| Phone or emulator | Asktrix Agent | `EMP001` / `asktrix123` |

If you have a second phone, mirror the emulator screen so the audience can see both at once.

---

## Act 1 — The problem this solves (1 min)

> **"The requirement was: employees must be able to do their job without ever seeing a customer's
> phone number. Most apps do this by hiding the number in the UI. That's not security — anyone with
> the app and a free proxy tool reads it in about two minutes."**

> **"So we did it differently. Let me show you what's actually in the database."**

   Run this in a terminal:

```bash
cd ~/Desktop/astrix/server && node -e "
require('dotenv').config();const{Pool}=require('pg');const p=new Pool({connectionString:process.env.DATABASE_URL});
p.query(\"select name, phone_full, email_full from clients where client_id='CLI-10240'\")
 .then(r=>{console.log(r.rows[0]); return p.end();});"
```

   It prints `9876543212` and `sivakumar@gmail.com`.

> **"That's the real data. Now watch what the phone is allowed to receive."**

---

## Act 2 — The agent's app (4 min)

### Sign in
   Open the app, sign in as `EMP001`.

> **"The device binds itself to this employee using a key generated inside the phone's secure
> hardware. That key can't be extracted, even on a rooted device."**

### Dashboard (§12)
> **"Six clients — only the ones assigned to this employee. That filtering happens on the server. If
> I modified the app to ask for someone else's clients, the server returns 403."**

   Point at the filter chips.

> **"Pending work, follow-ups due. The amber one is overdue."**

### Client detail — the important screen (§4, §5, §13)
   Tap **Sivakumar Ramanathan**.

> **"There's the same customer we just looked at in the database. `98XXXXXX12`. `siv****@gmail.com`."**

> **"The full number was never sent to this device. It doesn't exist in the app's memory, its cache,
> or its network traffic. There's nothing to leak."**

   Try to long-press the number — nothing happens.

> **"No copy, no selection, no share. And notice there's no dial pad anywhere in this app."**

### Place a call (§5, §6, §7)
   Tap **Call through CRM**.

> **"The app sent one thing: a client ID. The CRM looks up the real number and asks the telephony
> provider to bridge the two calls. The recording happens on the provider's servers."**

   Watch it move through *Connecting… → Calling your phone… → Ringing client… → Connected*.

> **"This app declares no calling permissions at all. No CALL_PHONE, no READ_CALL_LOG, no
> RECORD_AUDIO. That's not an oversight — it's the architecture. It also keeps us out of Google's
> sensitive-permissions review entirely."**

   When it ends, point at the timeline.

> **"Call logged, duration recorded, attached to the CRM timeline automatically."**

### Quick status (§13)
   Tap **Payment received**.

> **"Six one-tap actions, straight from the requirements."**

### Offline — the one to linger on (§9, §23)
   Turn on **aeroplane mode**.

> **"Field agents work in basements and villages. Watch."**

   Tap **Documents received**. Banner appears: *"Offline · 1 change will sync automatically"*.

> **"Saved instantly. The employee carries on working."**

   Turn aeroplane mode **off**. Wait about ten seconds, then switch to the admin console and refresh.

> **"There it is in the database. Every action carries a unique key, so if the network retries three
> times the customer still only gets marked once — they don't get charged twice or called twice."**

### Attendance (§10, §11)
   Open the **Attendance** tab, check in.

> **"GPS check-in. Notice the notification that appears — location tracking is now on, and the
> employee can see that at all times. It stops the moment they check out."**

> **"Tracking is limited to working hours, and that limit is enforced on the server, not the phone.
> An employee can change their phone's clock; they can't change ours."**

### Try to screenshot
   Attempt a screenshot on the release build.

> **"Blocked. That's the requirement working."**

---

## Act 3 — The manager's console (4 min)

   Switch to the browser.

### Overview
> **"Live view of the team. Who's on duty, calls today, talk time, follow-ups due, recordings held."**

### Location (§10)
   Open **Location**, pick an employee.

> **"Their route for the day, sampled every ten minutes. Below it, attendance with the exact position
> of each check-in — and both the time the phone reported and the time our server received it. If
> those ever disagree, we know."**

### Calls (§6, §7)
   Open **Calls**, press **Play** on a recording.

> **"Every call, its outcome, its duration, and the recording — playable here, in the console."**

> **"Note the asymmetry. The manager can play it. The phone is only ever told a recording exists. It
> can't fetch one."**

### Clients — the part that sells the design (§4)
   Open **Clients**.

> **"Same masking as the phones, by default, even for a manager."**

   Press **Reveal** on any client. Type a reason. Confirm.

> **"A manager handling an escalation legitimately needs the real number. So they can get it — once,
> deliberately, with a reason."**

   Open **Audit log**.

> **"And it's recorded. Who looked, which customer, why, when. When a regulator asks 'who accessed
> this person's data' — that's the answer."**

### Devices
> **"Every enrolled handset, its Android version, its integrity status, whether push is registered."**

---

## Act 4 — What's honest about the limits (2 min)

Do not skip this. It is the part that makes everything else credible.

> **"Three things I want to be straight about."**

> **"One. The requirement said record calls on the phone. That's impossible on modern Android — Google
> removed the API, and in India building our own voice path is actually unlawful. So recording happens
> at the telephony provider instead. Every call still gets recorded and attached to the CRM. And it
> made §4 and §5 genuinely enforceable rather than cosmetic, because the number never reaches the
> phone at all."**

> **"Two. Blocking the Play Store, browsers, uninstalling, factory reset — those need device
> management. The policy is written and ready" —** show `mdm/policy.json` **— "but it needs an
> enrolment subscription and a factory-reset handset. There's a free option; the runbook covers it."**

> **"Three. Real masked calls need a telephony account, about ₹1,600 per user per month. Everything
> you just saw runs on a simulated provider that follows the identical state machine, so switching to
> the real one is a configuration change, not a rewrite."**

> **"Everything else is done and running."**

---

## If something goes wrong

| Symptom | Fix |
| --- | --- |
| App shows "No internet connection" | The CRM stopped. `cd server && npm start`. The emulator reaches your machine at `10.0.2.2`, not `localhost`. |
| Admin console won't sign in | Use `EMP003`. `EMP001` and `EMP002` are agents and are correctly refused. |
| Dashboard empty | `cd server && npm run seed && npm run seed:demo` |
| Screenshot works on the phone | You're on a debug build with `asktrix.allowScreenshots=true`. That's expected; release builds always block it. |
| Call never connects | The simulator fails about 30% of calls on purpose, to show the failure paths. Try again. |

## Numbers worth knowing if asked

- **12 minutes** to enrol a handset, most of it waiting on downloads
- **11 MB** release APK
- **10 minutes** GPS sampling interval, working hours only
- **60 minutes** maximum life of cached client data on the device
- **Zero** telephony permissions in the APK
- **30 requirements** tracked individually in `docs/TRACEABILITY.md`, including the ones that are not done and why
