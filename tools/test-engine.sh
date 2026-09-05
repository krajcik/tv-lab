#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/apps/particle-dream/build/host-tests"
mkdir -p "$OUT"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVAC" -encoding UTF-8 -d "$OUT" \
  "$ROOT/apps/particle-dream/src/main/java/ru/krajcik/tvlab/particles/ParticleEngine.java" \
  "$ROOT/apps/particle-dream/src/test/java/ru/krajcik/tvlab/particles/ParticleEngineTest.java"
"$JAVA" -cp "$OUT" ru.krajcik.tvlab.particles.ParticleEngineTest
