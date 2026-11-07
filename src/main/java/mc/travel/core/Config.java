package mc.travel.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * travelmc.properties 的读写。首次运行自动生成，之后以文件内容为准；
 * 用户自己加的键在回写时会被保留。
 */
public final class Config {

    public static final String FILE_NAME = "travelmc.properties";

    private static final String[] KNOWN = {
            "memory", "port", "motd", "max-players", "online-mode", "auto-restart", "auto-run",
            "java", "auto-java", "engine", "neoforge-version", "neoforge-mirror",
            "extra-jvm-args", "server-args"
    };

    private final Path file;
    private final Properties props = new Properties();

    private Config(Path file) {
        this.file = file;
    }

    public static Config load(Path root) throws IOException {
        Path file = root.resolve(FILE_NAME);
        Config cfg = new Config(file);
        if (!Files.exists(file)) {
            Util.writeText(file, template(Util.CORE_VERSION), false);
        }
        InputStream in = Files.newInputStream(file);
        try {
            cfg.props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            in.close();
        }
        return cfg;
    }

    public Path file() {
        return file;
    }

    public String get(String key, String def) {
        String v = props.getProperty(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    public String get(String key) {
        return get(key, "");
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    public void set(String key, String value) {
        if (value != null) {
            props.setProperty(key, value);
        }
    }

    public void save() throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("memory", get("memory", "4G"));
        values.put("port", get("port", "25565"));
        values.put("motd", get("motd", defaultMotd()));
        values.put("max-players", get("max-players", "20"));
        values.put("online-mode", get("online-mode", "true"));
        values.put("auto-restart", get("auto-restart", "true"));
        values.put("auto-run", get("auto-run", "true"));
        values.put("java", get("java", "java"));
        values.put("auto-java", get("auto-java", "true"));
        values.put("engine", get("engine", "auto"));
        values.put("neoforge-version", get("neoforge-version", "auto"));
        values.put("neoforge-mirror", get("neoforge-mirror", "https://bmclapi2.bangbang93.com/maven"));
        values.put("extra-jvm-args", get("extra-jvm-args", ""));
        values.put("server-args", get("server-args", "nogui"));

        StringBuilder sb = new StringBuilder();
        sb.append("# ==============================================================\n");
        sb.append("#  Travel MC Core v").append(Util.CORE_VERSION).append(" 配置文件\n");
        sb.append("#  Minecraft ").append(Util.MC_VERSION).append("  |  NeoForge 模组 + Bukkit 插件\n");
        sb.append("#  修改后重新运行核心即可生效（start.bat / start.sh 会自动重写）\n");
        sb.append("# ==============================================================\n\n");
        sb.append("# 内存分配，例如 4G / 8G / 4096M。建议不超过物理内存的 70%\n");
        sb.append("memory=").append(values.get("memory")).append('\n');
        sb.append("\n# 服务器端口\n");
        sb.append("port=").append(values.get("port")).append('\n');
        sb.append("\n# 服务器列表里显示的描述\n");
        sb.append("motd=").append(values.get("motd")).append('\n');
        sb.append("\n# 最大人数\n");
        sb.append("max-players=").append(values.get("max-players")).append('\n');
        sb.append("\n# 正版验证（true=仅正版账号可进）\n");
        sb.append("online-mode=").append(values.get("online-mode")).append('\n');
        sb.append("\n# 崩溃/关服后自动重启\n");
        sb.append("auto-restart=").append(values.get("auto-restart")).append('\n');
        sb.append("\n# 运行核心后是否立即启动服务器\n");
        sb.append("auto-run=").append(values.get("auto-run")).append('\n');
        sb.append("\n# 使用的 java（默认 java=自动寻找；也可填绝对路径，如 C:/Java/jdk-25/bin/java.exe）\n");
        sb.append("java=").append(values.get("java")).append('\n');
        sb.append("\n# 找不到 Java ").append(Util.MIN_JAVA).append(" 时是否自动下载 Temurin（约 50MB）\n");
        sb.append("auto-java=").append(values.get("auto-java")).append('\n');
        sb.append("\n# 引擎：auto=优先 NeoForge 模组服务端（server/ 里放了核心则直接用）\n");
        sb.append("#       neoforge=只用 NeoForge    paper=只用 Paper（纯插件）\n");
        sb.append("engine=").append(values.get("engine")).append('\n');
        sb.append("\n# NeoForge 版本：auto=自动取 ").append(Util.MC_VERSION).append(" 线最新版本\n");
        sb.append("neoforge-version=").append(values.get("neoforge-version")).append('\n');
        sb.append("\n# NeoForge 依赖镜像，国内网络建议保留；填 none 可关闭\n");
        sb.append("neoforge-mirror=").append(values.get("neoforge-mirror")).append('\n');
        sb.append("\n# 额外的 JVM 参数，例如 -XX:+UseZGC\n");
        sb.append("extra-jvm-args=").append(values.get("extra-jvm-args")).append('\n');
        sb.append("\n# 传给服务端核心的参数，保持 nogui 可减少内存占用\n");
        sb.append("server-args=").append(values.get("server-args")).append('\n');

        // 保留用户自行添加的键
        List<String> extras = new ArrayList<String>();
        for (Object rawKey : props.keySet()) {
            String key = String.valueOf(rawKey);
            boolean known = false;
            for (String k : KNOWN) {
                if (k.equals(key)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                extras.add(key);
            }
        }
        if (!extras.isEmpty()) {
            java.util.Collections.sort(extras);
            sb.append("\n# --- 自定义键 ---\n");
            for (String key : extras) {
                sb.append(key).append('=').append(props.getProperty(key, "")).append('\n');
            }
        }
        Util.writeText(file, sb.toString(), false);
    }

    static String defaultMotd() {
        return "Travel MC  |  " + Util.MC_VERSION + "  |  NeoForge";
    }

    private static String template(String coreVersion) {
        return "# ==============================================================\n"
                + "#  Travel MC Core v" + coreVersion + " 配置文件\n"
                + "#  Minecraft " + Util.MC_VERSION + "  |  NeoForge 模组 + Bukkit 插件\n"
                + "# ==============================================================\n\n"
                + "memory=4G\n\n"
                + "port=25565\n\n"
                + "motd=" + defaultMotd() + "\n\n"
                + "max-players=20\n\n"
                + "online-mode=true\n\n"
                + "auto-restart=true\n\n"
                + "auto-run=true\n\n"
                + "java=java\n\n"
                + "auto-java=true\n\n"
                + "engine=auto\n\n"
                + "neoforge-version=auto\n\n"
                + "neoforge-mirror=https://bmclapi2.bangbang93.com/maven\n\n"
                + "extra-jvm-args=\n\n"
                + "server-args=nogui\n";
    }
}
