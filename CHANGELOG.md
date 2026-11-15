# 更新日志

## v1.0.4

- Release 新增 `TravelMC-Core-Full.zip`（约 186 MB）：包内直接带两个核心，解压双击 `start.bat` 就能开服
  - NeoForge 26.3.0.101 完整服务端依赖：`libraries/net/neoforged/...`、`unix_args.txt`、`win_args.txt`
  - Paper 26.3 的 Bukkit 核心：`server/paper-26.3-252.jar`（SHA-256 校验通过，52.7 MB）
  - 打包时把 `travelmc.properties` 的 `java=` 复原为 `java`，换机器也不会指向构建机路径
  - 自动清掉安装器日志、`world/`、`logs/`、`usercache.json` 等运行痕迹
- 新增 `make-bundle.sh`：一条命令生成完整包；CI 每次构建都会自动打这个 zip 并一起挂到 Release
- 实测：完整包解压后直接开服成功（NeoForge 引擎 `Done (3.057s)`；`engine=paper` 时识别为 `paper-26.3-252.jar`）

## v1.0.3

- 只开一个窗口：运行核心后服务器直接在**当前窗口**里跑，不再用 `start` 另开一个新的 cmd 窗口
- 去掉 `start.bat` 里的 `color 0B`，不再出现蓝底/浅蓝字的 cmd 窗口，保持终端默认配色
- 关闭窗口即停止服务器（和自动重启逻辑一致），不再需要「核心窗口 + 服务器窗口」两个窗口

## v1.0.2

- 下载显示改成单行动态进度条：`[########----------------]  41.8%  24.1 MB / 57.8 MB  paper-26.3-244.jar`
  - 进度条、百分比、已下载/总大小、文件名（含后缀）在同一行原地刷新，不再刷屏
  - 安装器、Temurin 25、Paper、以及镜像补齐的每个依赖都有各自的进度条
  - 下载完成后自动换行，日志不会和进度条糊在一起
- 内部结构：引擎识别（Engine）与下载安装（EngineInstaller）拆成两个类，单文件更好维护

## v1.0.1

**默认引擎换成 NeoForge，并把「自动」两个字落到实处。**

- 默认引擎改为 NeoForge 26.3：首次运行自动下载安装器并执行 `--installServer`，装完直接开服
- 新增镜像补依赖：安装器直连 `maven.neoforged.net` 超时时，核心解析安装器日志，把缺失依赖从 BMCLAPI 镜像直接落进 `libraries/`，再重跑安装器（最多 3 轮）——国内网络也能装上
- NeoForge 版本自动取 26.3 线最新（`neoforge-version=auto`），并按数值而非字符串排序
- 新增自动补 Java 25：本机没有就用本机已有的，再没有就下载 Temurin 25 到 `runtime/` 并解压（tar.gz / zip 都支持）
- 目录布局统一：NeoForge 安装到核心根目录，`mods/`、`world/`、`config/`、`eula.txt` 与它同层，模组放进去直接生效
- `start.bat` 增加 java 存在性检查；`start.sh` 与 `start.bat` 都不再 `cd server`
- 配置文件新增 `auto-java`、`neoforge-mirror`、`neoforge-version=auto`；回写时保留用户自己加的键
- 控制台流程改为 6 步，明确展示 Java 版本、引擎版本、模组/插件加载情况
- Paper 26.3 保留为 `engine=paper` 的纯插件模式

## v1.0.0

- 首个版本：TRAVEL MC 横幅、目录初始化、`start.bat` / `start.sh` 自动生成、Paper 26.3 自动下载、引擎自动识别（Youer / NeoForge / Paper）
