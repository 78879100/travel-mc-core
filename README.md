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
| 自动装 NeoForge | 首次运行自动下载 NeoForge 26.3 安装器，依赖走国内镜像补齐，装完即可用 |
| 自動生成启动脚本 | 每次运行都会重写 `start.bat` / `start.sh`，内含巨幅 TRAVEL MC 横幅 |
| 自动开服 | 准备完直接拉起服务器，崩服后 5 秒自动重启 |
| 模组 + 插件 | 原生 NeoForge 跑模组；`server/` 放入 Youer 之类混合核心即同时跑插件；纯插件可切 Paper |
| 自带优化参数 | 默认 Aikar GC 参数 + UTF-8 控制台，4G 内存起步即可开服 |

## 快速开始

1. 下载 [`TravelMC-Core.jar`](https://github.com/78879100/travel-mc-core/releases/latest)
2. 放进一个空文件夹，双击（或执行 `java -jar TravelMC-Core.jar`）
3. 等它跑完 —— 第一次大约要下一个 100MB 左右的服务端依赖
4. 之后每次开服，双击 `start.bat` 就行

核心自身兼容 Java 8+，但 **Minecraft 26.3 的服务端必须用 Java 25**；没装也没关系，核心会自动下载。

### 首次运行会做什么

```
TRAVEL MC 横幅
 [1/6] 检查运行环境        识别系统、架构、当前 Java
 [2/6] 初始化服务器目录    server/ mods/ plugins/ config/ world/ logs/ backups/
 [3/6] 准备 Java 25 运行时  本机有就用，没有就下载 Temurin 25 到 runtime/
 [4/6] 准备服务端核心      安装 NeoForge 26.3（镜像补依赖，最多重试 3 轮）
 [5/6] 生成启动脚本        start.bat / start.sh，写入横幅、内存、端口
 [6/6] 启动服务器          直接开服，日志同步到控制台
```

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
└── runtime/                自动下载的 Java 25
```

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
JAVA_HOME=/path/to/jdk25 ./build.sh
# 产物：dist/TravelMC-Core.jar
```

推送 `v*` tag 时 GitHub Actions 会自动构建，并把 jar 挂到 Release：

```bash
git tag v1.0.2 && git push origin v1.0.2
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
用 Windows Terminal，或在 cmd 里先 `chcp 65001`。`start.bat` 已经自动设了 UTF-8。

**内存给多少？**
4G 能开服带几个模组；几十个模组建议 6–8G。别超过物理内存的 70%。

**想换回纯插件服？**
把 `engine=paper`，核心会下载 Paper 26.3（SHA-256 校验）并以插件模式启动。

## 许可证与致谢

- 本项目源码：MIT，见 [LICENSE](LICENSE)
- 运行时下载/调用的第三方组件：见 [NOTICE.md](NOTICE.md)
- 感谢 [NeoForged](https://neoforged.net/)、[PaperMC](https://papermc.io/)、[MohistMC / Youer](https://github.com/MohistMC/Youer)、[Adoptium](https://adoptium.net/)、[BMCLAPI](https://bmclapi2.bangbang93.com/)

Minecraft 是 Mojang Studios 的商标，本项目与 Mojang、Microsoft 无隶属关系，也未获其背书。
