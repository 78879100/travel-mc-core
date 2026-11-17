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
# 内嵌 Travel AI 插件：核心首次运行会自动把它释放到 plugins/TravelAI.jar
if [ -f "$ROOT/plugin/dist/TravelAI.jar" ]; then
  mkdir -p "$OUT/plugins"
  cp "$ROOT/plugin/dist/TravelAI.jar" "$OUT/plugins/TravelAI.jar"
  echo "      已内嵌 TravelAI.jar ($(wc -c < "$ROOT/plugin/dist/TravelAI.jar") 字节)"
else
  echo "      [提示] 没有 plugin/dist/TravelAI.jar，先跑 plugin/build.sh 才能把 AI 插件打进核心"
fi
"$JAR" cfe "$DIST/TravelMC-Core.jar" mc.travel.core.Main -C "$OUT" .

echo
echo "完成: $DIST/TravelMC-Core.jar"
ls -lh "$DIST/TravelMC-Core.jar"
