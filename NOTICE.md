# 第三方组件说明

TravelMC-Core.jar 本身只包含本项目源码（MIT）。它在运行时与下列第三方组件交互，这些组件**不随本仓库分发**。

| 组件 | 用途 | 许可证 | 分发方式 |
| --- | --- | --- | --- |
| Paper 26.3 | 服务端核心（插件） | GPL-3.0 | 运行时从 PaperMC 官方 Downloads API 下载，带 SHA-256 校验 |
| NeoForge 26.3 | 模组加载器 | LGPL-2.1 | 由官方 installer 安装，或使用混合核心 |
| Youer 26.3 | NeoForge + Paper 混合核心 | GPL-3.0 | 用户自行放入 `server/`，本项目不代为分发 |
| Minecraft Java Edition 26.3 | 游戏本体服务端 | Mojang EULA | 由上述核心在运行时下载 |

- PaperMC: <https://papermc.io>
- NeoForged: <https://neoforged.net>
- MohistMC / Youer: <https://github.com/MohistMC/Youer>

Minecraft 是 Mojang Studios 的商标。本项目与 Mojang Studios、Microsoft 无隶属关系，也未获得其背书。
