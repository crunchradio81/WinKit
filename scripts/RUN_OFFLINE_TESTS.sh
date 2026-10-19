#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT
printf '\n[1/4] C++ POSIX disk I/O host tests\n'
cmake -S "$ROOT/host-tests" -B "$BUILD/native" -G Ninja -DCMAKE_BUILD_TYPE=Debug
cmake --build "$BUILD/native" --parallel 2
ctest --test-dir "$BUILD/native" --output-on-failure
printf '\n[2/4] Kotlin pure JVM storage + identity tests\n'
kotlinc \
  "$ROOT/android/app/src/main/java/org/winkit/android/GuestStore.kt" \
  "$ROOT/android/app/src/main/java/org/winkit/android/GuestIdentity.kt" \
  "$ROOT/android/app/src/main/java/org/winkit/android/BootProfile.kt" \
  "$ROOT/host-tests/GuestStoreChecks.kt" -include-runtime -d "$BUILD/winkit-a1-checks.jar"
java -jar "$BUILD/winkit-a1-checks.jar"
printf '\n[2b] Source-derived Kotlin ETHNET wire fixtures (not live Mac)\n'
kotlinc \
  "$ROOT/network/experimental/EthnetWire.kt" \
  "$ROOT/network/experimental/EthnetSession.kt" \
  "$ROOT/host-tests/EthnetSourceAuditChecks.kt" -include-runtime -d "$BUILD/ethnet-source-checks.jar"
java -jar "$BUILD/ethnet-source-checks.jar"
printf '\n[3/4] Boot config generator host tests and provenance regression\n'
python3 "$ROOT/host-tests/test_boot_conf.py"
(cd "$ROOT/reference/RD01" && bash RUN_OFFLINE_TESTS.command > "$BUILD/rd01.log" 2>&1)
(cd "$ROOT/reference/PoC1" && bash RUN_OFFLINE_TESTS.sh > "$BUILD/poc1.log" 2>&1)
tail -n 5 "$BUILD/rd01.log"
tail -n 5 "$BUILD/poc1.log"
printf '\n[4/4] Safety/archive checks\n'
python3 "$ROOT/host-tests/test_package.py" "$ROOT"
printf '\nALL OFFLINE TESTS PASSED. Native ARM64, Android SDK, actual boot and real Mac ETHNET NOT tested.\n'
