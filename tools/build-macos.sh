#!/bin/bash
set -euo pipefail
export LC_ALL=C
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/apps/macos-flow/build"
APP="$OUT/Flow Preview.app"
SAVER="$OUT/Flow.saver"
SDK="$(xcrun --show-sdk-path)"
ARCH="$(uname -m)"
mkdir -p "$SAVER/Contents/MacOS" "$SAVER/Contents/Resources" "$APP/Contents/MacOS" "$APP/Contents/Resources"
python3 - "$ROOT" "$SAVER" "$APP" <<'PY'
import pathlib, plistlib, shutil, sys
root, saver, app = map(pathlib.Path, sys.argv[1:])
for bundle, identifier, executable, package in ((saver,'ru.krajcik.tvlab.flow','Flow','BNDL'), (app,'ru.krajcik.tvlab.flow.preview','FlowPreview','APPL')):
    info = dict(CFBundleIdentifier=identifier, CFBundleExecutable=executable, CFBundleName='Поток · TV Lab', CFBundleDisplayName='Поток · TV Lab', CFBundlePackageType=package, CFBundleVersion='1', CFBundleShortVersionString='0.4.0', LSMinimumSystemVersion='13.0', NSHighResolutionCapable=True)
    if package == 'BNDL': info['NSPrincipalClass'] = 'FlowScreenSaver'
    else: info['NSPrincipalClass'] = 'NSApplication'
    (bundle/'Contents/Info.plist').write_bytes(plistlib.dumps(info))
    assets=root/'apps/particle-dream/src/main/res'
    for name in ('images.json','quotes.json'):
        shutil.copy2(assets/'raw'/name,bundle/'Contents/Resources'/name)
    import json
    for name in {x['resource'] for x in json.loads((assets/'raw/images.json').read_text())}:
        shutil.copy2(assets/'drawable-nodpi'/f'{name}.png',bundle/'Contents/Resources'/f'{name}.png')
    shutil.copy2(root/'apps/macos-flow/Sources/Particles.metal',bundle/'Contents/Resources/Particles.metal')
PY
COMMON=(-sdk "$SDK" -target "$ARCH-apple-macosx13.0" -O -module-name Flow -framework Cocoa -framework ScreenSaver -framework Metal -framework QuartzCore)
SOURCES=("$ROOT/apps/macos-flow/Sources/Catalog.swift" "$ROOT/apps/macos-flow/Sources/Renderer.swift" "$ROOT/apps/macos-flow/Sources/ScreenSaver.swift")
xcrun swiftc "${COMMON[@]}" -emit-library "${SOURCES[@]}" -o "$SAVER/Contents/MacOS/Flow"
xcrun swiftc "${COMMON[@]}" "${SOURCES[@]}" "$ROOT/apps/macos-flow/Sources/Validation.swift" "$ROOT/apps/macos-flow/Sources/main.swift" -o "$APP/Contents/MacOS/FlowPreview"
codesign --force --sign - "$SAVER"
codesign --force --sign - "$APP"
codesign --verify --strict "$SAVER"
codesign --verify --strict "$APP"
printf '%s\n' "$SAVER" "$APP"
