#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$ROOT/host-tests/a8/test_a8_build.py"
# The inherited gate compiles the native A6 snapshot test and verifies the
# A7 build-contract tests (no Android toolchain or proprietary images needed).
bash "$ROOT/scripts/RUN_A7_HOST_TESTS.sh"
printf '\nA8 HOST TESTS PASS; no Android ARM64 compilation or boot verified.\n'
