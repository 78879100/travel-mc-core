# Travel MC Core

**Minecraft Java 版 26.3 服务端核心 ｜ 模组用 NeoForge，插件用 Bukkit/Paper，一个 jar 全管**

把 `TravelMC-Core.jar` 丢进一个空文件夹，双击就行。核心会自动补齐 Java 25、自动安装 NeoForge 26.3、自动生成 `start.bat` 并开服；控制台和启动脚本里都会打出巨大的 **TRAVEL MC** 字样。

```
████████╗██████╗  █████╗ ██╗   ██╗███████╗██╗
╚══██╔══╝██╔══██╗██╔══██╗██║   ██║██╔════╝██║
   ██║   ██████╔╝███████║██║   ██║█████╗  ██║
   ██║   ██╔══██╗██╔══██║╚██╗ ██╔╝██╔══╝  ██║
   ██║   ██║  ██║██║  ██║ ╚████╔╝ ███████╗███████╗
   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝  ╚═══╝  ╚══════╝╚══════╝

███╗   ███╗ ██████╗
████╗ ████║██╔════╝
██╔████╔██║██║
██║╚██╔╝██║██║
██║ ╚═╝ ██║╚██████╗
╚═╝     ╚═╝ ╚═════╝
```

## 特性

| 能力 | 说明 |
| --- | --- |
| 自动补 Java | 找不到 Java 25 时自动下载 Temurin 25 到 `runtime/`，不用手动装 |
| 下载进度条 | 所有下载都是单行动态进度条：`[########--------] 45.2%  26.1 MB / 57.8 MB  paper-26.3-244.jar` |
| 单窗口运行 | 服务器直接在核心所在的那个窗口里启动，不会再弹出第二个 cmd 窗口，也没有蓝底配色 |
| 自动装 NeoForge | 首次运行自动下载 NeoForge 26.3 安装器，依赖走国内镜像补齐，装完即可用 |
| 自带两个核心 | Release 提供 `TravelMC-Core-Full.zip`，包内直接带 NeoForge 26.3 依赖库 + Paper 26.3 服务端 jar，解压双击就能开服 |
| 启动进度条 | 准备启动的 6 个步骤也走进度条：`[###############-------]  66.7%  [4/6] 准备服务端核心` |
| 自動生成启动脚本 | 每次运行都会重写 `start.bat` / `start.sh`；巨幅 TRAVEL MC 横幅由 `banner.txt` 承载，`start.bat` 用 `type` 输出，中文 Windows 不乱码 |
| 自动开服 | 准备完直接拉起服务器，崩服后 5 秒自动重启 |
| 模组 + 插件 | 原生 NeoForge 跑模组；`server/` 放入 Youer 之类混合核心即同时跑插件；纯插件可切 Paper |
| 自带优化参数 | 默认 Aikar GC 参数 + UTF-8 控制台，4G 内存起步即可开服 |
| 内置 AI 助手 | 玩家聊天框 `/AI <问题>` 直接问 AI，支持 DeepSeek / Kimi / 豆包 / GPT；后台 `/ai <名字>` 装服务商、`/ai key` 填 Key（回显打星号） |
| 网页控制面板 | 开服自动打开 `http://127.0.0.1:2000`：左侧玩家列表带头像（在线亮 / 离线暗）、下方命令行、日志实时滚动、一键上传 64×64 服务器图标 |
| 三条管理命令 | `/tcp <配置名> <值>` 改配置并自动重启；`/tcm <模组名>` / `/tic <插件名>` 从 Modrinth 直接下载到 `mods/`、`plugins/` |

## 快速开始

**方式 A：完整包（推荐，离线可用）**

