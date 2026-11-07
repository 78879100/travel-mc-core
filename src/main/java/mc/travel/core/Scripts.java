package mc.travel.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 生成 start.bat（Windows）与 start.sh（Linux / macOS）。
 * 脚本每次运行核心时重写，保证内存、端口、引擎与配置保持一致。
 */
public final class Scripts {

    private static final String[] AIKAR = {
            "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
            "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch",
            "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40", "-XX:G1HeapRegionSize=8M",
            "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
            "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
            "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32",
            "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
            "-Dusing.aikars.flags=https://mcflags.emc.gs"
    };

    private Scripts() {
    }

    public static void write(Path root, Config cfg, Engine.Resolved engine) throws IOException {
        Path bat = root.resolve("start.bat");
        Path sh = root.resolve("start.sh");
        Util.writeText(bat, buildBat(root, cfg, engine), true);
        Util.writeText(sh, buildSh(root, cfg, engine), false);
        try {
            java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                    new java.util.HashSet<java.nio.file.attribute.PosixFilePermission>();
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
            Files.setPosixFilePermissions(sh, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 或不支持 POSIX 权限的文件系统，忽略即可
        }
    }

    private static String normalizeMemory(String memory) {
        String m = memory == null ? "4G" : memory.trim().toUpperCase(Locale.ROOT);
        if (m.isEmpty()) {
            m = "4G";
        }
        if (m.endsWith("GB")) {
            m = m.substring(0, m.length() - 1);
        }
        if (!m.endsWith("G") && !m.endsWith("M")) {
            m = m + "M";
        }
        return m;
    }

    private static List<String> jvmArgs(Config cfg) {
        List<String> args = new ArrayList<String>();
        String mem = normalizeMemory(cfg.get("memory", "4G"));
        args.add("-Xms" + mem);
        args.add("-Xmx" + mem);
        for (String flag : AIKAR) {
            args.add(flag);
        }
        args.add("-Dfile.encoding=UTF-8");
        args.add("-Dstdout.encoding=UTF-8");
        args.add("-Dstderr.encoding=UTF-8");
        String extra = cfg.get("extra-jvm-args", "");
        if (!extra.isEmpty()) {
            for (String token : extra.split("\\s+")) {
                if (!token.isEmpty()) {
                    args.add(token);
                }
            }
        }
        return args;
    }

    private static String serverArgs(Config cfg) {
        return cfg.get("server-args", "nogui");
    }

    private static String buildBat(Path root, Config cfg, Engine.Resolved engine) {
        StringBuilder sb = new StringBuilder();
        String javaExe = cfg.get("java", "java");
        sb.append("@echo off\n");
        sb.append("chcp 65001 >nul\n");
        sb.append("title Travel MC  Minecraft ").append(Util.MC_VERSION).append("\n");
        sb.append("color 0B\n");
        sb.append("cd /d \"%~dp0\"\n");
        sb.append("set \"JAVA=").append(javaExe).append("\"\n");
        sb.append("\n");
        sb.append(batBanner());
        sb.append("\n");
        sb.append("echo   Travel MC Core v").append(Util.CORE_VERSION)
                .append("   -   Minecraft ").append(Util.MC_VERSION)
                .append("   -   NeoForge + Paper\n");
        sb.append("echo   ==============================================================\n");
        sb.append("echo   引擎: ").append(engine.displayName).append("\n");
        sb.append("echo   内存: ").append(cfg.get("memory", "4G"))
                .append("   端口: ").append(cfg.get("port", "25565"))
                .append("   模组: ").append(Setup.countJars(root.resolve("mods"))).append(" 个")
                .append("   插件: ").append(Setup.countJars(root.resolve("plugins"))).append(" 个\n");
        sb.append("echo.\n");
        sb.append("echo   正在启动服务器，请稍候...\n");
        sb.append("echo.\n");
        sb.append("\n");
        sb.append("if not exist \"%JAVA%\" (\n");
        sb.append("  if not \"%JAVA%\"==\"java\" (\n");
        sb.append("    echo   [错误] 找不到 java: %JAVA%\n");
        sb.append("    echo   请重新运行 TravelMC-Core.jar 自动修复，或修改 travelmc.properties 里的 java=\n");
        sb.append("    pause\n");
        sb.append("    exit /b 1\n");
        sb.append("  )\n");
        sb.append(")\n");
        sb.append("\n");
        sb.append(":travelmc_start\n");
        sb.append("\"%JAVA%\" ").append(join(jvmArgs(cfg))).append(' ').append(targetBat(engine));
        String sa = serverArgs(cfg);
        if (!sa.isEmpty()) {
            sb.append(' ').append(sa);
        }
        sb.append("\n");

        if (cfg.getBool("auto-restart", true)) {
            sb.append("echo.\n");
            sb.append("echo   [Travel MC] 服务器已退出，5 秒后自动重启（关闭本窗口即可停止）\n");
            sb.append("timeout /t 5 /nobreak >nul\n");
            sb.append("goto travelmc_start\n");
        } else {
            sb.append("echo.\n");
            sb.append("echo   [Travel MC] 服务器已停止。\n");
            sb.append("pause\n");
        }
        return sb.toString();
    }

    private static String buildSh(Path root, Config cfg, Engine.Resolved engine) {
        StringBuilder sb = new StringBuilder();
        String javaExe = cfg.get("java", "java");
        sb.append("#!/usr/bin/env bash\n");
        sb.append("set -u\n");
        sb.append("cd \"$(dirname \"$0\")\" || exit 1\n");
        sb.append("JAVA=\"").append(javaExe).append("\"\n");
        sb.append("\n");
        sb.append("cat <<'TRAVELMC_BANNER'\n");
        sb.append(Banner.text());
        sb.append("TRAVELMC_BANNER\n");
        sb.append("\n");
        sb.append("echo \"  Travel MC Core v").append(Util.CORE_VERSION)
                .append("   -   Minecraft ").append(Util.MC_VERSION)
                .append("   -   NeoForge + Paper\"\n");
        sb.append("echo \"  ==============================================================\"\n");
        sb.append("echo \"  引擎: ").append(engine.displayName).append("\"\n");
        sb.append("echo \"  内存: ").append(cfg.get("memory", "4G"))
                .append("   端口: ").append(cfg.get("port", "25565"))
                .append("   模组: ").append(Setup.countJars(root.resolve("mods"))).append(" 个")
                .append("   插件: ").append(Setup.countJars(root.resolve("plugins"))).append(" 个\"\n");
        sb.append("echo\n");
        sb.append("echo \"  正在启动服务器，请稍候...\"\n");
        sb.append("echo\n");
        sb.append("\n");

        String cmd = "\"$JAVA\" " + join(jvmArgs(cfg)) + " " + targetSh(engine);
        String sa = serverArgs(cfg);
        if (!sa.isEmpty()) {
            cmd += " " + sa;
        }

        if (cfg.getBool("auto-restart", true)) {
            sb.append("while true; do\n");
            sb.append("  ").append(cmd).append("\n");
            sb.append("  code=$?\n");
            sb.append("  echo\n");
            sb.append("  echo \"  [Travel MC] 进程退出（$code），5 秒后自动重启，按 Ctrl+C 停止\"\n");
            sb.append("  sleep 5\n");
            sb.append("done\n");
        } else {
            sb.append(cmd).append("\n");
            sb.append("echo\n");
            sb.append("echo \"  [Travel MC] 服务器已停止。\"\n");
        }
        return sb.toString();
    }

    private static String targetBat(Engine.Resolved engine) {
        if (engine.kind == Engine.Kind.JAR) {
            return "-jar \"server\\" + engine.jar.getFileName().toString() + "\"";
        }
        return "@\"" + engine.argsRelativePath.replace('/', '\\') + "\"";
    }

    private static String targetSh(Engine.Resolved engine) {
        if (engine.kind == Engine.Kind.JAR) {
            return "-jar \"server/" + engine.jar.getFileName().toString() + "\"";
        }
        return "@\"" + engine.argsRelativePath.replace('\\', '/') + "\"";
    }

    private static String join(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(a);
        }
        return sb.toString();
    }

    /** 把大字横幅转成 Windows 批处理的 echo 行。 */
    private static String batBanner() {
        String banner = Banner.text();
        StringBuilder sb = new StringBuilder();
        String[] lines = banner.split("\n");
        for (String line : lines) {
            String trimmed = line.replace("\r", "");
            if (trimmed.trim().isEmpty()) {
                sb.append("echo.\n");
            } else {
                sb.append("echo ").append(trimmed).append('\n');
            }
        }
        return sb.toString();
    }
}
