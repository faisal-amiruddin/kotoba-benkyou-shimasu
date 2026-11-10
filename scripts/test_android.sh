#!/usr/bin/env bash
# Collect screenshots even when a device assertion fails; keep Gradle's result.
set -uo pipefail
gradle -p android --no-daemon --continue :app:connectedDebugAndroidTest :lite:connectedDebugAndroidTest
test_status=$?
adb pull /sdcard/Pictures/KotobaTests android/screenshots
exit "$test_status"
