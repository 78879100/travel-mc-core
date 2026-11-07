package mc.travel.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
