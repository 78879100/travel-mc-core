package mc.travel.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 运行环境、文件读写与网络下载等通用工具。
 */
public final class Util {

    public static final String CORE_NAME = "Travel MC Core";
    public static final String CORE_VERSION = "1.0.1";
    public static final String MC_VERSION = "26.3";
    /** 26.3 服务端要求 Java 25 及以上。 */
    public static final int MIN_JAVA = 25;

    public interface Progress {
        void update(long done, long total);
    }

    private Util() {
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean isMac() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("mac");
    }

    public static int javaVersion() {
        String spec = System.getProperty("java.specification.version", "0");
        if (spec.startsWith("1.")) {
            spec = spec.substring(2);
        }
        int dot = spec.indexOf('.');
        if (dot > 0) {
            spec = spec.substring(0, dot);
        }
        try {
            return Integer.parseInt(spec.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String readResource(String name) throws IOException {
        InputStream in = Util.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException("内置资源缺失: " + name);
        }
        try {
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return sb.toString();
        } finally {
            in.close();
        }
    }

    public static void writeText(Path file, String content, boolean crlf) throws IOException {
        String text = crlf ? content.replace("\r\n", "\n").replace("\n", "\r\n") : content;
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, text.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream in = Files.newInputStream(file);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            } finally {
                in.close();
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static HttpURLConnection open(String urlText) throws IOException {
        URL url = new URL(urlText);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", CORE_NAME + "/" + CORE_VERSION);
        conn.setRequestProperty("Accept", "*/*");
        return conn;
    }

    public static String httpText(String urlText) throws IOException {
        HttpURLConnection conn = open(urlText);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + "  " + urlText);
            }
            InputStream in = conn.getInputStream();
            try {
                byte[] buf = new byte[8192];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = in.read(buf)) > 0) {
                    sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
                return sb.toString();
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    public static long download(String urlText, Path target, Progress progress) throws IOException {
        HttpURLConnection conn = open(urlText);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code + "  " + urlText);
            }
            long total = conn.getContentLengthLong();
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path part = target.resolveSibling(target.getFileName().toString() + ".part");
            long done = 0;
            InputStream in = conn.getInputStream();
            OutputStream out = Files.newOutputStream(part,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                byte[] buf = new byte[65536];
                int n;
                long lastReport = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (progress != null && (done - lastReport >= 512 * 1024 || done == total)) {
                        progress.update(done, total);
                        lastReport = done;
                    }
                }
            } finally {
                in.close();
                out.close();
            }
            try {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return done;
        } finally {
            conn.disconnect();
        }
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
