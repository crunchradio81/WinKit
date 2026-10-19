#!/bin/bash
set -euo pipefail
DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$DIR"
python3 -m unittest discover -s tests -v
printf '\nTEST STATUS: 10/10 passing locally means utility tests only.\n'
printf 'ANDROID RUNTIME STATUS: UNTESTED\n'
