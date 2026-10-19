#!/usr/bin/env bash
# Publish public, source-only A10 to crunchradio81/WinKit's own Android branch.
# Does not touch Mac disks or modify main; does not force-push or merge.
# Needs configured GitHub git authentication and a normal git identity.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="https://github.com/crunchradio81/WinKit.git"
BRANCH="android-a10-source"
if [[ "${1:-}" == '--test-local-remote' && $# == 2 ]]; then
    TARGET="$2"  # host test only; not user-facing deployment
elif [[ $# != 0 ]]; then
    echo 'Usage: bash scripts/PUBLISH_A10_GITHUB.sh' >&2; exit 2
fi
for cmd in git python3 tar; do command -v "$cmd" >/dev/null || { echo "Missing: $cmd" >&2; exit 2; }; done
python3 "$ROOT/VERIFY_A10.py"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
# Clone must succeed before any writes; the private source tree is read-only.
git clone --quiet --depth 1 --branch main "$TARGET" "$TMP/repo"
[[ "$(git -C "$TMP/repo" branch --show-current)" = main ]] || { echo 'Remote does not have expected main branch' >&2; exit 2; }
if git -C "$TMP/repo" ls-remote --exit-code --heads origin "$BRANCH" >/dev/null 2>&1; then
    echo "Refusing: remote branch $BRANCH exists; no overwrite" >&2; exit 2
fi
git -C "$TMP/repo" switch --quiet -c "$BRANCH"
# Copy only curated source tree, no build output, guest disks, or compiler artifacts.
tar -C "$ROOT" --exclude='./_external' --exclude='./_build_outputs' --exclude='./reference/PoC1/transport/ethnet-poc-jvm.jar' --exclude='./__pycache__' -cf - . \
    | tar -C "$TMP/repo" -xf -
git -C "$TMP/repo" add -A
# Independent last gate: inspect the *Git index*, not just the source tree.
python3 - "$TMP/repo" <<'PY'
import subprocess,sys
from pathlib import Path
repo=sys.argv[1]
paths=subprocess.check_output(['git','-C',repo,'diff','--cached','--name-only','-z']).decode().split('\0')
bad={'.raw','.img','.iso','.vhd','.vhdx','.vmdk','.apk','.jks','.keystore','.jar','.zip','.7z','.dsk','.qcow2','.exe','.dmg'}
blocked=[p for p in paths if p and (Path(p).suffix.lower() in bad or '__pycache__' in Path(p).parts or p.endswith('.partial'))]
if blocked: raise SystemExit('REFUSING private/binary files: '+repr(blocked))
required={'scripts/BUILD_A10_ANDROID.sh','scripts/RUN_A10_HOST_TESTS.sh','.github/workflows/a10-android-arm64.yml','overlay/kotlin/GuestStore.kt','LICENSE.GPL-2.txt'}
missing=required-set(paths)
if missing: raise SystemExit('REFUSING incomplete staged sources: '+repr(sorted(missing)))
print('SAFE staging check:',len([x for x in paths if x]),'changed paths')
PY
if [[ -z "$(git -C "$TMP/repo" diff --cached --name-only)" ]]; then echo 'Nothing staged' >&2; exit 2; fi
git -C "$TMP/repo" commit --quiet -m 'Android A10: cumulative offline source, no-replace guest disk, ARM64 CI recipe'
git -C "$TMP/repo" push origin "$BRANCH"
printf '\nA10 Android source published on %s (main unchanged).\n' "$BRANCH"
printf 'Open a review PR: https://github.com/crunchradio81/WinKit/compare/main...%s\n' "$BRANCH"
printf 'No Android build ran; run a10-android-arm64 after reviewing uploaded source.\n'
