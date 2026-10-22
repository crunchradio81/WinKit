#!/usr/bin/env bash
# Fetch only tree data + the two needed text blobs of a pinned, public upstream
# DOSBox-X revision. Does NOT vendor files, build an APK or change A29/A26.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM='https://github.com/joncampbell123/dosbox-x.git'
PIN='029bd65c5dcc7cdc7f2e14e35278620d9707d969'
[[ $# -eq 1 && -n "$1" ]] || { echo "Usage: $0 /absolute/report-output.json" >&2; exit 2; }
[[ "$1" = /* ]] || { echo 'Report output must be an absolute path' >&2; exit 2; }
OUT="$1"
[[ ! -e "$OUT" && ! -L "$OUT" ]] || { echo 'Refusing to replace existing report' >&2; exit 2; }
for cmd in git python3; do command -v "$cmd" >/dev/null || exit 2; done
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git -C "$TMP" init --quiet upstream
git -C "$TMP/upstream" remote add origin "$UPSTREAM"
GIT_TERMINAL_PROMPT=0 git -C "$TMP/upstream" -c protocol.version=2 \
  fetch --no-tags --depth=1 --filter=blob:none origin "$PIN"
git -C "$TMP/upstream" checkout --detach --quiet "$PIN"
python3 "$ROOT/scripts/a30_audit_upstream_core.py" \
  --checkout "$TMP/upstream" --output "$OUT"
