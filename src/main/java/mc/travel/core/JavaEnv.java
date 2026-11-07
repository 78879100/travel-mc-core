package mc.travel.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Java 25 运行时定位与自动补齐。
 *
 * <p>Minecraft 26.3 的服务端必须运行在 Java 25 上。核心自身可以在低版本 Java 上启动，
 * 启动后按下面的顺序寻找可用的 Java 25：</p>
 * <ol>
 *   <li>travelmc.properties 里显式配置的 java；</li>
 *   <li>当前正在运行核心的 JVM；</li>
 *   <li>核心目录下 runtime/ 里已经自动下载过的 JDK；</li>
 *   <li>系统 JAVA_HOME；</li>
 *   <li>PATH 上的 java；</li>
 *   <li>都没有时，自动从 Adoptium 下载 Temurin 25 到 runtime/ 并解压。</li>
 * </ol>
 */
public final class JavaEnv {

    private static final String ADOPTIUM =
            "https://api.adoptium.net/v3/binary/latest/" + Util.MIN_JAVA + "/ga/";

    private JavaEnv() {
    }

    /** 返回可直接写进启动脚本的 java 可执行文件；返回 null 表示找不到也下不下来。 */
    public static String resolve(Path root, Config cfg) {
        String configured = cfg.get("java", "java");
        if (!"java".equalsIgnoreCase(configured)) {
            if (Files.isRegularFile(Paths.get(configured))) {
                int v = versionOf(configured);
                if (v >= Util.MIN_JAVA) {
                    return configured;
                }
                System.out.println("  [提示] 配置的 java 是 " + (v > 0 ? "Java " + v : "无法识别的版本")
                        + "，低于 Java " + Util.MIN_JAVA + "，继续寻找其它运行时");
            } else if (configured.contains("/") || configured.contains("\\")) {
                System.out.println("  [提示] 配置的 java 路径不存在: " + configured);
            } else {
                int v = versionOf(configured);
                if (v >= Util.MIN_JAVA) {
                    return configured;
                }
            }
        }

        String current = javaExe(Paths.get(System.getProperty("java.home", "")));
        if (Util.javaVersion() >= Util.MIN_JAVA && current != null) {
            return current;
        }

        String bundled = findBundled(root);
        if (bundled != null) {
            return bundled;
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            String exe = javaExe(Paths.get(javaHome));
            if (exe != null && versionOf(exe) >= Util.MIN_JAVA) {
                return exe;
            }
        }

        if (versionOf("java") >= Util.MIN_JAVA) {
            return "java";
        }

        if (!cfg.getBool("auto-java", true)) {
            printManualHint();
            return null;
        }
        return provision(root, cfg);
    }

    private static void printManualHint() {
        System.out.println("  [错误] 没有找到 Java " + Util.MIN_JAVA + "，而 auto-java 已被关闭。");
        System.out.println("         请安装 Temurin 25: https://adoptium.net/temurin/releases/?version=25");
        System.out.println("         然后在 travelmc.properties 里写 java=<绝对路径>");
    }

    /** 在 runtime/ 目录里找已经解压好的 JDK。 */
    private static String findBundled(Path root) {
        Path runtime = root.resolve("runtime");
        if (!Files.isDirectory(runtime)) {
            return null;
        }
        List<Path> candidates = new ArrayList<Path>();
        try {
            java.util.stream.Stream<Path> stream = Files.walk(runtime);
            try {
                java.util.Iterator<Path> it = stream.iterator();
                while (it.hasNext()) {
                    Path p = it.next();
                    String name = p.getFileName().toString();
                    if (Files.isRegularFile(p) && ("java".equals(name) || "java.exe".equals(name))) {
                        String parent = p.getParent() == null ? "" : p.getParent().getFileName().toString();
                        if ("bin".equals(parent)) {
                            candidates.add(p);
                        }
                    }
                }
            } finally {
                stream.close();
            }
        } catch (IOException ignored) {
            return null;
        }
        java.util.Collections.sort(candidates);
        for (Path p : candidates) {
            int v = versionOf(p.toString());
            if (v >= Util.MIN_JAVA) {
                return p.toString();
            }
        }
        return null;
    }

    /** 下载 Temurin 25 并解压到 runtime/，成功后写回配置。 */
    private static String provision(Path root, Config cfg) {
        String os = platformOs();
        String arch = platformArch();
        if (os == null || arch == null) {
            System.out.println("  [错误] 无法识别当前系统，请手动安装 Java " + Util.MIN_JAVA);
            return null;
        }
        final String ext = Util.isWindows() ? "zip" : "tar.gz";
        String url = ADOPTIUM + os + "/" + arch + "/jre/hotspot/normal/eclipse";

        System.out.println();
        System.out.println("  [Java] 没有找到 Java " + Util.MIN_JAVA + "，正在自动下载 Temurin "
                + Util.MIN_JAVA + "（" + os + "/" + arch + "）");
        System.out.println("         下载地址: " + url);

        Path runtime = root.resolve("runtime");
        Path archive = runtime.resolve("temurin-" + Util.MIN_JAVA + "." + ext);
        Path target = runtime.resolve("jdk-" + Util.MIN_JAVA);
        try {
            Files.createDirectories(runtime);
            Util.download(url, archive, new Util.Progress() {
                @Override
                public void update(long done, long total) {
                    if (total > 0) {
                        System.out.print(String.format(Locale.ROOT, "\r       %5.1f%%   %s / %s   ",
                                done * 100.0 / total, Util.human(done), Util.human(total)));
                    } else {
                        System.out.print("\r       " + Util.human(done) + "   ");
                    }
                    System.out.flush();
                }
            });
            System.out.println();
            System.out.println("  [Java] 下载完成，正在解压...");
            deleteRecursively(target);
            Files.createDirectories(target);
            if ("zip".equals(ext)) {
                unzip(archive, target);
            } else {
                untarGz(archive, target);
            }
            Files.deleteIfExists(archive);

            String exe = findBundled(root);
            if (exe == null) {
                System.out.println("  [错误] 解压完成但没有找到 bin/java，请手动安装 Java " + Util.MIN_JAVA);
                return null;
            }
            cfg.set("java", exe);
            cfg.save();
            System.out.println("  [Java] 就绪: " + exe);
            return exe;
        } catch (IOException e) {
            System.out.println();
            System.out.println("  [错误] 自动下载 Java 失败: " + e.getMessage());
            printManualHint();
            return null;
        }
    }

