#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKING="${1:-$HOME/Documents/WinKit/TestSessions/WorkingGuests-20261018-174922-30f2e1c8}"
SOURCE="${2:-$WORKING/YARDO-B-WORKING.raw}"
OUTPUT_BASE="${3:-$HOME/Documents/WinKit/AndroidExports}"
python3 "$ROOT/scripts/common/export_guest.py" --source "$SOURCE" --output-base "$OUTPUT_BASE"
