package mc.travel.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NeoForge 安装器与 Paper 的下载安装。
 *
 * <p>官方安装器在国内网络下必然卡在 maven.neoforged.net，这里的做法是：
 * 跑安装器 → 解析日志里缺的依赖 → 从镜像补进 libraries/ → 再跑安装器，最多三轮。</p>
 */
final class EngineInstaller {

    private static final String PAPER_API =
            "https://fill.papermc.io/v3/projects/paper/versions/" + Util.MC_VERSION + "/builds";

    private static final String OFFICIAL_MAVEN = "https://maven.neoforged.net/releases/";
    private static final String DEFAULT_MIRROR = "https://bmclapi2.bangbang93.com/maven/";
    private static final String FALLBACK_NEO_VERSION = "26.3.0.101";

    private EngineInstaller() {
    }

    /** 下载安装器 → 运行安装器 → 缺口依赖走镜像补齐，最多三轮。 */
    static boolean installNeoForge(Path root, Config cfg) throws IOException {
        Path serverDir = root.resolve("server");
        Files.createDirectories(serverDir);
        // 安装目录就是核心根目录：这样 mods/、world/、config/、eula.txt 都在同一层
        Path gameDir = root;
        String java = cfg.get("java", "java");
        String mirror = normalizeMirror(cfg.get("neoforge-mirror", DEFAULT_MIRROR));

        String requested = cfg.get("neoforge-version", "auto").trim();
        String version = requested.isEmpty() || requested.equalsIgnoreCase("auto")
                ? latestNeoForgeVersion(mirror)
                : requested;
        Engine.log("  目标版本: NeoForge " + version);

        String fileName = "neoforge-" + version + "-installer.jar";
        Path installer = serverDir.resolve(fileName);
        if (!Files.isRegularFile(installer) || Files.size(installer) == 0) {
            boolean ok = false;
            String artifactPath = "net/neoforged/neoforge/" + version + "/" + fileName;
            for (String base : installerSources(mirror)) {
                String url = base + artifactPath;
                try {
                    Engine.log("  正在下载安装器: " + url);
                    Util.ProgressBar bar = new Util.ProgressBar(fileName);
                    long size = Util.download(url, installer, bar);
                    bar.finish(size);
                    ok = Files.size(installer) > 0;
                    if (ok) {
                        break;
                    }
                } catch (IOException e) {
                    System.out.println();
                    Engine.log("  下载失败: " + e.getMessage());
                }
            }
            if (!ok) {
                Engine.log("  [错误] 安装器下载失败，请检查网络，或在 travelmc.properties 里换 neoforge-mirror");
                return false;
            }
        }

        Path installLog = serverDir.resolve("neoforge-install.log");
        for (int attempt = 1; attempt <= 3; attempt++) {
            Engine.log("  正在运行 NeoForge 安装器（第 " + attempt + "/3 次），日志: server/neoforge-install.log");
            int code = runInstaller(java, installer, gameDir, installLog);
            Engine.ensureCrossArgs(gameDir);
            if (Engine.findNeoForge(gameDir) != null) {
                Engine.log("  NeoForge " + version + " 安装完成");
                return true;
            }
            int fetched = prefetchMissing(gameDir, installLog, mirror);
            Engine.log("  安装器退出码 " + code + "，本轮从镜像补齐依赖 " + fetched + " 个");
            if (fetched == 0 && code != 0 && attempt > 1) {
                break;
            }
        }
        Engine.log("  [错误] NeoForge 安装没有完成，安装器日志最后几行:");
        printTail(installLog, 12);
        return false;
    }

    private static int runInstaller(String java, Path installer, Path gameDir, Path logFile) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(java);
        cmd.add("-jar");
        cmd.add(installer.toAbsolutePath().toString());
        cmd.add("--installServer");
        cmd.add(gameDir.toAbsolutePath().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        try {
            Process p = pb.start();
            return p.waitFor();
        } catch (IOException e) {
            Engine.log("  无法启动安装器: " + e.getMessage());
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
