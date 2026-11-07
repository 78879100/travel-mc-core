# 更新日志

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
