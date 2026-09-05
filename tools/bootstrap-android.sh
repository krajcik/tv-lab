#!/usr/bin/env bash
# Optional, isolated macOS arm64 toolchain. Downloads only after explicit invocation.
set -euo pipefail
export LC_ALL=C LANG=C
if [[ "${1:-}" != "--accept-sdk-licenses" ]]; then
  echo 'This downloads Android SDK/Gradle and accepts the Android SDK licenses.'
  echo 'Read https://developer.android.com/studio/terms and run with --accept-sdk-licenses if you agree.'
  exit 2
fi
if [[ "$(uname -sm)" != 'Darwin arm64' ]]; then
  echo 'This bootstrap is for macOS arm64. On other systems install SDK 36 and use ./gradlew.' >&2
  exit 2
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.toolchain"
export ANDROID_HOME="$WORK/android-sdk"
mkdir -p "$WORK" "$ANDROID_HOME/cmdline-tools"
download() { curl --noproxy '*' --fail --location --connect-timeout 20 --max-time 600 "$1" -o "$2"; }
if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  if [[ ! -f "$WORK/android-tools.zip" ]]; then
    download 'https://dl.google.com/android/repository/commandlinetools-mac_arm64-15859902_latest.zip' "$WORK/android-tools.zip"
  fi
  printf '%s  %s\n' '835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e' "$WORK/android-tools.zip" | shasum -a 256 -c -
  unzip -q "$WORK/android-tools.zip" -d "$WORK/android-tools"
  mv "$WORK/android-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
# Invoking this script with its required flag is explicit consent to these licenses.
(set +o pipefail; yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses) > "$WORK/sdk-licenses.log" 2>&1
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;36.0.0'
if [[ ! -x "$WORK/gradle-8.13/bin/gradle" ]]; then
  download 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256' "$WORK/gradle.sha256"
  download 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' "$WORK/gradle.zip"
  printf '%s  %s\n' "$(cat "$WORK/gradle.sha256")" "$WORK/gradle.zip" | shasum -a 256 -c -
  unzip -q "$WORK/gradle.zip" -d "$WORK"
fi
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/local.properties"
echo "Toolchain ready: $WORK"
