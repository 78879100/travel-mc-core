package mc.travel.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 服务端引擎解析。
 *
 * <p>默认引擎是 NeoForge（模组），首次运行会自动下载安装器并用国内镜像补依赖；
 * 如果 server/ 里已经放了 Youer 之类的混合核心或者 Paper，会优先使用用户放进去的核心。
 * 纯插件场景可以用 engine=paper 强制走 Paper。</p>
 */
public final class Engine {

    public enum Kind {
        /** 单个 jar 核心，例如 paper-26.3-244.jar、youer-26.3-server.jar。 */
        JAR,
        /** NeoForge 官方安装器产出的 @args 启动方式。 */
        NEOFORGE_ARGS
    }

    public static final class Resolved {
        public final Kind kind;
        public final Path jar;
        public final String argsRelativePath;
        public final String displayName;

        private Resolved(Kind kind, Path jar, String argsRelativePath, String displayName) {
            this.kind = kind;
            this.jar = jar;
            this.argsRelativePath = argsRelativePath;
            this.displayName = displayName;
        }

        public static Resolved jar(Path jar) {
            return new Resolved(Kind.JAR, jar, null, jar.getFileName().toString());
        }

        public static Resolved neoForge(Path argsFile, String displayName) {
            return new Resolved(Kind.NEOFORGE_ARGS, null, argsFile.toString(), displayName);
        }

        /** 是否为 NeoForge + Paper 的混合核心。 */
        public boolean isHybrid() {
            String n = displayName.toLowerCase(Locale.ROOT);
            return n.contains("youer") || n.contains("hybrid");
        }

        public boolean isPaper() {
            String n = displayName.toLowerCase(Locale.ROOT);
            return n.contains("paper") || n.contains("purpur") || n.contains("spigot");
        }

        public boolean isNeoForge() {
            return kind == Kind.NEOFORGE_ARGS || displayName.toLowerCase(Locale.ROOT).contains("neoforge");
        }
    }

    private static final String PAPER_API =
            "https://fill.papermc.io/v3/projects/paper/versions/" + Util.MC_VERSION + "/builds";

    private static final String OFFICIAL_MAVEN = "https://maven.neoforged.net/releases/";
    private static final String DEFAULT_MIRROR = "https://bmclapi2.bangbang93.com/maven/";
    private static final String FALLBACK_NEO_VERSION = "26.3.0.101";

    private Engine() {
    }

    public static Resolved resolve(Path root, Config cfg) throws IOException {
        Path serverDir = root.resolve("server");
        Files.createDirectories(serverDir);
        String mode = cfg.get("engine", "auto").toLowerCase(Locale.ROOT);
        if (mode.equals("hybrid")) {
            mode = "auto";
        }

        if (mode.equals("paper")) {
            Path existing = pickJar(serverDir);
            if (existing != null) {
                return Resolved.jar(existing);
            }
            return downloadPaper(serverDir).orElse(null);
        }

        Resolved neo = findNeoForge(root);
        if (neo != null) {
            return neo;
        }

        Path existing = pickJar(serverDir);
        if (existing != null) {
            return Resolved.jar(existing);
        }

        log("  未检测到服务端核心，开始安装 NeoForge " + Util.MC_VERSION + "（模组模式）");
        if (installNeoForge(root, cfg)) {
            neo = findNeoForge(root);
            if (neo != null) {
                return neo;
            }
        }

        if (mode.equals("neoforge")) {
            return null;
        }

        log("  NeoForge 安装未完成，回退到 Paper " + Util.MC_VERSION + "（插件模式）");
        return downloadPaper(serverDir).orElse(null);
    }