1. 下载 [`TravelMC-Core-Full.zip`](https://github.com/78879100/travel-mc-core/releases/latest)（约 186 MB）
2. 解压到任意文件夹 —— 里面**已经有 NeoForge 26.3 的依赖库**（`libraries/`）**和 Paper 26.3 的 `server/paper-26.3-252.jar`**
3. 双击 `start.bat`（Linux / macOS 执行 `bash start.sh`）直接开服，不再联网下载核心
4. 想跑纯插件模式：把 `travelmc.properties` 里的 `engine` 改成 `paper`

**方式 B：单文件 jar（体积小，首次联网自装）**

1. 下载 [`TravelMC-Core.jar`](https://github.com/78879100/travel-mc-core/releases/latest)
2. 放进一个空文件夹，双击（或执行 `java -jar TravelMC-Core.jar`）
3. 等它跑完 —— 第一次大约要下一个 100MB 左右的服务端依赖
4. 之后每次开服，双击 `start.bat` 就行

核心自身兼容 Java 8+，但 **Minecraft 26.3 的服务端必须用 Java 25**；没装也没关系，核心会自动下载。

### 首次运行会做什么

```
巨幅 TRAVEL MC 横幅（来自 banner.txt）

  [####----------------------]  16.7%  [1/6] 检查运行环境
  [#########-----------------]  33.3%  [2/6] 初始化服务器目录
  [#############-------------]  50.0%  [3/6] 准备 Java 25 运行时
  [#################---------]  66.7%  [4/6] 准备服务端核心
  [######################----]  83.3%  [5/6] 生成启动脚本
  [##########################] 100.0%  [6/6] 启动服务器
```

每一步下面照样打印细节（识别到的系统与 Java、目录清单、引擎版本、模组/插件数量）。

## 目录结构

根目录就是服务器的游戏目录，`mods/`、`world/`、`config/` 与 NeoForge 同层，放进去就会被加载。

```
你的文件夹/
├── TravelMC-Core.jar       核心（只剩它也能重新生成一切）
├── start.bat               Windows 启动脚本（自动生成）
├── start.sh                Linux / macOS 启动脚本（自动生成）
├── travelmc.properties     核心配置
├── server.properties       服务器配置（MOTD 已预设 Travel MC）
├── eula.txt                EULA（首次运行自动写 true）
├── mods/                   NeoForge 模组，丢进去自动加载
├── plugins/                Bukkit/Paper 插件（需要混合核心或 Paper）
├── config/                 模组/插件配置
├── world/                  地图存档
├── logs/                   日志
├── backups/                备份放这里
├── server/                 服务端 jar（自动安装器、或你手动放的 Youer/Paper）
├── libraries/              NeoForge 依赖库（自动安装产物）
├── runtime/                自动下载的 Java 25
└── plugins/TravelPanel/    面板数据（玩家名单、头像缓存、上传的图标）
```

## AI 助手（Travel AI）

核心会把 `TravelAI.jar` 自动释放到 `plugins/TravelAI.jar`，玩家在聊天框就能问 AI：

```
/AI 这附近有什么矿
/AI 帮我写个欢迎公告
```

服主（控制台，或游戏内 OP）先装服务商、再填 Key：

```
/ai deepseek                        # 安装 DeepSeek 模块（也可 kimi / doubao / gpt）
/ai key deepseek sk-xxxxxxxx        # 控制台直接填
/ai key deepseek                    # 游戏内 OP：输完这句，下一条聊天粘贴 Key，回显只有 sk-t****3456（长度 20）
/ai list                            # 看已装什么、Key 填好没
/ai model deepseek deepseek-chat    # 换模型（火山方舟填 endpoint id）
/ai use deepseek                    # 设为默认服务商
```

- 所有玩家都能提问；**只有 OP 能让 AI 代执行服务器指令**
- AI 给出的指令会先列出来，OP 输入 `/ai yes` 才真正执行，`/ai no` 放弃
- 只放行 `say / tp / give / time / weather / gamemode / effect / summon` 等常用指令；`op / deop / stop / reload / execute / whitelist` 一律拦截
- Key 存在 `plugins/TravelAI/keys.properties`，只以掩码形式显示，不写进聊天和日志
- AI 是 Bukkit 插件：需要 `engine=paper` 或 Youer 混合端；原生 NeoForge 模式不加载 Bukkit 插件，核心启动时会直接提示

## 控制面板（Travel Panel）

开服后核心会把 `TravelPanel.jar` 释放到 `plugins/`，并自动打开浏览器访问 **`http://127.0.0.1:2000`**（只绑本机，外网连不上）。

- **左侧玩家列表**：名字 + 头像；在线玩家亮色，离线玩家压暗（0.42 透明度），按最后上线时间排序
- **头像三级兜底**：先读 `plugins/TravelPanel/avatars/` 缓存 → 再试 mc-heads / minotar（4 秒超时）→ 都失败就按名字哈希生成本地像素头像，断网也不会空着
- **下方命令行**：输入什么就直接以控制台身份执行（`say` / `whitelist` / `stop` 都行），回显在日志区
- **日志区**：增量拉 `logs/latest.log`，已清掉 ANSI 颜色码，只取新行，不刷整屏
- **服务器图标**：面板里点上传，**强制校验必须是 64×64 的 PNG**，尺寸不对会直接返回「图片必须是 64×64，当前是 32×32」并拒绝写入；合格的写进 `server-icon.png`，重启后生效
- **不改端口**：想换端口改 `plugins/TravelPanel/config.yml` 的 `panel-port` / `panel-bind` / `open-browser`

## 三条管理命令

| 命令 | 作用 | 权限 |
| --- | --- | --- |
| `/tcp <配置名> <值>` | 改配置：开关写 `t` / `f`，数字直接写数字；写到 `server.properties`（有这个键时）或 `travelmc.properties`，**3 秒后自动重启服务器**让新值生效 | `travelpanel.tcp`（默认 OP） |
| `/tcm <模组名>` | 从 Modrinth 搜模组，只取支持 NeoForge 的最新版本，下载到 `mods/` | `travelpanel.download`（默认 OP） |
| `/tic <插件名>` | 同上，取支持 Paper/Bukkit 的版本，下载到 `plugins/` | `travelpanel.download`（默认 OP） |

```
/tcp motd TRAVEL MC 欢迎你
/tcp online-mode f
/tcp max-players 50
/tcp memory 8G
/tcm jei
/tic chunky
```

- 下载是「先写 `.part` 再改名」，中途断网不会留下半个坏 jar；下完会打印 `已下载 Chunky 1.5.66 → plugins/Chunky-Paper-1.5.66.jar (387 KB)，重启服务器后生效`
- 搜索按名字打分（slug / 标题精确匹配优先），所以 `/tic spark` 不会再误抓成 `sparklogin`
- 找不到适配 26.3 的版本时会明确告诉你试过哪些项目，而不是硬塞一个不兼容的 jar
- **只有 OP 能改配置**；非 OP 只能看面板，让 AI 代执行指令的权限同样只给 OP（见上一节 `/ai yes`）
- `/tcp` 改的是「真正生效」的那个文件：`server.properties` 里有这个键就改它，否则改 `travelmc.properties`；改完 3 秒重启，实测重启后新值不会被 Paper 停机回写覆盖
- 启动脚本的内存参数不再写死，运行时从 `travelmc.properties` 现读，所以 `/tcp memory 8G` 重启就生效，不用重跑核心

## 插件 + 模组

26.3 目前的服务端生态分三档，本核心都管：

| 引擎 | 插件（Bukkit/Paper） | 模组（NeoForge） | 怎么来 |
| --- | --- | --- | --- |
| **NeoForge 26.3**（默认） | ❌ | ✅ | 核心自动下载安装 |
| Paper 26.3 | ✅ | ❌ | `engine=paper` 或把 jar 放进 `server/` |
| **Youer 26.3**（NeoForge + Paper 混合） | ✅ | ✅ | 自行取得 jar，放进 `server/` 自动接管 |

**想让插件和模组同时生效**：把混合核心（Youer）的 server jar 放进 `server/`，再运行一次核心。核心会认出它、重写启动脚本，`mods/` 和 `plugins/` 同时生效，横幅里的引擎名也会变。

> 说明：Youer 目前公开版本只到 1.21.11，26.3 需要从 MohistMC 的 GitHub Actions 夜间构建取，官方没有公开二进制。本核心不会替你分发第三方核心，但只要你把 jar 放进 `server/`，剩下的全自动。
>
> 这是唯一诚实的现状：26.3 的「插件 + 模组」双支持依赖上游混合端，核心这边做到了自动识别与接管。

## 配置文件 `travelmc.properties`

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `memory` | `4G` | 内存，支持 `4G` / `8G` / `4096M` |
| `port` | `25565` | 服务器端口 |
| `motd` | `Travel MC \| 26.3 \| NeoForge` | 服务器列表描述 |
| `max-players` | `20` | 最大人数 |
| `online-mode` | `true` | 正版验证 |
| `auto-restart` | `true` | 崩服后 5 秒自动重启 |
| `auto-run` | `true` | 运行核心后立即开服 |
| `java` | `java` | java 路径；核心会把找到的 Java 25 写回来 |
| `auto-java` | `true` | 找不到 Java 25 时是否自动下载 |
| `engine` | `auto` | `auto`（优先 NeoForge）/ `neoforge` / `paper` |
| `neoforge-version` | `auto` | 自动取 26.3 线最新版本 |
| `neoforge-mirror` | `bmclapi2` | 依赖镜像；填 `none` 走官方源 |
| `extra-jvm-args` | 空 | 追加 JVM 参数 |
| `server-args` | `nogui` | 传给服务端核心的参数 |

改完重新运行一次核心，启动脚本会按新配置重写。

## 命令行参数

```
java -jar TravelMC-Core.jar [参数]

--dir <路径>        指定服务器目录（默认 jar 所在目录）
--engine <类型>     auto / neoforge / paper
--memory <大小>     例如 4G、8G、4096M
--port <端口>       服务器端口
--java <路径>       指定 java 可执行文件
--setup-only        只生成目录与脚本，不启动服务器
--install-neoforge  强制重装 NeoForge
--version           显示版本
--help              显示帮助
```

## 从源码构建

只依赖 JDK（21 或 25 都行），不需要 Gradle / Maven：

```bash
git clone https://github.com/78879100/travel-mc-core.git
cd travel-mc-core

# 1) 先编两个插件（核心会把它们内嵌进 jar）
JAVA_HOME=/path/to/jdk25 ./plugin/build.sh   # 产物：plugin/dist/TravelAI.jar
JAVA_HOME=/path/to/jdk25 ./panel/build.sh    # 产物：panel/dist/TravelPanel.jar

# 2) 再编核心
JAVA_HOME=/path/to/jdk25 ./build.sh
# 产物：dist/TravelMC-Core.jar（内含 plugins/TravelAI.jar + plugins/TravelPanel.jar）

# 3) 想生成完整包（NeoForge 依赖 + Paper 核心 + 两个插件）
JAVA_HOME=/path/to/jdk25 ./make-bundle.sh
```

`plugin/build.sh` 与 `panel/build.sh` 都会自动从 `repo.papermc.io` 拉 `paper-api` 编译依赖，也可以 `PAPER_API=/path/to/paper-api.jar` 指定本地 jar。

推送 `v*` tag（或直接 push main）时 GitHub Actions 会自动构建，并把 `TravelMC-Core.jar`、`TravelMC-Core-Full.zip`、`TravelAI.jar`、`TravelPanel.jar` 一起挂到 Release：

```bash
git tag v1.2.0 && git push origin v1.2.0
```

源码结构：

```
src/main/java/mc/travel/core/
├── Main.java            入口、参数解析、6 步流程编排
├── Banner.java          巨幅 TRAVEL MC 横幅
├── Config.java          travelmc.properties 读写
├── Setup.java           目录、eula、server.properties、使用说明
├── JavaEnv.java         Java 25 定位 / 自动下载 / 解压
├── Engine.java          引擎识别、NeoForge 产物定位、版本比较
├── EngineInstaller.java 安装器下载与运行、镜像补依赖、Paper 下载
├── Scripts.java         start.bat / start.sh 生成
└── Util.java            下载、进度条、校验、编码等工具

plugin/src/main/java/mc/travel/ai/
├── TravelAI.java        插件主类：/ai 子命令、聊天拦截填 Key、异步调 AI
├── Json.java            零依赖 JSON 解析/生成
├── Http.java            纯 JDK HTTP 调用（聊天补全）
├── Modules.java         服务商模块安装（内置 modules/<id>.jar）
├── KeyStore.java        API Key 存储与掩码
└── Guard.java           指令白名单 + 从回答里抽 ```mc 代码块

panel/src/main/java/mc/travel/panel/
├── TravelPanel.java     面板主类：HttpServer 路由、/tcp /tcm /tic、玩家与头像、图标校验
└── Json.java            零依赖 JSON 解析（解析 Modrinth 响应）
```

## 常见问题

**NeoForge 装到一半失败？**
安装器直连 `maven.neoforged.net` 在国内经常超时。核心会解析安装器日志，把缺的依赖从 `bmclapi` 镜像补进 `libraries/` 再重跑安装器，最多三轮。仍然失败时：

- 检查 `neoforge-mirror` 是否可用（默认 `https://bmclapi2.bangbang93.com/maven`）
- 或在有网络代理的机器上装好，把整个文件夹拷过来

**下载时进度条一直在刷？**
进度条用 `\r` 原地重绘，最快 12 帧每秒，不会刷屏；下载完成后会换行，后面的日志不会和它糊在一起。如果终端不支持回车覆盖（少数旧 cmd），会看到多行进度，不影响下载结果。

**端口能连上吗？**
默认 25565，记得在系统防火墙 / 云服务器安全组放行。

**控制台方块字是乱码？**
启动脚本已经设了 `chcp 65001`。如果用的终端本身字体缺方块字符，换成 Windows Terminal 或等宽字体（如 Cascadia Mono / Consolas）。

**`start.bat` 报 `'��║' is not recognized as an internal or external command`？**
这是 v1.0.4 及更早版本的 BUG，v1.0.5 已修：横幅不再 `echo` 进 `.bat`，改为 `banner.txt` + `type`。升级 jar 后重新运行一次核心，`start.bat` 与 `banner.txt` 会自动重写。

**内存给多少？**
4G 能开服带几个模组；几十个模组建议 6–8G。别超过物理内存的 70%。

**想换回纯插件服？**
把 `engine=paper`，核心会下载 Paper 26.3（SHA-256 校验）并以插件模式启动。

## 许可证与致谢

- 本项目源码：MIT，见 [LICENSE](LICENSE)
- 运行时下载/调用的第三方组件：见 [NOTICE.md](NOTICE.md)
- 感谢 [NeoForged](https://neoforged.net/)、[PaperMC](https://papermc.io/)、[MohistMC / Youer](https://github.com/MohistMC/Youer)、[Adoptium](https://adoptium.net/)、[BMCLAPI](https://bmclapi2.bangbang93.com/)

Minecraft 是 Mojang Studios 的商标，本项目与 Mojang、Microsoft 无隶属关系，也未获其背书。
