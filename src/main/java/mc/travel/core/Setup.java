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


    /** 核心自带的两个插件。核心里已内嵌一份，plugins/ 里没有就释放；连内嵌都没有（用户拿的是旧核心）时从 GitHub Release 下载。 */
    public static final String[][] BUNDLED_PLUGINS = {
            {"TravelAI.jar", "AI 聊天助手（/AI 问题）"},
            {"TravelPanel.jar", "控制面板（127.0.0.1:2000）+ /tcp /tcm /tic"}
    };

    private static final String RELEASE_LATEST =
            "https://github.com/78879100/travel-mc-core/releases/latest/download/";

    /**
     * 保证 plugins/ 里两个插件都在。
     * 返回释放/下载成功的插件名列表（已存在的跳过，返回空列表）。
     */
    public static java.util.List<String> ensurePlugins(Path root) throws IOException {
        java.util.List<String> done = new java.util.ArrayList<String>();
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        for (String[] item : BUNDLED_PLUGINS) {
            String name = item[0];
            Path target = plugins.resolve(name);
            if (Files.exists(target) && Files.size(target) > 1024) {
                continue;
            }
            java.io.InputStream in = Setup.class.getClassLoader().getResourceAsStream("plugins/" + name);
            if (in != null) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
                in.close();
                Files.write(target, buf.toByteArray());
                done.add(name);
                continue;
            }
            // 核心里没有内嵌资源：从 GitHub Release 补一个
            System.out.println("      plugins/" + name + " 不在核心里，正在从 GitHub 下载…");
            Util.ProgressBar bar = new Util.ProgressBar(name);
            Util.download(RELEASE_LATEST + name, target, bar);
            bar.finish(Files.size(target));
            done.add(name);
        }
        return done;
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
                + "五、AI 助手（Travel AI）\n"
                + "  plugins/TravelAI.jar 已自动放好，玩家在聊天框输入：\n"
                + "    /AI 问题内容                     直接问 AI\n"
                + "  服主在服务器后台（控制台）输入：\n"
                + "    /ai deepseek                    安装 DeepSeek 模块（也支持 kimi / doubao / gpt）\n"
                + "    /ai key deepseek <你的Key>      填 API Key（游戏内 OP 输入 /ai key deepseek 后在下一条聊天粘贴，显示为 ****）\n"
                + "    /ai list                        看已装哪些服务商、Key 是否填好\n"
                + "  权限：所有玩家都能提问；只有 OP 能让 AI 代执行服务器指令，\n"
                + "        并且要先输入 /ai yes 确认，只放行 say/tp/give/time/weather 等常用指令。\n"
                + "  AI 插件是 Bukkit 插件，需要 engine=paper 或 Youer 混合端才会加载。\n\n"
                + "六、控制面板（Travel Panel）\n"
                + "  开服后会自动打开 http://127.0.0.1:2000/ （只监听本机，不对外网开放）：\n"
                + "    - 左侧：在这个服玩过的人，在线亮色、离线灰暗，头像自动拉取；\n"
                + "    - 底部：命令行，直接敲 say 你好 / tcp memory 8G 这类命令，输出实时回显；\n"
                + "    - 左上角图标：点一下上传服务器图标，必须是 64×64 的 PNG，否则会被拒绝。\n"
                + "  三条命令（后台或游戏内 OP）：\n"
                + "    /tcp <配置名> <值>      改配置（server.properties 里有的项就写那里，否则写 travelmc.properties），\n"
                + "                            t=开 f=关 数字=数字，改完 3 秒后自动重启；\n"
                + "    /tcp <配置名>           只看当前值；不带参数列出全部配置\n"
                + "    /tcm <模组名>           从 Modrinth 下载 NeoForge 模组到 mods/\n"
                + "    /tic <插件名>           从 Modrinth 下载 Paper/Bukkit 插件到 plugins/\n"
                + "  面板和这三个命令都在 plugins/TravelPanel.jar 里，需要 engine=paper 或 Youer 混合端。\n\n"
                + "七、常用命令\n"
                + "  java -jar TravelMC-Core.jar --setup-only        只生成脚本，不启动服务器\n"
                + "  java -jar TravelMC-Core.jar --engine paper      纯插件模式（Paper " + Util.MC_VERSION + "）\n"
                + "  java -jar TravelMC-Core.jar --install-neoforge  强制重装 NeoForge 核心\n"
                + "  java -jar TravelMC-Core.jar --memory 8G         改内存\n"
                + "  java -jar TravelMC-Core.jar --help              查看全部参数\n\n"
                + "八、注意事项\n"
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
