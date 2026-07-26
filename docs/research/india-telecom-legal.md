# Research: India telecom regulation, SIP viability, and recording consent

Research date **2026-07-26**. Sources are primary instruments (TRAI/DoT/MeitY PDFs, AOSP signature
files) fetched directly. Where `developer.android.com` reference pages rendered as navigation-only,
the AOSP SDK stub API files (`prebuilts/sdk/<N>/public/api/android.txt`) were used — the same source
of truth those pages are generated from.

## The one-line conclusion

**You cannot legally own the SIP↔PSTN path in India, and Android has no usable built-in SIP stack.
The PSTN leg must be bought from a licensed Indian carrier/CPaaS.** This independently confirms the
Acefone decision in `docs/adr/0002-telephony-architecture.md`.

---

## 1. `android.net.sip` is dead

| Milestone | API level | Evidence |
| --- | --- | --- |
| Added | **9** (Android 2.3 Gingerbread) | `prebuilts/sdk/8/.../android.txt` has **0** occurrences; `prebuilts/sdk/9/.../android.txt` declares `package android.net.sip` at line 9093 |
| Deprecated | **31** (Android 12) | The official API-diff page deprecates the entire class and all 21 methods + 3 fields at once |
| Removed | **Never** | `prebuilts/sdk/36/.../android.txt` line 42014 still declares the package, every member `@Deprecated`. It still compiles. |

AOSP javadoc, verbatim: *"`android.net.sip.SipManager` and associated classes are no longer supported
and should not be used as the basis of future VOIP apps."*

**But it is dead at runtime.** The API is gated on a system feature:

```java
public static SipManager newInstance(Context context) {
    return (isApiSupported(context) ? new SipManager(context) : null);  // returns null
}
// isApiSupported() == packageManager.hasSystemFeature(PackageManager.FEATURE_SIP)
```

The AOSP Phone app (`packages/services/Telephony`, branch `main`) no longer declares any SIP service,
provider, or `SipAccountRegistry` — the only trace left is a vestigial `USE_SIP` permission. With no
system component publishing `FEATURE_SIP`, `newInstance()` returns `null`.

**Treat as removed. Do not design against it.**

Note: the Android 12 "Behavior changes: all apps" page and the Android 12 "Deprecations" page do
**not** mention SIP at all — the only first-party deprecation records are the API-diff page and the
javadoc. There is no behaviour-changes citation to be had.

> `UNVERIFIED` — some OEMs historically shipped their own SIP implementation. Confirm per
> OEM/Android-version in the fleet with a 5-line test:
> `packageManager.hasSystemFeature(PackageManager.FEATURE_SIP)`.

**Google's documented replacement is call *integration*, not a stack.** From the official SIP guide:
*"look at one of the many modern open source alternatives as the basis for any VOIP calling
implementation. Alternatively, you can implement the `ConnectionService` API…"* The current entry
point is `androidx.core:core-telecom` (`CallsManager`), requiring `MANAGE_OWN_CALLS`. It provides
`addCall()`, `answer()`, `disconnect()`, `setActive()`, `requestEndpointChange()` and audio routing —
**zero SIP signalling, zero RTP media**. Explicit warning in the docs: do not use
`AudioManager#setCommunicationDevice` or `startBluetoothSco` alongside Telecom.

## 2. Both open SIP stacks are strong copyleft