    private static String platformOs() {
        if (Util.isWindows()) {
            return "windows";
        }
        if (Util.isMac()) {
            return "mac";
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return "linux";
        }
        return null;
    }

    private static String platformArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        }
        if (arch.contains("64")) {
            return "x64";
        }
        if (arch.contains("86") || arch.contains("i386") || arch.contains("i686")) {
            return "x86";
        }
        return null;
    }

    private static String javaExe(Path home) {
        if (home == null) {
            return null;
        }
        Path exe = home.resolve("bin").resolve(Util.isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(exe)) {
            return exe.toString();
        }
        return null;
    }

    /** 执行 java -version，解析出版本号；失败返回 0。 */
    public static int versionOf(String java) {
        if (java == null || java.isEmpty()) {
            return 0;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(java, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String line = readFirstLine(p);
            p.waitFor();
            return parseVersion(line);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String readFirstLine(Process p) throws IOException {
        InputStream in = p.getInputStream();
        try {
            byte[] buf = new byte[512];
            int n = in.read(buf);
            if (n <= 0) {
                return "";
            }
            return new String(buf, 0, n, "UTF-8");
        } finally {
            in.close();
        }
    }

    static int parseVersion(String text) {
        if (text == null) {
            return 0;
        }
        int idx = text.indexOf("version \"");
        if (idx < 0) {
            return 0;
        }
        int start = idx + "version \"".length();
        int end = text.indexOf('"', start);
        if (end < 0) {
            return 0;
        }
        String v = text.substring(start, end);
        if (v.startsWith("1.")) {
            v = v.substring(2);
        }
        int dot = v.indexOf('.');
        if (dot > 0) {
            v = v.substring(0, dot);
        } else {
            int dash = v.indexOf('-');
            if (dash > 0) {
                v = v.substring(0, dash);
            }
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- 解压

    private static void unzip(Path zip, Path dest) throws IOException {
        ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip));
        try {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Path parent = out.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    OutputStream os = Files.newOutputStream(out);
                    try {
                        int n;
                        while ((n = zin.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    } finally {
                        os.close();
                    }
                    if (out.getFileName().toString().equals("java")
                            || out.getFileName().toString().equals("java.exe")) {
                        out.toFile().setExecutable(true);
                    }
                }
                zin.closeEntry();
            }
        } finally {
            zin.close();
        }
    }

    private static void untarGz(Path tarGz, Path dest) throws IOException {
        GZIPInputStream gin = new GZIPInputStream(Files.newInputStream(tarGz));
        try {
            byte[] header = new byte[512];
            byte[] buf = new byte[8192];
            while (true) {
                int read = readFully(gin, header, 0, 512);
                if (read < 512) {
                    break;
                }
                String name = new String(header, 0, 100, "UTF-8");
                int zero = name.indexOf('\0');
                if (zero >= 0) {
                    name = name.substring(0, zero);
                }
                name = name.trim();
                if (name.isEmpty()) {
                    break;
                }
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];
                Path out = dest.resolve(name);
                if (type == '5') {
                    Files.createDirectories(out);
                } else if (type == '0' || type == '7' || type == '\0') {
                    Path parent = out.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    OutputStream os = Files.newOutputStream(out);
                    try {
                        long left = size;
                        while (left > 0) {
                            int n = gin.read(buf, 0, (int) Math.min(buf.length, left));
                            if (n <= 0) {
                                break;
                            }
                            os.write(buf, 0, n);
                            left -= n;
                        }
                    } finally {
                        os.close();
                    }
                    if (out.getFileName().toString().equals("java")) {
                        out.toFile().setExecutable(true);
                    }
                } else if (type == '2') {
                    // 符号链接（部分 JDK 里的 bin/java 会指向真实文件），这里用普通文件占位，
                    // 解压结束后会统一校验 bin/java 是否存在。
                    continue;
                }
                skipFully(gin, (size + 511) / 512 * 512 - size);
            }
        } finally {
            gin.close();
        }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    private static long parseOctal(byte[] header, int off, int len) {
        String s = new String(header, off, len, java.nio.charset.StandardCharsets.US_ASCII)
                .replace("\0", " ").trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        long left = count;
        byte[] buf = new byte[512];
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n <= 0) {
                break;
            }
            left -= n;
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
