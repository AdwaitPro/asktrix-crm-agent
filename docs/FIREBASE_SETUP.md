# Firebase setup for push notifications (§24) — free, ~5 minutes

Firebase Cloud Messaging on the **Spark (free) plan** is all this app needs. FCM is free with no
message cap, so there is nothing to pay and no card to add.

## What you do

1. Go to **https://console.firebase.google.com** and sign in with any Google account.

2. Click **Create a project** (or **Add project**).
   - **Project name:** `Asktrix Agent` (any name works)
   - **Google Analytics:** turn it **OFF**. We do not want analytics on an app that handles customer
     PII — it is an extra data-sharing path we would have to declare under DPDP for no benefit.
   - Click **Create project**, wait ~30 seconds, then **Continue**.

3. On the project overview page, click the **Android** icon (the little robot) under
   *"Get started by adding Firebase to your app"*.

4. Fill in the registration form:

   | Field | Value — must be exact |
   | --- | --- |
   | **Android package name** | `com.asktrix.agent.dev` |
   | App nickname | `Asktrix Agent (dev)` |
   | Debug signing certificate SHA-1 | **leave blank** — not needed for FCM |

   > The `.dev` suffix matters. Debug builds use the application ID `com.asktrix.agent.dev` so a dev
   > build can sit alongside a production build on the same handset. If the package name does not
   > match exactly, FCM will not deliver.

5. Click **Register app**.

6. Click **Download google-services.json**.

7. **Send me that file** — or just drop it into the repo yourself at:

   ```
   /Users/adwaitkeshari/Desktop/astrix/app/google-services.json
   ```

8. Skip the rest of the wizard ("Add Firebase SDK", "Next", "Continue to console"). I wire the SDK
   in the build; you do not need to touch Gradle.

## Later, for production

When the production build ships, repeat steps 3–7 with the package name **`com.asktrix.agent`**
(no `.dev`). One Firebase project can hold both apps — click **Add app** on the project overview.

## What I do with it

- Register the device's FCM token against the employee via `PUT /device/push-token`.
- Deliver call outcomes, new client assignments, and follow-up reminders as push messages, so the
  app does not have to poll and drain battery.
- **The push payload carries only identifiers** — a `clientId` or a `callSessionId`, never a name,
  never contact details. Notification content is fetched over the authenticated API after the push
  arrives. A push travels through Google's infrastructure and lands on a lock screen, so it is not a
  place to put customer data (§4).

## Cost and privacy

- **Cost: ₹0.** FCM is unlimited on the free Spark plan.
- No credit card, no billing account.
- `google-services.json` is **not a secret** in the credential sense — it contains a project ID and a
  public API key, and it ships inside every Android APK by design. It is still gitignored here, since
  it identifies your project and there is no reason to publish it.

## Until you send it

**Nothing is blocked.** FCM is behind a flag: the build does not require `google-services.json`, and
the app works fully without push. The only difference is that call outcomes and new assignments
arrive on the next sync rather than instantly.
