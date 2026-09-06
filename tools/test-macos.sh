#!/bin/bash
set -euo pipefail
export LC_ALL=C
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/apps/macos-flow/build/validation"
mkdir -p "$OUT"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
SOURCES="$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles"
"$JAVAC" -encoding UTF-8 -d "$OUT" "$SOURCES/SceneSource.java" "$SOURCES/SceneCache.java" "$SOURCES/ScenePlaylist.java" "$SOURCES/SceneTimeline.java" "$SOURCES/FlowField.java" "$SOURCES/ParticleEngine.java" "$ROOT/apps/macos-flow/Tests/MetalReference.java"
"$JAVA" -cp "$OUT" ru.krajcik.tvlab.particles.MetalReference "$OUT/reference.json"
"$ROOT/apps/macos-flow/build/Flow Preview.app/Contents/MacOS/FlowPreview" --validate "$OUT"
xcrun swift "$ROOT/apps/macos-flow/Tests/BundleCheck.swift" "$ROOT/apps/macos-flow/build/Flow.saver"
