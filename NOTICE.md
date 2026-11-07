# 第三方组件说明

`TravelMC-Core.jar` 本身只包含本项目源码（MIT）。它在运行时下载、安装或调用下列第三方组件，这些组件**不随本仓库分发**。

| 组件 | 用途 | 许可证 | 获取方式 |
| --- | --- | --- | --- |
| Minecraft Java Edition 26.3 服务端 | 游戏本体 | Mojang EULA | 由 NeoForge 安装器在运行时从 Mojang 官方源下载 |
| NeoForge 26.3 | 模组加载器（默认引擎） | LGPL-2.1 | 运行时从 `maven.neoforged.net` 或其镜像下载安装器并执行 `--installServer` |
| Paper 26.3 | 服务端核心（纯插件模式） | GPL-3.0 | 运行时从 PaperMC `fill.papermc.io` 下载，带 SHA-256 校验 |
| Youer（NeoForge + Paper 混合核心） | 插件 + 模组双支持 | GPL-3.0 | **用户自行取得并放入 `server/`**，本项目不分发、不代为下载 |
| Eclipse Temurin 25 (JRE) | Java 运行时 | GPL-2.0 + Classpath Exception | 本机没有 Java 25 时，从 Adoptium API 下载到 `runtime/` |
| BMCLAPI 镜像 | NeoForge 依赖加速 | 第三方公共服务 | 仅作为 Maven 依赖镜像地址使用，不参与代码分发 |

- NeoForged: <https://neoforged.net>
- PaperMC: <https://papermc.io>
- MohistMC / Youer: <https://github.com/MohistMC/Youer>
- Adoptium: <https://adoptium.net>
- BMCLAPI: <https://bmclapi2.bangbang93.com>

如果不需要自动安装，可以把 `neoforge-mirror` 设为 `none`，或手动把服务端核心放进 `server/`，核心会跳过所有下载直接使用你的文件。

Minecraft 是 Mojang Studios 的商标。本项目与 Mojang Studios、Microsoft 无隶属关系，也未获得其背书。
