#!/usr/bin/env python3
"""Generate an Android Enterprise QR provisioning payload for the Asktrix fleet.

Produces the JSON that a factory-reset device scans during setup to become a fully managed
(Device Owner) device, and renders it as a PNG when the `qrcode` package is available.

Every extras key below is a documented `android.app.extra.PROVISIONING_*` constant. They are spelled
exactly as the platform expects — a typo does not error, it silently fails at the device, which is a
miserable thing to debug while standing over a wiped phone.

IMPORTANT — read before using:

  * QR provisioning only works on a **factory-reset** device, at the very first setup screen. Tap the
    same spot on that screen six times to open the QR scanner. A device that is already set up
    CANNOT be made a Device Owner; it must be wiped first.

  * The DPC this payload installs must be one Android Enterprise has approved. Since 2026 Google
    blocks non-approved custom DPCs at provisioning time with "Harmful app blocked", so the values
    here come from whichever EMM you subscribe to (see docs/adr/0004-device-management.md). The
    Asktrix app is deployed *by* that EMM as a managed Google Play private app; it is not the DPC.

Usage:
    python3 scripts/generate_provisioning_qr.py \\
        --dpc-package com.google.android.apps.work.clouddpc \\
        --dpc-signature-checksum "I5YvS0O5hXY46mb01BlRjq4oJJGs2kuUcHvVkAPEXlg" \\
        --dpc-download-url "https://play.google.com/managed/downloadManagingApp?identifier=setup" \\
        --wifi-ssid "Asktrix-Office" --wifi-password "..." \\
        --enrollment-token "ABCDEF123456" \\
        --out build/provisioning-qr.png
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import pathlib
import sys


def signature_checksum(apk_signature_der: bytes) -> str:
    """Return the URL-safe, unpadded base64 SHA-256 the provisioning extras expect.

    The platform requires SHA-256 encoded with `base64.urlsafe_b64encode`, with trailing `=`
    padding stripped. Standard base64 is a common mistake and fails silently on the device.
    """
    digest = hashlib.sha256(apk_signature_der).digest()
    return base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")


def build_payload(args: argparse.Namespace) -> dict:
    payload: dict[str, object] = {
        # The DPC component or package that becomes Device Owner.
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME": args.dpc_package,
        # Signing-certificate checksum of that DPC. Required, and the device verifies it.
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": args.dpc_signature_checksum,
        # Where to fetch the DPC when it is not already on the device.
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.dpc_download_url,
        # Keep system apps. Removing them tends to break OEM dialers and settings in surprising ways.
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
        # Skip the setup wizard's optional screens so a field enrollment is quick and uniform.
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
    }

    if args.wifi_ssid:
        # Provisioning needs network before it can download the DPC. Without these the operator has
        # to join Wi-Fi by hand on every device.
        payload["android.app.extra.PROVISIONING_WIFI_SSID"] = args.wifi_ssid
        payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = args.wifi_security
        if args.wifi_password:
            payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = args.wifi_password
        payload["android.app.extra.PROVISIONING_WIFI_HIDDEN"] = args.wifi_hidden

    if args.enrollment_token:
        # Passed through to the DPC. For AMAPI-based EMMs this is the enrollment token that binds
        # the device to your enterprise and policy.
        payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = {
            "com.google.android.apps.work.clouddpc.EXTRA_ENROLLMENT_TOKEN": args.enrollment_token,
        }

    if args.timezone:
        payload["android.app.extra.PROVISIONING_TIME_ZONE"] = args.timezone
    if args.locale:
        payload["android.app.extra.PROVISIONING_LOCALE"] = args.locale

    return payload


def render_png(payload_json: str, out_path: pathlib.Path) -> bool:
    try:
        import qrcode  # type: ignore
    except ImportError:
        return False

    # Error correction M: enough redundancy for a printed sticker that gets scuffed, without making
    # the code so dense that a low-end camera struggles.
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=4)
    qr.add_data(payload_json)
    qr.make(fit=True)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    qr.make_image(fill_color="black", back_color="white").save(out_path)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dpc-package", required=True, help="DPC package name, from your EMM")
    parser.add_argument("--dpc-signature-checksum", required=True,
                        help="URL-safe base64 SHA-256 of the DPC signing certificate, from your EMM")
    parser.add_argument("--dpc-download-url", required=True, help="DPC download URL, from your EMM")
    parser.add_argument("--enrollment-token", help="Enrollment token that binds the device to your enterprise")
    parser.add_argument("--wifi-ssid")
    parser.add_argument("--wifi-password")
    parser.add_argument("--wifi-security", default="WPA", choices=["NONE", "WPA", "WEP", "EAP"])
    parser.add_argument("--wifi-hidden", action="store_true")
    parser.add_argument("--timezone", default="Asia/Kolkata")
    parser.add_argument("--locale", default="en_IN")
    parser.add_argument("--out", default="build/provisioning-qr.png")
    args = parser.parse_args()

    payload = build_payload(args)
    payload_json = json.dumps(payload, separators=(",", ":"))

    print("--- Provisioning payload -------------------------------------------------")
    print(json.dumps(payload, indent=2))
    print("--------------------------------------------------------------------------")
    print(f"Payload size: {len(payload_json)} bytes")
    if len(payload_json) > 2000:
        print("WARNING: large payloads produce dense QR codes that cheap cameras struggle to scan.",
              file=sys.stderr)

    out_path = pathlib.Path(args.out)
    if render_png(payload_json, out_path):
        print(f"QR written to {out_path}")
    else:
        raw = out_path.with_suffix(".json")
        raw.parent.mkdir(parents=True, exist_ok=True)
        raw.write_text(payload_json)
        print(f"`qrcode` is not installed, so no PNG was produced. Raw payload written to {raw}")
        print("Install it with:  python3 -m pip install qrcode[pil]")
        print("Or paste the JSON above into any QR generator — the payload is what matters.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
