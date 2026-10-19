#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v java >/dev/null || { echo 'JDK java required' >&2; exit 2; }
command -v kotlinc >/dev/null || { echo 'Kotlin CLI compiler required' >&2; exit 2; }
command -v python3 >/dev/null || { echo 'Python 3 required' >&2; exit 2; }
mkdir -p transport
kotlinc transport/src/main/kotlin/org/winkit/transport/*.kt -include-runtime -d transport/ethnet-poc-jvm.jar
python3 -m unittest discover -s tests -v
