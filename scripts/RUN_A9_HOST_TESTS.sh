#!/usr/bin/env bash
# A9 offline policy parity + capture tests, with inherited A8/A7/A6 regressions.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$ROOT/host-tests/a9/test_capture_contract.py"
python3 "$ROOT/host-tests/a9/test_policy_parity.py"
if [[ "${A9_SKIP_INHERITED:-0}" != 1 ]]; then
  bash "$ROOT/scripts/RUN_A8_HOST_TESTS.sh"
fi
python3 "$ROOT/VERIFY_A9.py"
echo 'A9 HOST SUITE PASS (NOT AN ANDROID ARM64 COMPILE OR GUEST BOOT)'
