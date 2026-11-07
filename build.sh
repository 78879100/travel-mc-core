#!/usr/bin/env bash
# 编译并打包 TravelMC-Core.jar（无需 Gradle / Maven，只用 JDK）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/build/classes"
DIST="$ROOT/dist"

JAVA_HOME="${JAVA_HOME:-}"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
  JAR="$JAVA_HOME/bin/jar"
else
  JAVAC="$(command -v javac || true)"
  JAR="$(command -v jar || true)"
fi

if [ -z "${JAVAC:-}" ] || [ -z "${JAR:-}" ]; then
  echo "[错误] 找不到 javac / jar，请安装 JDK 21 或 25 后重试" >&2
  exit 1
fi

echo "[1/3] 清理输出目录"
rm -rf "$OUT"
mkdir -p "$OUT" "$DIST"

echo "[2/3] 编译 Java 源码（目标 Java 8，兼容各版本 JRE）"
find "$ROOT/src/main/java" -name '*.java' > "$ROOT/build/sources.txt"
"$JAVAC" --release 8 -encoding UTF-8 -Xlint:-options -d "$OUT" @"$ROOT/build/sources.txt"

echo "[3/3] 拷贝资源并打包"
cp -R "$ROOT/src/main/resources/." "$OUT/"
"$JAR" cfe "$DIST/TravelMC-Core.jar" mc.travel.core.Main -C "$OUT" .

echo
echo "完成: $DIST/TravelMC-Core.jar"
ls -lh "$DIST/TravelMC-Core.jar"
