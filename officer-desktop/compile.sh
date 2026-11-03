#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
find src/main/java -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d out
java -cp out vaahansetu.desktop.ui.Main
