#!/usr/bin/env bash
# A9: On-device evidence gathering only. Does not connect to Mac/Tailscale,
# modify guest storage, enable Ethernet, or claim successful boot.
# Launch/reach the offline smoke guest MANUALLY, then run this script on Linux.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v adb >/dev/null || { echo 'adb not found (install Android platform-tools)' >&2; exit 2; }
DEVICE="${ANDROID_SERIAL:-}"
if [[ -z "$DEVICE" ]]; then
  mapfile -t ONLINE < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#ONLINE[@]} != 1 ]]; then
    echo 'Select exactly one authorized device with ANDROID_SERIAL=...; refusing ambiguous connection' >&2
    exit 2
  fi
  DEVICE="${ONLINE[0]}"
fi
ADB=(adb -s "$DEVICE")
"${ADB[@]}" get-state | grep -qx device || { echo 'Device not ready/authorized' >&2; exit 2; }
PKG=org.winkit.android.a2
"${ADB[@]}" shell pm path "$PKG" | tr -d '\r' | grep -q '^package:' || {
  echo "WinKit app $PKG not installed; install a separately verified APK first" >&2; exit 2;
}
if [[ -n "${1:-}" && "$1" != --out ]]; then
  echo 'Usage: scripts/CAPTURE_A9_ANDROID_BOOT.sh [--out DIRECTORY]' >&2; exit 2
fi
if [[ "${1:-}" == --out ]]; then
  [[ $# == 2 ]] || { echo '--out needs an empty output directory' >&2; exit 2; }
  TARGET="$2"
else
  TARGET="$ROOT/_device_evidence/a9-$(date -u +%Y%m%dT%H%M%SZ)"
fi
[[ ! -e "$TARGET" ]] || { echo 'Refusing to reuse any existing evidence destination' >&2; exit 2; }
mkdir -p -- "$TARGET"
# Never export any guest disk, Tailscale credential, private application data or log-in token.
{
  printf 'WinKit Android A9 local offline boot evidence (NOT an automatic pass)\n'
  printf 'Device ID: %s\n' "$DEVICE"
  printf 'Capture UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'App ID: %s\n' "$PKG"
  echo 'Screenshot/log evidence requires manual visual inspection; no OCR/automatic boot claim.'
  echo 'Do not share the captured files without inspecting them for personal data.'
} > "$TARGET/README.txt"
"${ADB[@]}" shell getprop ro.product.cpu.abi | tr -d '\r' > "$TARGET/device-abi.txt"
"${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r' > "$TARGET/device-api.txt"
"${ADB[@]}" shell pm path "$PKG" > "$TARGET/installed-package-paths.txt"
"${ADB[@]}" shell dumpsys activity activities > "$TARGET/activity-state.txt"
"${ADB[@]}" shell screencap -p > "$TARGET/current-screen.png"
# Since this can include arbitrary app output, do not ship inside source or uploads.
"${ADB[@]}" logcat -d -v brief -s 'retrodos:*' 'SDL:*' 'AndroidRuntime:E' > "$TARGET/emulator-log.txt" || true
[[ -s "$TARGET/current-screen.png" ]] || { echo 'Screenshot capture empty' >&2; exit 2; }
cat > "$TARGET/MANUAL_ACCEPTANCE.txt" <<'CHECK'
[ ] App installed and launch screen visible
[ ] Local standalone BIOS smoke disk installed without a Mac export
[ ] WinKit machine selected in native emulator library
[ ] Screen visibly shows WINKIT A4 BIOS HDD BOOT OK
[ ] Input and quit/relaunch behavior tested
[ ] Original test disk remains app-private and no Mac access occurred
[ ] Networking was not enabled or tested

These boxes MUST be checked by a person using evidence on the device.
A screenshot alone does NOT establish Windows 3.11 boot or NetBEUI.
CHECK
printf 'Captured A9 diagnostic evidence at %s\n' "$TARGET"
printf 'NOT a boot PASS: inspect screenshot and manually check acceptance items.\n'
