#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf test-out
mkdir -p test-out
find src -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d test-out
java -cp test-out vaahansetu.desktop.Member2SmokeCheck
