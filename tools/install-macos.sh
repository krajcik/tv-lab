#!/bin/bash
set -euo pipefail
export LC_ALL=C
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/apps/macos-flow/build/Flow.saver"
DEST="$HOME/Library/Screen Savers/Flow.saver"
codesign --verify --strict "$SOURCE"
if [[ -e "$DEST" ]]; then
    printf '%s\n' "Already installed: $DEST. Preserve it or explicitly replace it before running this installer." >&2
    exit 1
fi
mkdir -p "$HOME/Library/Screen Savers"
ditto "$SOURCE" "$DEST"
codesign --verify --strict "$DEST"
printf '%s\n' "Installed: $DEST" "Choose Поток · TV Lab in System Settings → Screen Saver."
