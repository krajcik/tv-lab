#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/apps/particle-dream/build/host-tests"
mkdir -p "$OUT"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVAC" -encoding UTF-8 -d "$OUT" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/ParticleModes.java" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/SceneSource.java" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/SceneCache.java" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/ScenePlaylist.java" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/SceneTimeline.java" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/FlowField.java" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/ParticleEngine.java" \
  "$ROOT/apps/particle-dream/src/test/java/ru/krajcik/tvlab/particles/ParticleEngineTest.java" \
  "$ROOT/apps/particle-dream/src/test/java/ru/krajcik/tvlab/particles/SceneCacheTest.java" \
  "$ROOT/apps/particle-dream/src/test/java/ru/krajcik/tvlab/particles/ParticleModesTest.java"
"$JAVA" -cp "$OUT" ru.krajcik.tvlab.particles.ParticleEngineTest

"$JAVA" -Xmx64m -cp "$OUT" ru.krajcik.tvlab.particles.SceneCacheTest

"$JAVA" -cp "$OUT" ru.krajcik.tvlab.particles.ParticleModesTest
