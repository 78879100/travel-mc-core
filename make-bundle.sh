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
python3 - "$BUNDLE" <<'PY_PAPER'
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
PY_PAPER

echo "[4/4] 生成便携启动脚本并打包"
# 打包后配置里的 java= 和启动脚本里的 java 路径都是构建机的，换机器就失效。
# 所以包里的 start.bat / start.sh 改成「先跑核心」，由核心去找 Java 25（缺了自动下载）、
# 认出现成的 NeoForge / Paper，再生成真正带绝对 java 路径的启动脚本并开服。
python3 - "$BUNDLE" <<'PY_LAUNCHER'
import os, re, sys

bundle = sys.argv[1]

prop = os.path.join(bundle, "travelmc.properties")
if os.path.exists(prop):
    lines = open(prop, encoding="utf-8").read().splitlines()
    out = []
    for line in lines:
        if re.match(r"^\\s*java\\s*=", line):
            out.append("java=java")
        else:
            out.append(line)
    open(prop, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")

bat = r"""@echo off
chcp 65001 >nul
title Travel MC  Minecraft 26.3
cd /d "%~dp0"

set "JAVA="
where java >nul 2>nul && set "JAVA=java"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Java\jdk*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Java\jre*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Microsoft\jdk*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Zulu\zulu*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\Amazon Corretto\jdk*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%ProgramFiles%\BellSoft\LibericaJDK*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
if not defined JAVA for /d %%d in ("%LocalAppData%\Programs\Eclipse Adoptium\jdk*") do if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"

if not defined JAVA (
  echo.
  echo   [错误] 这台机器上没有找到 java
  echo   请安装 Java 25+: https://adoptium.net/
  echo   或者直接双击本目录里的 TravelMC-Core.jar（双击 jar 会用它自己找到的 java）
  echo.
  pause
  exit /b 1
)

echo   使用 Java: %JAVA%
"%JAVA%" -jar "TravelMC-Core.jar"
echo.
pause
"""
sh = """#!/usr/bin/env bash
set -u
cd "$(dirname "$0")" || exit 1
exec java -jar TravelMC-Core.jar
"""

open(os.path.join(bundle, "start.bat"), "w", encoding="utf-8", newline="\n").write(bat)
open(os.path.join(bundle, "start.sh"), "w", encoding="utf-8", newline="\n").write(sh)
os.chmod(os.path.join(bundle, "start.sh"), 0o755)
print("      已写入便携启动脚本（start.bat / start.sh -> TravelMC-Core.jar）")
PY_LAUNCHER

rm -rf "$BUNDLE/logs" "$BUNDLE/world" "$BUNDLE/server/neoforge-install.log"
rm -f "$BUNDLE"/server/*.log "$BUNDLE"/*.log "$BUNDLE"/hs_err_pid*.log
rm -f "$BUNDLE"/usercache.json "$BUNDLE"/banned-ips.json "$BUNDLE"/banned-players.json \
      "$BUNDLE"/ops.json "$BUNDLE"/whitelist.json

cd "$ROOT/dist"
zip -qr "TravelMC-Core-Full.zip" "TravelMC-Core-Full"
echo
echo "完成: $ZIP"
du -h "$ZIP" | sed 's/^/      /'
