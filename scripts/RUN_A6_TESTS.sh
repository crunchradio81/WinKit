#!/usr/bin/env bash
# Host-only: actual native compilation/execution, x86 fixture + Kotlin/JVM.
# No Mac system, network, Android SDK, Microsoft files or emulated boot.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for cmd in c++ python3 as ld sha256sum; do command -v "$cmd" >/dev/null || { echo "Missing host prerequisite: $cmd" >&2; exit 2; }; done
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
bash "$ROOT/scripts/BUILD_A4_BOOT_FIXTURE.sh" --out "$TMP/fixture"
DISK="$TMP/fixture/machines/YARDOMOB.raw"
CONF="$TMP/fixture/machines/YARDOMOB.offline.conf"
c++ -std=c++17 -Wall -Wextra -Werror -I"$ROOT/overlay/native" \
   "$ROOT/host-tests/a6/test_launch_snapshot.cpp" -o "$TMP/test-launch"
"$TMP/test-launch" "$DISK" "$CONF"
python3 "$ROOT/host-tests/a6/test_a6_patch.py"
python3 "$ROOT/host-tests/a5/test_integration.py"
python3 "$ROOT/host-tests/a2/test_patch.py"
python3 "$ROOT/VERIFY_A6.py"
if [[ "${A6_INCLUDE_A5:-0}" = 1 ]]; then bash "$ROOT/scripts/RUN_A5_TESTS.sh"; fi
printf '\nA6 HOST-GATE PASS. NO Android library/APK or guest boot is asserted.\n'
