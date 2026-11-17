#!/usr/bin/env bash
# 构建 Travel AI 插件：dist/TravelAI.jar
# 需要 Java 21+；编译依赖 paper-api，可用 PAPER_API 环境变量指定本地 jar，否则自动从 repo.papermc.io 下载。
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$(pwd)"
BUILD="$ROOT/build"
DIST="$ROOT/dist"
PAPER_API_VERSION="${PAPER_API_VERSION:-26.3.build.252-stable}"
PAPER_API_REPO="${PAPER_API_REPO:-https://repo.papermc.io/repository/maven-public}"

JAVA_HOME_DIR="${JAVA_HOME:-}"
if [ -n "$JAVA_HOME_DIR" ] && [ -x "$JAVA_HOME_DIR/bin/javac" ]; then
  JAVAC="$JAVA_HOME_DIR/bin/javac"
  JAR="$JAVA_HOME_DIR/bin/jar"
else
  JAVAC="javac"
  JAR="jar"
fi

mkdir -p "$BUILD/classes" "$BUILD/modules" "$DIST" "$BUILD/libs"

API_JAR="${PAPER_API:-}"
if [ -z "$API_JAR" ]; then
  API_JAR="$BUILD/libs/paper-api-$PAPER_API_VERSION.jar"
  if [ ! -f "$API_JAR" ]; then
    echo "==> 下载 paper-api $PAPER_API_VERSION"
    curl -sSL --max-time 180 -o "$API_JAR" \
      "$PAPER_API_REPO/io/papermc/paper/paper-api/$PAPER_API_VERSION/paper-api-$PAPER_API_VERSION.jar"
  fi
fi
if [ ! -f "$API_JAR" ]; then
  echo "缺少 paper-api jar" >&2
  exit 1
fi

# paper-api 编译时要用的传递依赖（运行时由服务端自带，不入插件 jar）
fetch() { # url file
  if [ ! -f "$2" ]; then
    echo "==> 下载 $(basename "$2")"
    curl -sSL --max-time 180 -o "$2" "$1"
  fi
}
LIBS="$BUILD/libs"
fetch "https://repo1.maven.org/maven2/net/kyori/adventure-api/5.2.0/adventure-api-5.2.0.jar" "$LIBS/adventure-api.jar"
fetch "https://repo1.maven.org/maven2/net/kyori/adventure-key/5.2.0/adventure-key-5.2.0.jar" "$LIBS/adventure-key.jar"
fetch "https://repo1.maven.org/maven2/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar" "$LIBS/examination-api.jar"
fetch "https://repo1.maven.org/maven2/net/kyori/examination-string/1.3.0/examination-string-1.3.0.jar" "$LIBS/examination-string.jar"
fetch "https://repo1.maven.org/maven2/org/jetbrains/annotations/26.0.1/annotations-26.0.1.jar" "$LIBS/annotations.jar"
fetch "$PAPER_API_REPO/net/md-5/bungeecord-chat/1.21-R0.2-deprecated%2Bbuild.21/bungeecord-chat-1.21-R0.2-deprecated%2Bbuild.21.jar" "$LIBS/bungeecord-chat.jar"
CP="$API_JAR:$LIBS/adventure-api.jar:$LIBS/adventure-key.jar:$LIBS/examination-api.jar:$LIBS/examination-string.jar:$LIBS/annotations.jar:$LIBS/bungeecord-chat.jar"

echo "==> 打包服务商模块"
rm -f "$BUILD/modules"/*.jar
for d in providers/*/; do
  id="$(basename "$d")"
  "$JAR" --create --file "$BUILD/modules/$id.jar" -C "$d" module.json
done
ls -l "$BUILD/modules"

echo "==> 编译插件"
rm -rf "$BUILD/classes"
mkdir -p "$BUILD/classes"
"$JAVAC" --release 21 -encoding UTF-8 -Xlint:-options \
  -cp "$CP" \
  -d "$BUILD/classes" \
  $(find src/main/java -name '*.java')

echo "==> 组装 jar"
cp -r src/main/resources/* "$BUILD/classes/"
mkdir -p "$BUILD/classes/modules"
cp "$BUILD/modules"/*.jar "$BUILD/classes/modules/"
rm -f "$DIST/TravelAI.jar"
"$JAR" --create --file "$DIST/TravelAI.jar" -C "$BUILD/classes" .

echo "==> 完成：$DIST/TravelAI.jar ($(wc -c < "$DIST/TravelAI.jar") 字节)"
