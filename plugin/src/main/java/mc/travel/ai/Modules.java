package mc.travel.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * AI 服务商模块的「安装」：插件里内置了各家的小模块 jar，
 * 服主在后台敲 /ai 名字 时把对应模块落到 modules/ 目录并注册，这一步就是用户说的「API 编译工具」安装。
 */
public final class Modules {

    /** 内置模块 id，与 src 里 modules/<id>.jar 对应。 */
    public static final List<String> BUILTIN = Arrays.asList("deepseek", "kimi", "doubao", "gpt");

    private final Path dir;

    public Modules(Path pluginDir) throws IOException {
        this.dir = pluginDir.resolve("modules");
        Files.createDirectories(dir);
    }

    public boolean isBuiltin(String id) {
        return id != null && BUILTIN.contains(id.toLowerCase());
    }

    /** 已安装过的模块（重装也能覆盖）。 */
    public List<String> installedOnDisk() {
        List<String> ids = new ArrayList<String>();
        try {
            if (Files.isDirectory(dir)) {
                java.util.stream.Stream<Path> stream = Files.list(dir);
                try {
                    stream.forEach(p -> {
                        String n = p.getFileName().toString();
                        if (n.endsWith(".jar")) {
                            ids.add(n.substring(0, n.length() - 4));
                        }
                    });
                } finally {
                    stream.close();
                }
            }
        } catch (IOException ignored) {
            // 读不到就当没有
        }
        return ids;
    }

    /**
     * 把内置模块解出来，并返回里面的服务商描述。
     */
    public Provider install(String id) throws IOException {
        String lower = id.toLowerCase();
        InputStream in = TravelAI.class.getClassLoader().getResourceAsStream("modules/" + lower + ".jar");
        if (in == null) {
            throw new IOException("插件内没有名为 " + id + " 的模块（可选："
                    + join(BUILTIN, " / ") + "）");
        }
        Path target = dir.resolve(lower + ".jar");
        try {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            in.close();
        }
        return readDescriptor(target);
    }

    private Provider readDescriptor(Path jar) throws IOException {
        ZipFile zip = new ZipFile(jar.toFile());
        try {
            ZipEntry entry = zip.getEntry("module.json");
            if (entry == null) {
                throw new IOException(jar.getFileName() + " 缺少 module.json");
            }
            InputStream in = zip.getInputStream(entry);
            String text;
            try {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
                text = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
            Map<String, Object> obj = Json.parseObject(text);
            Provider p = new Provider(
                    str(obj, "id", jar.getFileName().toString().replace(".jar", "")),
                    str(obj, "display", "AI"),
                    str(obj, "baseUrl", ""),
                    str(obj, "model", ""),
                    str(obj, "keyPage", ""));
            p.installed = true;
            return p;
        } finally {
            zip.close();
        }
    }

    /** 目录里已有模块时，用它刷新描述。 */
    public Provider load(Path jar) throws IOException {
        return readDescriptor(jar);
    }

    public Path jarOf(String id) {
        return dir.resolve(id.toLowerCase() + ".jar");
    }

    private static String str(Map<String, Object> obj, String key, String def) {
        String v = Json.str(obj, key);
        if (v == null || v.isEmpty()) {
            return def;
        }
        return v;
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(s);
        }
        return sb.toString();
    }
}
