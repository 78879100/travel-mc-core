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

    /**
     * 安装器在国内网络下会卡在 maven.neoforged.net。这里读安装器日志里列出的下载地址，
     * 把缺的 jar 用镜像直接放进 libraries/，下一轮安装器就能跳过下载。
     */
    private static int prefetchMissing(Path gameDir, Path logFile, String mirror) {
        if (!Files.isRegularFile(logFile)) {
            return 0;
        }
        List<String> urls = new ArrayList<String>();
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("Downloading library from (https://\\S+)");
            for (String line : lines) {
                Matcher m = p.matcher(line);
                if (m.find()) {
                    urls.add(m.group(1));
                }
            }
        } catch (IOException e) {
            return 0;
        }
        int fetched = 0;
        for (int index = 0; index < urls.size(); index++) {
            String url = urls.get(index);
            String rel = libraryRelativePath(url);
            if (rel == null) {
                continue;
            }
            Path target = gameDir.resolve("libraries").resolve(rel);
            if (Files.isRegularFile(target) && sizeOf(target) > 0) {
                continue;
            }
            if (mirror.isEmpty()) {
                continue;
            }
            String name = target.getFileName().toString();
            Util.ProgressBar bar = new Util.ProgressBar(name + "  (依赖 " + (index + 1) + "/" + urls.size() + ")");
            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    long size = Util.download(mirror + rel, target, bar);
                    bar.finish(size);
                    fetched++;
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    System.out.println();
                    Util.sleep(1500);
                }
            }
            if (last != null) {
                Engine.log("  依赖补齐失败: " + rel + " (" + last.getMessage() + ")");
            }
        }
        return fetched;
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    /** https://maven.neoforged.net/releases/net/... 转成 net/... */
    private static String libraryRelativePath(String url) {
        String marker = "maven.neoforged.net/releases/";
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        String rel = url.substring(idx + marker.length());
        int q = rel.indexOf('?');
        return q >= 0 ? rel.substring(0, q) : rel;
    }

    private static List<String> installerSources(String mirror) {
        List<String> list = new ArrayList<String>();
        if (!mirror.isEmpty()) {
            list.add(mirror);
        }
        list.add(OFFICIAL_MAVEN);
        return list;
    }

    private static String normalizeMirror(String mirror) {
        String m = mirror == null ? "" : mirror.trim();
        if (m.equalsIgnoreCase("none") || m.equalsIgnoreCase("off")) {
            return "";
        }
        if (m.isEmpty()) {
            return DEFAULT_MIRROR;
        }
        if (!m.endsWith("/")) {
            m = m + "/";
        }
        return m;
    }

    /** 从镜像的 maven-metadata.xml 里挑出 26.3 线的最高版本。 */
    static String latestNeoForgeVersion(String mirror) {
        List<String> bases = new ArrayList<String>();
        if (!mirror.isEmpty()) {
            bases.add(mirror);
        }
        bases.add(OFFICIAL_MAVEN);
        for (String base : bases) {
            String found = latestFrom(base + "net/neoforged/neoforge/maven-metadata.xml");
            if (found != null) {
                return found;
            }
        }
        Engine.log("  版本列表获取失败，使用默认 " + FALLBACK_NEO_VERSION);
        return FALLBACK_NEO_VERSION;
    }

    private static String latestFrom(String url) {
        try {
            String xml = Util.httpText(url);
            String best = null;
            Matcher m = Pattern.compile("<version>(" + Pattern.quote(Util.MC_VERSION) + "\\.\\d+)</version>").matcher(xml);
            while (m.find()) {
                String v = m.group(1);
                if (best == null || Engine.compareVersions(v, best) > 0) {
                    best = v;
                }
            }
            return best;
        } catch (IOException e) {
            Engine.log("  镜像不可用（" + e.getMessage() + "），换下一个源");
            return null;
        }
    }

    private static void printTail(Path file, int lines) {
        try {
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            for (int i = from; i < all.size(); i++) {
                String line = all.get(i);
                if (!line.contains("at java.") && !line.contains("at net.minecraftforge")) {
                    Engine.log("    " + line);
                }
            }
        } catch (IOException ignored) {
            // 日志都读不到就不再报错
        }
    }

    /** 从官方 Downloads API 获取 Paper 26.3 并校验 SHA-256 后下载。 */
    static java.util.Optional<Engine.Resolved> downloadPaper(Path serverDir) throws IOException {
        String json;
        try {
            json = Util.httpText(PAPER_API);
        } catch (IOException e) {
            Engine.log("  官方 API 不可达: " + e.getMessage());
            return java.util.Optional.empty();
        }

        int marker = json.indexOf("\"server:default\"");
        if (marker < 0) {
            marker = 0;
        }
        String slice = json.substring(marker, Math.min(json.length(), marker + 2000));

        Matcher urlMatcher = Pattern.compile("\"url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"").matcher(slice);
        if (!urlMatcher.find()) {
            Engine.log("  未能从 API 响应中解析出下载地址");
            return java.util.Optional.empty();
        }
        String url = urlMatcher.group(1);
        String fileName = url.substring(url.lastIndexOf('/') + 1);

        String sha256 = null;
        Matcher shaMatcher = Pattern.compile("\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"").matcher(slice);
        if (shaMatcher.find()) {
            sha256 = shaMatcher.group(1).toLowerCase(Locale.ROOT);
        }

        Path target = serverDir.resolve(fileName);
        if (Files.exists(target) && sha256 != null) {
            String actual = Util.sha256(target);
            if (actual.equalsIgnoreCase(sha256)) {
                Engine.log("  已存在且校验通过: " + fileName);
                return java.util.Optional.of(Engine.Resolved.jar(target));
            }
        }

        Engine.log("  正在下载 " + fileName);
        Util.ProgressBar bar = new Util.ProgressBar(fileName);
        long size = Util.download(url, target, bar);
        bar.finish(size);
        Engine.log("  下载完成: " + Util.human(size));

        if (sha256 != null) {
            String actual = Util.sha256(target);
            if (!actual.equalsIgnoreCase(sha256)) {
                Files.deleteIfExists(target);
                throw new IOException("SHA-256 校验失败，文件已删除，请重试");
            }
            Engine.log("  SHA-256 校验通过");
        }
        return java.util.Optional.of(Engine.Resolved.jar(target));
    }
}
