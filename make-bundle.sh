#!/usr/bin/env bash
# 生成「自带核心」的完整发行包：TravelMC-Core-Full.zip
#   包内直接就有 NeoForge 26.3 的依赖库（libraries/）和 Paper 26.3 的 server jar，
#   用户解压后双击 start.bat 就能开服，不需要再联网下载任何服务端核心。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="$ROOT/dist/TravelMC-Core.jar"
BUNDLE="$ROOT/dist/TravelMC-Core-Full"
ZIP="$ROOT/dist/TravelMC-Core-Full.zip"

if [ ! -f "$JAR" ]; then
  echo "[错误] 先执行 ./build.sh 生成 dist/TravelMC-Core.jar" >&2
  exit 1
fi

JAVA_BIN="${JAVA:-}"
if [ -z "$JAVA_BIN" ]; then
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  else
    JAVA_BIN="$(command -v java || true)"
  fi
fi
if [ -z "$JAVA_BIN" ]; then
  echo "[错误] 找不到 java，无法执行 NeoForge 安装器" >&2
  exit 1
fi

echo "[1/4] 准备目录结构"
rm -rf "$BUNDLE" "$ZIP"
mkdir -p "$BUNDLE/server"
cp "$JAR" "$BUNDLE/TravelMC-Core.jar"

echo "[2/4] 安装 NeoForge 26.3（官方源失败时自动走 BMCLAPI 镜像补依赖）"
"$JAVA_BIN" -jar "$BUNDLE/TravelMC-Core.jar" --dir "$BUNDLE" --install-neoforge --setup-only

echo "[3/4] 下载 Paper 26.3 到 server/"
python3 - "$BUNDLE" <<'PY'
import hashlib, json, sys, urllib.request

bundle = sys.argv[1]
api = "https://fill.papermc.io/v3/projects/paper/versions/26.3/builds"
req = urllib.request.Request(api, headers={"User-Agent": "TravelMC-Core/1.0.4"})
builds = json.load(urllib.request.urlopen(req, timeout=60))
dl = builds[0]["downloads"]["server:default"]
name, url = dl["name"], dl["url"]
want = dl["checksums"]["sha256"].lower()

target = bundle + "/server/" + name
print("      正在下载 " + name)
req = urllib.request.Request(url, headers={"User-Agent": "TravelMC-Core/1.0.4"})
with urllib.request.urlopen(req, timeout=300) as r, open(target, "wb") as f:
    data = r.read()
    f.write(data)

got = hashlib.sha256(data).hexdigest()
if got != want:
    print("[错误] Paper SHA-256 校验失败", file=sys.stderr)
    sys.exit(1)
print("      校验通过: " + name + " (" + str(round(len(data) / 1048576.0, 1)) + " MB)")
PY

echo "[4/4] 清理运行痕迹并打包"
# 安装产物里的 java= 是构建机的绝对路径，改回 java，让用户机器自己找 Java 25
if [ -f "$BUNDLE/travelmc.properties" ]; then
  python3 - "$BUNDLE/travelmc.properties" <<'PY'
import re, sys
p = sys.argv[1]
lines = open(p, encoding="utf-8").read().splitlines()
out = []
for line in lines:
    if re.match(r"^\s*java\s*=", line):
        out.append("java=java")
    else:
        out.append(line)
open(p, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
PY
fi
rm -rf "$BUNDLE/logs" "$BUNDLE/world" "$BUNDLE/server/neoforge-install.log"
rm -f "$BUNDLE"/server/*.log "$BUNDLE"/*.log "$BUNDLE"/hs_err_pid*.log
rm -f "$BUNDLE"/usercache.json "$BUNDLE"/banned-ips.json "$BUNDLE"/banned-players.json \
      "$BUNDLE"/ops.json "$BUNDLE"/whitelist.json

cd "$ROOT/dist"
zip -qr "TravelMC-Core-Full.zip" "TravelMC-Core-Full"
echo
echo "完成: $ZIP"
du -h "$ZIP" | sed 's/^/      /'
