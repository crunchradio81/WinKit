#!/usr/bin/env bash
# A29 Mac transfer correctness gate. No Retro-DOS core, SDK, emulator, guest disk, or network.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for cmd in kotlinc java; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 2; }; done
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc \
  "$ROOT/overlay/kotlin/MacTransferArchiveVerifier.kt" \
  "$ROOT/host-tests/a29/MacTransferArchiveChecks.kt" \
  -include-runtime -d "$TMP/a29-transfer-tests.jar"
java -jar "$TMP/a29-transfer-tests.jar"

# Exercise the SAME publication transaction called by the Android adapter,
# with real filesystem operations and deterministic injected failure points.
kotlinc \
  "$ROOT/overlay/kotlin/GuestStore.kt" \
  "$ROOT/overlay/kotlin/MacTransferArchiveVerifier.kt" \
  "$ROOT/overlay/kotlin/MacTransferDiskInstaller.kt" \
  "$ROOT/host-tests/a29/MacTransferDiskInstallerChecks.kt" \
  -include-runtime -d "$TMP/a29-disk-transaction-tests.jar"
java -jar "$TMP/a29-disk-transaction-tests.jar"

echo 'A29 JVM archive and storage integration gates passed; Android APK and DOS/WFW boot NOT verified.'
