#!/usr/bin/env bash
# Complete all Android 16 checks and retain diagnostics even if one fails.
set +e
python3 android-premiere/scripts/smoke_notifications.py
notification_rc=$?
python3 android-premiere/scripts/smoke_android.py
media_rc=$?
printf 'Android 16 notification result: %s\nAndroid 16 media result: %s\n' "$notification_rc" "$media_rc"
if [ "$notification_rc" -ne 0 ] || [ "$media_rc" -ne 0 ]; then
  echo "Android 16 acceptance failed; inspect both uploaded evidence folders."
  exit 1
fi
echo "Android 16 notifications and playback smoke passed."
