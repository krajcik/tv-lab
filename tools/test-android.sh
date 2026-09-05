#!/usr/bin/env bash
# Installs test APKs and resets app test data. Intentionally restricted to local emulators.
set -euo pipefail
SERIAL="${1:-}"
if [[ ! "$SERIAL" =~ ^emulator-[0-9]+$ ]]; then
  echo 'Usage: bash tools/test-android.sh emulator-PORT (disposable emulator only)' >&2
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./gradlew :particle-dream:assembleDebug :particle-dream:assembleDebugAndroidTest
adb -s "$SERIAL" install -r apps/particle-dream/build/outputs/apk/debug/particle-dream-debug.apk
adb -s "$SERIAL" install -r apps/particle-dream/build/outputs/apk/androidTest/debug/particle-dream-debug-androidTest.apk
RESULT="$(adb -s "$SERIAL" shell am instrument -w ru.krajcik.tvlab.particles.test/ru.krajcik.tvlab.particles.SmokeInstrumentation)"
printf '%s\n' "$RESULT"
[[ "$RESULT" == *'PASS: default animals, settings launch, EXIF rotation'* ]]
