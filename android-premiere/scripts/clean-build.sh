#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAVA="$(python3 "$ROOT/android-premiere/scripts/toolchain.py" java)"
APKTOOL="$(python3 "$ROOT/android-premiere/scripts/toolchain.py" apktool)"
LOG="$ROOT/android-premiere/build/clean-build.log"
OUT="$ROOT/android-premiere/build/VLC-clean-unsigned.apk"
python3 "$ROOT/android-premiere/scripts/prepare.py" || exit $?
set +e
"$JAVA" -jar "$APKTOOL" b "$ROOT/android-premiere/work" -o "$OUT" >"$LOG" 2>&1
status=$?
set -e
printf 'apktool clean build exit: %s\n' "$status"
printf 'log: %s\n' "$LOG"
tail -n 100 "$LOG"
exit "$status"
