#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAVA="$(python3 "$ROOT/android-premiere/scripts/toolchain.py" java)"
APKTOOL="$(python3 "$ROOT/android-premiere/scripts/toolchain.py" apktool)"
if [[ ! -f "$ROOT/android-premiere/tools/android.jar" ]]; then
    printf 'Missing Android API 30 platform jar: %s\nSet it up from $ANDROID_SDK_ROOT/platforms/android-30/android.jar.\n' "$ROOT/android-premiere/tools/android.jar" >&2
    exit 2
fi
LOG="$ROOT/android-premiere/build/build.log"
OUT="$ROOT/android-premiere/dist/VLC-Premiere-unsigned.apk"
mkdir -p "$ROOT/android-premiere/build" "$ROOT/android-premiere/dist"
python3 "$ROOT/android-premiere/scripts/prepare.py" || exit $?
python3 "$ROOT/android-premiere/scripts/patch.py" "$ROOT/android-premiere/work" || exit $?
python3 "$ROOT/android-premiere/scripts/polish.py" "$ROOT/android-premiere/work"
python3 "$ROOT/android-premiere/scripts/restore_libraries.py" "$ROOT/android-premiere/work"
python3 "$ROOT/android-premiere/scripts/restore_resources.py" "$ROOT/android-premiere/work"
python3 "$ROOT/android-premiere/scripts/native_ui.py" prepare
python3 "$ROOT/android-premiere/scripts/validate_bindings.py"
python3 "$ROOT/android-premiere/scripts/validate_resource_fields.py"
set +e
"$JAVA" -jar "$APKTOOL" b "$ROOT/android-premiere/work" -o "$OUT" >"$LOG" 2>&1
status=$?
set -e
printf 'apktool build exit: %s\n' "$status"
printf 'log: %s\n' "$LOG"
printf 'output: %s\n' "$OUT"
tail -n 80 "$LOG"
if [ "$status" -ne 0 ]; then exit "$status"; fi
python3 "$ROOT/android-premiere/scripts/native_ui.py" inject
python3 "$ROOT/android-premiere/scripts/sign.py"