    /** 在 server/ 中挑选最合适的 core jar。 */
    public static Path pickJar(Path serverDir) throws IOException {
        List<Path> jars = new ArrayList<Path>();
        Stream<Path> stream = Files.list(serverDir);
        try {
            java.util.Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar") || name.contains("installer") || name.endsWith(".part")) {
                    continue;
                }
                jars.add(p);
            }
        } finally {
            stream.close();
        }
        if (jars.isEmpty()) {
            return null;
        }
        java.util.Collections.sort(jars, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                int ra = rank(a);
                int rb = rank(b);
                if (ra != rb) {
                    return Integer.compare(ra, rb);
                }
                return b.getFileName().toString().compareTo(a.getFileName().toString());
            }

            private int rank(Path p) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.contains("youer")) {
                    return 0;
                }
                if (n.contains("neoforge") || n.contains("forge")) {
                    return 1;
                }
                if (n.contains("paper") || n.contains("purpur") || n.contains("spigot")) {
                    return 2;
                }
                return 3;
            }
        });
        return jars.get(0);
    }

    /** 查找 NeoForge 官方安装器生成的启动参数文件。 */
    public static Resolved findNeoForge(Path gameDir) throws IOException {
        Path base = gameDir.resolve("libraries").resolve("net").resolve("neoforged").resolve("neoforge");
        if (!Files.isDirectory(base)) {
            return null;
        }
        String argsName = Util.isWindows() ? "win_args.txt" : "unix_args.txt";
        List<Path> candidates = new ArrayList<Path>();
        Stream<Path> stream = Files.list(base);
        try {
            java.util.Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path versionDir = it.next();
                Path args = versionDir.resolve(argsName);
                if (Files.isRegularFile(args)) {
                    candidates.add(args);
                }
            }
        } finally {
            stream.close();
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // 版本号按数值比较，避免 26.3.0.9 排在 26.3.0.101 后面
        java.util.Collections.sort(candidates, new Comparator<Path>() {
            @Override
            public int compare(Path a, Path b) {
                return compareVersions(b.getParent().getFileName().toString(),
                        a.getParent().getFileName().toString());
            }
        });
        Path args = candidates.get(0);
        Path rel = gameDir.relativize(args);
        return Resolved.neoForge(rel, "NeoForge " + args.getParent().getFileName());
    }

    /** 安装器产物里同时有 win_args.txt 和 unix_args.txt，跨平台切换时也能直接用。 */
    public static void ensureCrossArgs(Path gameDir) {
        String[][] map = {{"win_args.txt", "unix_args.txt"}, {"unix_args.txt", "win_args.txt"}};
        Path base = gameDir.resolve("libraries").resolve("net").resolve("neoforged").resolve("neoforge");
        if (!Files.isDirectory(base)) {
            return;
        }
        try {
            Stream<Path> stream = Files.list(base);
            try {
                java.util.Iterator<Path> it = stream.iterator();
                while (it.hasNext()) {
                    Path dir = it.next();
                    for (String[] pair : map) {
                        Path src = dir.resolve(pair[0]);
                        Path dst = dir.resolve(pair[1]);
                        if (Files.isRegularFile(src) && !Files.exists(dst)) {
                            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            } finally {
                stream.close();
            }
        } catch (IOException ignored) {
            // 复制失败不影响主流程，运行时再报错
        }
    }

    // ------------------------------------------------------------ NeoForge 安装

    /** 下载安装器 → 运行安装器 → 缺口依赖走镜像补齐，最多三轮。 */
    public static boolean installNeoForge(Path root, Config cfg) throws IOException {
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
        log("  目标版本: NeoForge " + version);

        String fileName = "neoforge-" + version + "-installer.jar";
        Path installer = serverDir.resolve(fileName);
        if (!Files.isRegularFile(installer) || Files.size(installer) == 0) {
            boolean ok = false;
            String artifactPath = "net/neoforged/neoforge/" + version + "/" + fileName;
            for (String base : installerSources(mirror)) {
                String url = base + artifactPath;
                try {
                    log("  正在下载安装器: " + url);
                    Util.download(url, installer, null);
                    ok = Files.size(installer) > 0;
                    if (ok) {
                        break;
                    }
                } catch (IOException e) {
                    log("  下载失败: " + e.getMessage());
                }
            }
            if (!ok) {
                log("  [错误] 安装器下载失败，请检查网络，或在 travelmc.properties 里换 neoforge-mirror");
                return false;
            }
        }

        Path installLog = serverDir.resolve("neoforge-install.log");
        for (int attempt = 1; attempt <= 3; attempt++) {
            log("  正在运行 NeoForge 安装器（第 " + attempt + "/3 次），日志: server/neoforge-install.log");
            int code = runInstaller(java, installer, gameDir, installLog);
            ensureCrossArgs(gameDir);
            if (findNeoForge(gameDir) != null) {
                log("  NeoForge " + version + " 安装完成");
                return true;
            }
            int fetched = prefetchMissing(gameDir, installLog, mirror);
            log("  安装器退出码 " + code + "，本轮从镜像补齐依赖 " + fetched + " 个");
            if (fetched == 0 && code != 0 && attempt > 1) {
                break;
            }
        }
        log("  [错误] NeoForge 安装没有完成，安装器日志最后几行:");
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
            log("  无法启动安装器: " + e.getMessage());
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
        for (String url : urls) {
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
            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Util.download(mirror + rel, target, null);
                    fetched++;
                    last = null;
                    break;
                } catch (IOException e) {
                    last = e;
                    Util.sleep(1500);
                }
            }
            if (last != null) {
                log("  依赖补齐失败: " + rel + " (" + last.getMessage() + ")");
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

    /** https://maven.neoforged.net/releases/net/... → net/... */
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
    public static String latestNeoForgeVersion(String mirror) {
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
        log("  版本列表获取失败，使用默认 " + FALLBACK_NEO_VERSION);
        return FALLBACK_NEO_VERSION;
    }

    private static String latestFrom(String url) {
        try {
            String xml = Util.httpText(url);
            String best = null;
            Matcher m = Pattern.compile("<version>(" + Pattern.quote(Util.MC_VERSION) + "\\.\\d+)</version>").matcher(xml);
            while (m.find()) {
                String v = m.group(1);
                if (best == null || compareVersions(v, best) > 0) {
                    best = v;
                }
            }
            return best;
        } catch (IOException e) {
            log("  镜像不可用（" + e.getMessage() + "），换下一个源");
            return null;
        }
    }

    /** 按点分段做数值比较，a>b 返回正数。 */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-]");
        String[] pb = b.split("[.\\-]");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? toInt(pa[i]) : 0;
            int vb = i < pb.length ? toInt(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void printTail(Path file, int lines) {
        try {
            List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - lines);
            for (int i = from; i < all.size(); i++) {
                String line = all.get(i);
                if (!line.contains("at java.") && !line.contains("at net.minecraftforge")) {
                    log("    " + line);
                }
            }
        } catch (IOException ignored) {
            // 日志都读不到就不再报错
        }
    }

    // ------------------------------------------------------------ Paper

    /** 从官方 Downloads API 获取 Paper 26.3 并校验 SHA-256 后下载。 */
    public static java.util.Optional<Resolved> downloadPaper(Path serverDir) throws IOException {
        String json;
        try {
            json = Util.httpText(PAPER_API);
        } catch (IOException e) {
            log("  官方 API 不可达: " + e.getMessage());
            return java.util.Optional.empty();
        }

        int marker = json.indexOf("\"server:default\"");
        if (marker < 0) {
            marker = 0;
        }
        String slice = json.substring(marker, Math.min(json.length(), marker + 2000));

        Matcher urlMatcher = Pattern.compile("\"url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"").matcher(slice);
        if (!urlMatcher.find()) {
            log("  未能从 API 响应中解析出下载地址");
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
                log("  已存在且校验通过: " + fileName);
                return java.util.Optional.of(Resolved.jar(target));
            }
        }

        log("  正在下载 " + fileName);
        Util.Progress progress = new Util.Progress() {
            @Override
            public void update(long done, long total) {
                if (total > 0) {
                    System.out.print(String.format(Locale.ROOT, "\r      %5.1f%%   %s / %s   ",
                            done * 100.0 / total, Util.human(done), Util.human(total)));
                } else {
                    System.out.print("\r      " + Util.human(done) + "   ");
                }
                System.out.flush();
            }
        };
        long size = Util.download(url, target, progress);
        System.out.println();
        log("  下载完成: " + Util.human(size));

        if (sha256 != null) {
            String actual = Util.sha256(target);
            if (!actual.equalsIgnoreCase(sha256)) {
                Files.deleteIfExists(target);
                throw new IOException("SHA-256 校验失败，文件已删除，请重试");
            }
            log("  SHA-256 校验通过");
        }
        return java.util.Optional.of(Resolved.jar(target));
    }

    static void log(String message) {
        System.out.println(message);
    }
}