| | Linphone SDK | PJSIP / pjsua2 |
| --- | --- | --- |
| Licence | **AGPLv3** (client-side ≈ GPLv3), dual-licensed | **GPLv2-or-later**, dual-licensed |
| Commercial price | **Not published** — "contact Belledonne" | **Not published** — `licensing@teluu.com` |
| Official Android AAR | **Yes** — `org.linphone:linphone-sdk-android` on `download.linphone.org/maven_repository/`, release **5.4.124**, active 5.6.0-alphas | **No** — Maven Central `numFound: 0` for `g:org.pjsip` |
| Integration cost | Low (ready AAR) | **High** — you own NDK + SWIG + OpenSSL cross-build + the Android 15 16KB page-size patch (needs NDK r27+ and PR #4068) |
| Opus | **ON** by default | Yes |

**Linphone licence trap**, verbatim from its README: *"NO GPL third parties means… your final
application is **still subject to the GPL except if you have a commercial license** from Belledonne
Communications. **This is the default mode.**"*

**G.729 hazard on both stacks:** each uses **bcg729**, which is **GPLv3**. If an Indian SIP trunk
prefers G.729 for bandwidth, that path inherits GPLv3 unless covered by a commercial licence.

For a closed-source CRM, both stacks put an unpublished-price commercial negotiation on the critical
path. The carrier-integrated Indian SDKs (Exotel, Ozonetel) avoid this entirely.

**Managed alternatives with official Android SDKs:** Twilio `com.twilio:voice-android` (Maven Central,
6.10.3, updated 2026-04-06, API 25+) — but see the India disqualification in
`telephony-cpaas.md`. **Exotel** `github.com/exotel/exotel-voip-sdk-android` — AAR side-loaded, no
Maven; *"exotelvoice library always routes the call via sip exophone configured for your account"*,
i.e. **carrier-licensed party owns the PSTN leg**; repo artifact is `1.0.13`, so confirm current
version and API 35/36 compatibility. **Ozonetel** CXi Switch `.aar` (~350KB, min API 26) — documents
App-to-App and App-to-CloudAgent only; **app-to-PSTN is not stated — UNVERIFIED, do not assume.**

---

## 3. India regulation — the binding constraint

### 3.1 DLT registration applies to VOICE, not just SMS — settled

TCCCPR 2018 definitions clause, verbatim:

> **"Commercial Communication" means any voice call or message using telecommunication services**,
> where the primary purpose is to inform about or advertise or solicit business…

Every DLT obligation is keyed to "Commercial Communication", so it lands on voice as well as SMS.
Reg. 13 mandates DLT adoption; the Schedule defines CLI structure **separately for voice** (140-level
series). The **TCCCPR 2nd Amendment 2025** makes it explicit: *"…or **voice calls are made through
the DLT platform**."*

### 3.2 TCCCPR 2nd Amendment 2025 — obligations on commercial voice

Instrument: *Telecom Commercial Communications Customer Preference (Second Amendment) Regulations,
2025 (1 of 2025)*, No. RG-25/(25)/2023-QoS, dated **12 February 2025**. Commencement 30 days after
Gazette publication; regulations 8, 17, 20(a), 20(b), 21(b) at 60 days.

**Registration is mandatory, enforced by disconnection.** New Reg. 3(2), verbatim:

> **"No Sender, who is not registered with any Access Provider** for the purpose of sending commercial
> communications under these regulations, **shall make any commercial communication**, and in case,
> any such Sender sends commercial communication, **all the telecom resources of such Sender may be
> put under suspension or may also be disconnected**…"

**Auto-dialler declaration is now a hard obligation.** New Reg. 4, verbatim:

> **"Intimation regarding use of Auto Dialer or Robo-Calls.—** Every Sender shall notify the
> Originating Access Provider, in advance, about the use of Auto Dialer or Robo-Calls as well as the
> intended objective of such calls **in writing**."

**Numbering series:** promotional auto-dialled voice → **140-series only**; service and transactional
auto-dialled voice → **1600-series**.

**Consent model for voice:**
- **Promotional** — only to subscribers who have not blocked the category, or who consented via the
  Access Providers' DCA (Digital Consent Acquisition) platforms.
- Explicit consent is time-boxed: *"shall not extend beyond duration / discharge of the contract"*;
  for certain service calls, *"**seven days** or as directed by the Authority."*
- **Service** — informational (warranty, recall, delivery, balance), no explicit consent needed.
- **Transactional** — customer-initiated, **within thirty minutes** of the transaction.
- **The anti-mixing rule that catches CRMs**, verbatim: *"if promotional content is mixed with any
  type of commercial Voice Call, such voice call shall be treated as a **Promotional Voice Call**."*
  An agent upselling during a "service" call reclassifies the entire call, inheriting 140-series and
  preference-scrubbing obligations.
- **Content scrubbing is NOT required for voice** (¶51: *"not practical in case of voice calls and
  should not be mandated"*). Preference/consent scrubbing still applies.
- Traceability: max **two** telemarketers in the chain; annual self-certification, with **automatic
  suspension** on failure.
- `UCC_Detect`: real-time inter-operator sharing over DLT, and a **cap of twenty outgoing voice calls
  per day** on flagged numbers.

**1600-series is mandatory for regulated sectors, and the deadlines have already passed.** TRAI
Direction dated **19 November 2025** (F. No. G-6/(8)/2025-QoS-Part(1)): entities *"shall not be
permitted to initiate any service or transactional voice calls, **even with the explicit or inferred
consent of customers**, from numbers other than those allocated under the 1600-series, after the
specified dates"*; non-adopters treated *"as per regulatory provisions applicable to unregistered
telemarketer."*

| Regulator | Phase | Entities | Deadline |
| --- | --- | --- | --- |
| RBI | I | Commercial Banks | **1 Jan 2026** |
| RBI | II | Large NBFCs (>₹5000cr), Payments Banks, SFBs | **1 Feb 2026** |
| RBI | III | Remaining NBFCs, Co-op Banks, RRBs | **1 Mar 2026** |
| SEBI | I | Mutual Funds & AMCs | **15 Feb 2026** |
| SEBI | II | Qualified Stockbrokers | **15 Mar 2026** |

A further Direction dated **16 December 2025** extends this to **IRDAI**-regulated entities.

> `UNVERIFIED — HIGH PRIORITY.` Whether Asktrix's clients fall inside the RBI/SEBI/PFRDA/IRDAI
> 1600-series mandate. If the CRM serves BFSI customers, **these deadlines have already passed** and
> non-compliant dialling is treated as unregistered telemarketing. Confirm per client sector, and
> check Directions issued after Feb 2026 (`Direction_27022026.pdf` institutionalises AI/ML
> `UCC_Detect` enforcement).

### 3.3 OSP registration is NOT required — but the logging duties are

Operative instrument: **DoT Revised Guidelines for Other Service Providers, No. 18-8/2020-CS-I (Pt.),
dated 23 June 2021**, *"in supersession of the earlier orders"*.

Chapter-2 ¶1, verbatim: *"The distinction between international OSPs and domestic OSPs has been
removed and **no registration will be required for OSP centres in India.**"*
¶2: *"**No Bank Guarantee** whatsoever will be required…"*
¶4: *"**Entities that are not carrying out voice-based business process outsourcing services shall
not be regulated under the OSP Guidelines.**"*
¶5: self-regulation, *"**no requirement to submit any report/information to the DOT HQ**"*, *"**No
audit/inspection on routine basis**"*.

**Work-From-Anywhere is fully permitted** (Chapter-4) — relevant for field agents: remote agents may
connect *"using **any technology including broadband over wireline/wireless**"*. But *"**The OSP shall
be responsible for any violation related to toll-bypass.**"*

**Chapter-5 security conditions that DO survive and are engineering requirements:**
- ¶6: *"OSPs shall be required to **preserve the CDRs for all the voice traffic**… time-stamp… shall
  be **synchronized with the Indian Standard Time**."*
- ¶7: CDR/UDR must contain *"Calling Number, Called Number, Date, Start Time, End time/duration,
  **Identity of the Device used for making the call (MAC ID, Device signature etc.)**, User identity
  (login name)…"*; system logs must contain login/logout times, commands, and responses.
- ¶8: for Remote Agents, *"the system logs are **tamper-proof**"*, retained **one year**.
- ¶9: *"CDRs / UDRs / System logs… shall be maintained for a period of **one year**."*
- ¶2/¶5: remote access to CDRs, logs, EPABX config and routing tables **on demand to DoT/LEAs**.

**→ Design requirement:** every call must be logged server-side with device identity, IST-synchronised
timestamps, agent login identity, tamper-evidence, and one-year retention. Retrofitting device
identity into CDRs later is painful. This lands in the CRM backend contract, not the app.

### 3.4 You cannot own the SIP↔PSTN path — the decisive constraint

TRAI's *Recommendations on Regulatory framework for Internet Telephony* (24 Oct 2017) quotes the
operative licence clauses verbatim.

**Unified Licence — ACCESS SERVICE authorisation → PSTN interconnection PERMITTED**
(Clause 2.1(a)(i), Chapter-VIII): *"**While providing Internet Telephony service, the Licensee may
interconnect Internet Telephony network with PSTN/PLMN/GMPCS network.**"*

**Unified Licence — INTERNET SERVICE (ISP) authorisation → domestic PSTN PROHIBITED:**
- Clause 2.1(ii) permits Internet Telephony connecting **only** PC-to-PC (in or outside India), a
  conforming device *"in India **to PSTN/PLMN abroad**"*, or device-to-device via static IP.
- Clause 2.2(iii): *"**Voice communication to and from a telephone connected to PSTN/PLMN/GMPCS and
  use of E.164 numbering is prohibited.**"*
- Clause 2.2(iv): *"**Translation of E.164 number / private number to IP address… is not
  permitted.**"* — this forecloses the obvious workaround.

**OSP toll-bypass is a second, independent bar.** Revised OSP Guidelines Chapter-1 ¶10 defines toll
bypass as *"illegal carriage of voice traffic infringing upon the jurisdiction of authorised TSPs"*
including *"(b) **Voice calls between public network (PSTN/PLMN/ISDN) of one city and the other city
in India by transiting it through their own network.**"* Chapter-5 ¶1: *"The OSP shall **not** engage
in the provision of any Telecom Services."*

**Conclusions — these hold regardless of the open item below:**
1. In-app SIP for **app-to-app / intra-org CUG voice over data is fine.** No licence issue.
2. In-app SIP terminating on **Indian PSTN numbers is only lawful if the PSTN leg is provided by a
   holder of UL Access Service authorisation.** You cannot build your own SIP↔PSTN gateway, and you
   cannot ride an ISP authorisation to reach Indian numbers.
3. Routing agent↔customer calls between Indian cities over your own IP network to reach PSTN is
   squarely **toll-bypass definition (b)**, with liability on the OSP.
4. **Safe architectures: (i)** native cellular dial via the device's own SIM with CRM-side logging, or
   **(ii)** a licensed Indian CPaaS owning the PSTN leg. ← we choose (ii).

> `UNVERIFIED — the highest-value open item in this report.` Whether DoT actually implemented TRAI's
> Oct 2017 liberalisation recommendation, i.e. what the ISP-authorisation clauses say **today**. TRAI
> recommendations are not law. The current consolidated UL text could not be retrieved:
> `dot.gov.in/sites/default/files/Unified%20Licence_0.pdf` → 404; `/unified-license` and
> `/internet-telephony` → 403 behind a bot challenge; the UL(VNO) PDF failed with an HTTP/2 framing
> error. Confirm via S.No. 197 of `dot.gov.in/unified-licencing`, Internet Service authorisation
> Clauses 2.1(ii)/2.2(iii)/2.2(iv), plus post-2017 ISP licence amendments.
> **This does not change our architecture** — the OSP toll-bypass prohibition and "shall not engage
> in the provision of any Telecom Services" are from the currently operative 2021 guidelines and
> independently rule out owning the interconnection. Liberalisation would change *whose* licence
> carries the leg, not our need to buy it from a licensee.

### 3.5 Call-recording consent: there is no Indian "beep rule"

**No Indian statute or DoT/TRAI licence condition was found requiring a consent tone, beep, or
pre-call announcement before recording a business call.** Checked: OSP Guidelines (2020 and 2021),
TCCCPR 2018, TCCCPR 2nd Amendment 2025, DPDP Act 2023. None contains such an obligation.

What does bind us is the **DPDP Act 2023** (Act 22 of 2023, Gazette CG-DL-E-12082023-248045,
11 August 2023). A recording of an identifiable customer is personal data:

- **s.4(1)** — processing only *"for a lawful purpose"* (consent, or a s.7 legitimate use).
- **s.5(1)** — every consent request must be *"**accompanied or preceded by a notice**"* stating the
  personal data and purpose, how to exercise rights, and how to complain to the Board.
- **s.5(3)** — notice available in **English or any Eighth Schedule language**.
- **s.6(1)** — consent must be *"**free, specific, informed, unconditional and unambiguous with a
  clear affirmative action**"* and *"limited to such personal data as is necessary"*.
- **s.6(3)** — clear and plain language, with **Data Protection Officer** contact details.
- **s.6(4)** — *"**right to withdraw her consent at any time**, with the ease of doing so being
  comparable to the ease with which such consent was given."*
- **s.5(2)** — pre-Act consents need a retrospective notice *"as soon as it is reasonably
  practicable."*

**Practical read:** the DPDP notice-and-consent architecture — not a telecom beep rule — is what makes
a pre-call disclosure the defensible design. Build: a disclosed recording purpose in the notice, a
per-purpose consent record (who / when / notice version / recording ID), and a withdrawal path. The
OSP CDR duties in 3.3 are *retention* obligations, not recording-consent obligations — do not conflate.

> `NEEDS LEGAL SIGN-OFF / UNVERIFIED`, three items:
> 1. **DPDP Rules 2025 commencement schedule and which provisions are in force as of July 2026.**
>    MeitY `/data-protection-framework` → 403; the Rules PDF path → 404. The Rules supply the
>    prescribed notice **format** that s.5/s.6 defer to — required before writing UI copy.
> 2. Whether any DoT **Unified Licence** condition or IT Act provision imposes a recording
>    announcement duty. The consolidated UL text could not be retrieved (404/403).
> 3. **Telecommunications Act 2023** interception provisions (successor to Indian Telegraph Act
>    s.5(2)) — both candidate DoT PDF paths 404. Note these govern *State* interception, not a
>    business recording its own calls.
>
> Do not let a confident sentence about Indian call-recording consent law enter any client-facing
> document until 1–3 are closed.

---

## 4. Sources

**Android:** `developer.android.com/sdk/api_diff/31/changes/android.net.sip.SipManager`,
`/guide/topics/connectivity/sip`, `/develop/connectivity/telecom/selfManaged`,
`/about/versions/12/behavior-changes-all`, `/about/versions/12/deprecations`;
`android.googlesource.com/platform/prebuilts/sdk/+/master/{8,9,36}/public/api/android.txt`;
`.../frameworks/opt/net/voip/+/master/src/java/android/net/sip/SipManager.java`;
`.../packages/services/Telephony/+/refs/heads/main/AndroidManifest.xml`.

**SDKs:** `pjsip.org/licensing.htm`, `docs.pjsip.org/en/latest/get-started/android/*`,
`/overview/features_codec.html`, `search.maven.org/solrsearch/select?q=g:org.pjsip` (numFound 0);
`github.com/BelledonneCommunications/linphone-sdk` README + LICENSE.txt + `cmake/Options.cmake`,
`linphone-android` README + `linphonerc_default`, `bcg729/LICENSE.txt`,
`download.linphone.org/maven_repository/org/linphone/linphone-sdk-android/maven-metadata.xml`;
`twilio.com/docs/voice/sdks/android`, `repo1.maven.org/maven2/com/twilio/voice-android/`;
`docs.exotel.com/voice-apis/android-sdk-integration`,
`api.github.com/repos/exotel/exotel-voip-sdk-android/contents/`;
`docs.ozonetel.com/docs/product-overview`.

**India regulation:** `trai.gov.in/sites/default/files/2025-02/Regulation_12022025.pdf` (TCCCPR 2nd
Amendment 2025), `/2024-09/RegulationUcc19072018.pdf` (TCCCPR 2018),
`/2025-01/DLT_UCC_28012022.pdf`, `/2025-11/Direction_19112025.pdf` (1600-series RBI/SEBI/PFRDA),
`/2025-12/Direction_16122025.pdf` (IRDAI), `/2025-11/Directions_18112025.PDF`,
`/2024-09/Recommendations_24_10_2017_0.pdf` (Internet Telephony);
`dot.gov.in/static/uploads/2025/08/4e6e21029dc45f42b4e1e57e6300fa72.pdf` (Revised OSP Guidelines
23.06.2021 — operative), `.../37d70ceec29374f291f3a92249bd1f02.pdf` (New OSP Guidelines 05.11.2020);
`meity.gov.in/static/uploads/2024/06/2bf1f0e9f04e6fb4f8fef35e82c42aa5.pdf` (DPDP Act 2023 Gazette).

**Unretrievable (403/404, flagged above):** DoT `/internet-telephony`, `/unified-license`,
consolidated Unified Licence PDF, Telecommunications Act 2023 PDF; MeitY `/data-protection-framework`,
DPDP Rules 2025 PDF.
