#!/bin/bash
set -euo pipefail
WORKING="${1:-$HOME/Documents/WinKit/TestSessions/WorkingGuests-20261018-174922-30f2e1c8}"
printf 'Working guest directory: %s\n' "$WORKING"
if [[ ! -d "$WORKING" ]]; then echo 'ERROR: working clone directory not present'; exit 1; fi
for f in "$WORKING"/*-WORKING.raw; do
  [[ -f "$f" ]] || continue
  stat -f 'FILE %N | bytes=%z' "$f" 2>/dev/null || stat -c 'FILE %n | bytes=%s' "$f"
done
printf '\nFound configuration references (read-only; inspect mounted media):\n'
find "$WORKING" -maxdepth 1 -type f \( -name '*.conf' -o -name '*.ini' \) -print | while IFS= read -r f; do
  printf '\n--- %s ---\n' "$(basename "$f")"
  grep -iEn 'imgmount|boot |ethnet|\[ne2000\]|^nic(base|irq)|^backend|^macaddr|\[autoexec\]|^machine|^memsize|^core|^cycles' "$f" || :
done
printf '\nRead-only inspection complete. Source disk image not opened for writing.\n'
