package mc.travel.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * travelmc.properties 的读写。首次运行自动生成，之后以文件内容为准。
 */
public final class Config {

    public static final String FILE_NAME = "travelmc.properties";

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
        StringBuilder sb = new StringBuilder();
        sb.append("# ==============================================================\n");
        sb.append("#  Travel MC Core 配置文件\n");
        sb.append("#  修改后重新运行核心即可生效（脚本会在每次启动时自动重写）\n");
        sb.append("# ==============================================================\n\n");
        sb.append("# 内存分配，例如 4G / 8G / 4096M。建议不超过物理内存的 70%\n");
        sb.append("memory=").append(get("memory", "4G")).append('\n');
        sb.append("\n# 端口\n");
        sb.append("port=").append(get("port", "25565")).append('\n');
        sb.append("\n# 服务器列表里显示的描述\n");
        sb.append("motd=").append(get("motd", "Travel MC  |  26.3  |  NeoForge + Paper")).append('\n');
        sb.append("\n# 最大人数\n");
        sb.append("max-players=").append(get("max-players", "20")).append('\n');
        sb.append("\n# 正版验证（true=仅正版账号可进）\n");
        sb.append("online-mode=").append(get("online-mode", "true")).append('\n');
        sb.append("\n# 崩溃/关服后自动重启\n");
        sb.append("auto-restart=").append(get("auto-restart", "true")).append('\n');
        sb.append("\n# 运行核心后是否立即启动服务器\n");
        sb.append("auto-run=").append(get("auto-run", "true")).append('\n');
        sb.append("\n# 使用的 java 命令（可填绝对路径，例如 C:/Java/jdk-25/bin/java.exe）\n");
        sb.append("java=").append(get("java", "java")).append('\n');
        sb.append("\n# 引擎：auto=优先使用 server/ 内已有核心，没有则自动下载 Paper 26.3\n");
        sb.append("engine=").append(get("engine", "auto")).append('\n');
        sb.append("\n# NeoForge 版本（仅在安装 NeoForge 原生核心时使用）\n");
        sb.append("neoforge-version=").append(get("neoforge-version", "26.3.0.99")).append('\n');
        sb.append("\n# 额外的 JVM 参数，例如 -XX:+UseZGC\n");
        sb.append("extra-jvm-args=").append(get("extra-jvm-args", "")).append('\n');
        sb.append("\n# 传给服务端核心的参数，保持 nogui 可减少内存占用\n");
        sb.append("server-args=").append(get("server-args", "nogui")).append('\n');
        Util.writeText(file, sb.toString(), false);
    }

    private static String template(String coreVersion) {
        return "# ==============================================================\n"
                + "#  Travel MC Core v" + coreVersion + " 配置文件\n"
                + "#  修改后重新运行核心即可生效（脚本会在每次启动时自动重写）\n"
                + "# ==============================================================\n\n"
                + "# 内存分配，例如 4G / 8G / 4096M。建议不超过物理内存的 70%\n"
                + "memory=4G\n\n"
                + "# 端口\n"
                + "port=25565\n\n"
                + "# 服务器列表里显示的描述\n"
                + "motd=Travel MC  |  26.3  |  NeoForge + Paper\n\n"
                + "# 最大人数\n"
                + "max-players=20\n\n"
                + "# 正版验证（true=仅正版账号可进）\n"
                + "online-mode=true\n\n"
                + "# 崩溃/关服后自动重启\n"
                + "auto-restart=true\n\n"
                + "# 运行核心后是否立即启动服务器\n"
                + "auto-run=true\n\n"
                + "# 使用的 java 命令（可填绝对路径，例如 C:/Java/jdk-25/bin/java.exe）\n"
                + "java=java\n\n"
                + "# 引擎：auto=优先使用 server/ 内已有核心，没有则自动下载 Paper 26.3\n"
                + "engine=auto\n\n"
                + "# NeoForge 版本（仅在安装 NeoForge 原生核心时使用）\n"
                + "neoforge-version=26.3.0.99\n\n"
                + "# 额外的 JVM 参数，例如 -XX:+UseZGC\n"
                + "extra-jvm-args=\n\n"
                + "# 传给服务端核心的参数，保持 nogui 可减少内存占用\n"
                + "server-args=nogui\n";
    }
}
