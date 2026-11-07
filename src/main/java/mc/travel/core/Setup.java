package mc.travel.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务器目录初始化：文件夹、eula.txt、server.properties、使用说明。
 * 已存在的文件不会被覆盖，避免破坏用户已有配置和存档。
 */
public final class Setup {

    private Setup() {
    }

    public static void ensureRoot(Path root) throws IOException {
        if (Files.exists(root) && !Files.isWritable(root)) {
            throw new IOException("工作目录不可写: " + root);
        }
        Files.createDirectories(root);
    }

    public static void createLayout(Path root) throws IOException {
        String[] dirs = {"server", "mods", "plugins", "config", "logs", "backups", "world"};
        for (String dir : dirs) {
            Files.createDirectories(root.resolve(dir));
        }
    }

    public static boolean ensureEula(Path root) throws IOException {
        Path eula = root.resolve("eula.txt");
        if (Files.exists(eula)) {
            String content = new String(Files.readAllBytes(eula), java.nio.charset.StandardCharsets.UTF_8);
            return content.toLowerCase(java.util.Locale.ROOT).contains("eula=true");
        }
        String text = "# 将下面的 false 改成 true 即表示你同意 Minecraft 最终用户许可协议\n"
                + "# https://aka.ms/MinecraftEULA\n"
                + "# Travel MC Core 首次运行已自动置为 true，若不同意请立即停止使用本服务器\n"
                + "eula=true\n";
        Util.writeText(eula, text, false);
        return true;
    }

    public static void ensureServerProperties(Path root, Config cfg) throws IOException {
        Path file = root.resolve("server.properties");
        if (Files.exists(file)) {
            return;
        }
        String text = "#Minecraft server properties\n"
                + "#Travel MC Core\n"
                + "server-port=" + cfg.get("port", "25565") + "\n"
                + "motd=" + cfg.get("motd", Config.defaultMotd()) + "\n"
                + "max-players=" + cfg.get("max-players", "20") + "\n"
                + "online-mode=" + cfg.get("online-mode", "true") + "\n"
                + "level-name=world\n"
                + "gamemode=survival\n"
                + "difficulty=normal\n"
                + "pvp=true\n"
                + "allow-flight=true\n"
                + "view-distance=10\n"
                + "simulation-distance=10\n"
                + "spawn-protection=16\n"
                + "white-list=false\n"
                + "enable-command-block=false\n"
                + "enable-query=false\n";
        Util.writeText(file, text, false);
    }

    public static void ensureGuide(Path root) throws IOException {
        Path file = root.resolve("TravelMC-使用说明.txt");
        if (Files.exists(file)) {
            return;
        }
        String text = "Travel MC Core v" + Util.CORE_VERSION + " 使用说明\n"
                + "=========================================\n\n"
                + "一、直接启动\n"
                + "  双击 TravelMC-Core.jar（或在终端执行 java -jar TravelMC-Core.jar）。\n"
                + "  核心会自动：\n"
                + "    1) 检查 / 自动下载 Java " + Util.MIN_JAVA + "；\n"
                + "    2) 安装 NeoForge " + Util.MC_VERSION + " 服务端（走国内镜像，首次约 100MB）；\n"
                + "    3) 生成 start.bat / start.sh 并立即开服。\n"
                + "  之后每次开服双击 start.bat 即可。\n\n"
                + "二、目录说明\n"
                + "  server/    服务端核心与 NeoForge 库（自动安装产物，别随便删）\n"
                + "  mods/      NeoForge 模组（.jar），放进来自动加载\n"
                + "  plugins/   Bukkit/Paper 插件（.jar），需要装在混合核心或 Paper 上\n"
                + "  config/    模组与插件生成的配置\n"
                + "  world/     主世界存档（地图）\n"
                + "  logs/      日志\n"
                + "  backups/   备份建议放这里\n"
                + "  runtime/   自动下载的 Java " + Util.MIN_JAVA + "（删掉后会自动重新下载）\n\n"
                + "三、插件 + 模组同时使用\n"
                + "  NeoForge 原生只加载模组，Paper 只加载插件。\n"
                + "  想同时用，请换成 NeoForge + Paper 混合核心（例如 Youer）：\n"
                + "    1) 取得混合核心的 server jar；\n"
                + "    2) 放入 server/ 目录；\n"
                + "    3) 重新运行 TravelMC-Core.jar，核心会自动识别并重写启动脚本。\n"
                + "  server/ 里同时存在多个 jar 时，优先级：youer > neoforge > paper > 其它。\n\n"
                + "四、配置\n"
                + "  修改 travelmc.properties 后重新运行核心即可，启动脚本会自动重写。\n"
                + "  国内网络下载不动时，可改 neoforge-mirror（填 none 表示走官方源）。\n\n"
                + "五、常用命令\n"
                + "  java -jar TravelMC-Core.jar --setup-only        只生成脚本，不启动服务器\n"
                + "  java -jar TravelMC-Core.jar --engine paper      纯插件模式（Paper " + Util.MC_VERSION + "）\n"
                + "  java -jar TravelMC-Core.jar --install-neoforge  强制重装 NeoForge 核心\n"
                + "  java -jar TravelMC-Core.jar --memory 8G         改内存\n"
                + "  java -jar TravelMC-Core.jar --help              查看全部参数\n\n"
                + "六、注意事项\n"
                + "  - Minecraft " + Util.MC_VERSION + " 服务端必须用 Java " + Util.MIN_JAVA + "，低版本会直接启动失败；\n"
                + "  - 首次启动已写入 eula=true，表示你同意 https://aka.ms/MinecraftEULA；\n"
                + "  - 端口默认 25565，开服前记得在防火墙/安全组放行。\n";
        Util.writeText(file, text, false);
    }

    public static int countJars(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            java.util.Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith(".jar") && !name.endsWith(".jar.disabled")) {
                    count++;
                }
            }
        } catch (IOException ignored) {
            return 0;
        }
        return count;
    }
}
