#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$ROOT/scripts/RUN_A6_TESTS.sh"
python3 "$ROOT/host-tests/a7/test_a7_build.py"
printf '\nA7 HOST TESTS PASS; Android compilation and runtime NOT verified.\n'
