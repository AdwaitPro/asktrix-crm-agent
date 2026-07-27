#!/usr/bin/env bash
#
# One command to bring the whole demo up: CRM + admin console + Android build + emulator install.
#
# Written for a live presentation, so every step prints what it is doing and fails loudly rather
# than half-starting. Safe to re-run.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

say() { printf '\n\033[1;36m▸ %s\033[0m\n' "$1"; }
ok()  { printf '  \033[0;32m✓\033[0m %s\n' "$1"; }
die() { printf '  \033[0;31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

say "1/5  Checking the toolchain"
[ -f scripts/env.sh ] || die "scripts/env.sh missing"
# shellcheck disable=SC1091
source scripts/env.sh >/dev/null
ok "JDK and Android SDK on PATH"

say "2/5  Starting the CRM + admin console"
[ -f server/.env ] || die "server/.env missing — copy server/.env.example and fill in DATABASE_URL"
cd server
[ -d node_modules ] || npm install --silent
# Restart cleanly so a stale process from an earlier run cannot serve old code.
pkill -f "node src/index.js" 2>/dev/null || true
sleep 1
nohup node src/index.js > /tmp/asktrix-crm.log 2>&1 &
sleep 3
curl -sf localhost:4010/health >/dev/null || die "CRM did not start — see /tmp/asktrix-crm.log"
ok "CRM listening on http://localhost:4010"
ok "Admin console at http://localhost:4010/admin/"
cd "$ROOT"

say "3/5  Seeding demo data"
cd server
npm run seed --silent >/dev/null
npm run seed:demo --silent | tail -1
cd "$ROOT"
ok "3 employees, 12 clients, a week of calls, attendance and GPS"

say "4/5  Building the app"
./gradlew assembleDebug -q
ok "app/build/outputs/apk/debug/app-debug.apk"

say "5/5  Installing on a connected device or emulator"
if adb devices | grep -qE "device$"; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
  adb shell am force-stop com.asktrix.agent.dev || true
  adb shell am start -n com.asktrix.agent.dev/com.asktrix.agent.MainActivity >/dev/null
  ok "Installed and launched"
else
  printf '  \033[0;33m!\033[0m No device attached. Start one with:\n'
  printf '      emulator -avd asktrix_pixel &\n'
  printf '      adb install -r app/build/outputs/apk/debug/app-debug.apk\n'
fi

cat <<'SUMMARY'

  ────────────────────────────────────────────────────────────
  Ready.

  Admin console   http://localhost:4010/admin/
                  EMP003 / asktrix123   (team leader)

  Mobile app      EMP001 / asktrix123   (relationship manager)
                  EMP002 / asktrix123   (customer support)

  Walkthrough     docs/DEMO_SCRIPT.md
  ────────────────────────────────────────────────────────────
SUMMARY
