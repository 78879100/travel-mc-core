# 更新日志

## v1.1.0

- 新增 AI 助手插件 `TravelAI`（Bukkit 插件，随核心自动释放到 `plugins/TravelAI.jar`）
  - 玩家聊天框 `/AI <问题>` 直接问 AI；支持 DeepSeek / Kimi / 豆包（火山方舟）/ GPT
  - 后台（控制台）`/ai deepseek|kimi|doubao|gpt` 安装对应服务商模块（内置 `modules/<id>.jar`，解压到 `plugins/TravelAI/modules/`）
  - `/ai key <名字> <Key>` 填 Key；游戏内 OP 只输 `/ai key <名字>`，下一条聊天内容即 Key，回显仅掩码 `sk-t****3456（长度 20）`，不写日志
  - `/ai list`、`/ai model`、`/ai use`、`/ai base` 管理服务商；Key 存 `plugins/TravelAI/keys.properties`（POSIX 0600）
  - 权限：所有玩家可提问；只有 OP 能让 AI 代执行服务器指令，且必须 `/ai yes` 二次确认
  - 指令白名单：`say/tell/tp/give/time/weather/gamemode/effect/summon/tellraw/title/...`；
    黑名单机制挡死 `op/deop/stop/reload/execute/whitelist/ban/save-*/plugins` 等
  - AI 只能把指令写在 ```mc 代码块里，插件抽取后执行；非 OP 只显示文字、指令块被丢弃
  - 单玩家 3 秒冷却、可配超时、保留最近 4 轮对话
- 核心 v1.1.0
  - `TravelMC-Core.jar` 内嵌 `plugins/TravelAI.jar`，首次运行自动释放（已存在则不覆盖）
  - 启动摘要与兼容性提示新增 AI 行：paper 模式直接告诉怎么用；NeoForge 模式提示要改 `engine=paper` 或换 Youer
  - `TravelMC-使用说明.txt` 增加「五、AI 助手」章节
  - `make-bundle.sh` 把 `TravelAI.jar` 一起放进完整包的 `plugins/`
  - CI 先编插件再编核心，Release 多挂一个 `TravelAI.jar` 附件
- 实测（Paper 26.3 build 253 + JDK 25）：插件正常加载，`/ai deepseek` 安装、`/ai key` 掩码回显、
  `/ai <问题>` 走真实 HTTP 链路、`/ai yes` 执行 `say`/`list`、`/stop` 被白名单拦截

## v1.0.5

- 修复大字横幅在 Windows 中文控制台报 `'��║' is not recognized as an internal or external command`
  - 原因：中文 Windows 控制台代码页是 GBK(936)，cmd 解析 UTF-8 的 `.bat` 时按 GBK 解码；`║`/`═` 的字节序列会把后面的 ASCII 字节当成 GBK 尾字节吞掉，token 错位被当成命令名。`chcp 65001` 写在文件里也救不了读取期解码
  - 改法：横幅不再塞进 `.bat`，核心单独生成 UTF-8 的 `banner.txt`，`start.bat` 改成 `if exist "banner.txt" (type "banner.txt") else (echo TRAVEL MC)`；`type` 原样输出字节，不参与 cmd 解析
  - `start.sh` 仍用 heredoc 内嵌横幅（bash 无此问题）
- 启动准备流程改成进度条：`Util.StepBar`（26 格 + 百分比 + `[n/6] 步骤名`），6 个准备步骤各占一行
  - 形如 `[###############-------]  66.7%  [4/6] 准备服务端核心`
- 已在 Windows 11 + JDK 26 实机验证：横幅完整显示、无任何 `is not recognized`、服务器 `Done (0.336s)` 正常开服

## v1.0.4

- Release 新增 `TravelMC-Core-Full.zip`（约 186 MB）：包内直接带两个核心，解压双击 `start.bat` 就能开服
  - NeoForge 26.3.0.101 完整服务端依赖：`libraries/net/neoforged/...`、`unix_args.txt`、`win_args.txt`
  - Paper 26.3 的 Bukkit 核心：`server/paper-26.3-252.jar`（SHA-256 校验通过）
  - 打包时把 `travelmc.properties` 的 `java=` 复原为 `java`，换机器也不会指向构建机路径
  - 自动清掉安装器日志、`world/`、`logs/`、`usercache.json` 等运行痕迹
- 新增 `make-bundle.sh`：一条命令生成完整包；CI 每次构建都会自动打这个 zip 并一起挂到 Release

## v1.0.3

- 只开一个窗口：运行核心后服务器直接在**当前窗口**里跑，不再用 `start` 另开一个新的 cmd 窗口
- 去掉 `start.bat` 里的 `color 0B`，不再出现蓝底/浅蓝字的 cmd 窗口，保持终端默认配色
- 关闭窗口即停止服务器（和自动重启逻辑一致），不再需要「核心窗口 + 服务器窗口」两个窗口

## v1.0.2

- 下载显示改成单行动态进度条：`[########----------------]  41.8%  24.1 MB / 57.8 MB  paper-26.3-244.jar`
  - 进度条、百分比、已下载/总大小、文件名（含后缀）在同一行原地刷新，不再刷屏
  - 安装器、Temurin 25、Paper、以及镜像补齐的每个依赖都有各自的进度条
  - 下载完成后自动换行，日志不会和进度条糊在一起

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
