package mc.travel.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * API Key 存储：写到插件目录下的 keys.properties，文件权限尽量收窄到仅服主可读。
 * 任何对外输出都只显示掩码（sk-****1234），不会把明文写进日志。
 */
public final class KeyStore {

    private final Path file;
    private final Properties props = new Properties();

    public KeyStore(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            java.io.InputStream in = Files.newInputStream(file);
            try {
                props.load(in);
            } finally {
                in.close();
            }
        }
    }

    public String get(String id) {
        String v = props.getProperty(id);
        return v == null ? "" : v.trim();
    }

    public void set(String id, String key) throws IOException {
        props.setProperty(id, key);
        save();
    }

    public boolean has(String id) {
        return !get(id).isEmpty();
    }

    private void save() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Travel AI 的 API Key（明文，请勿外传）\n");
        Set<String> ids = new HashSet<String>();
        for (String name : props.stringPropertyNames()) {
            ids.add(name);
        }
        for (String id : ids) {
            sb.append(id).append('=').append(props.getProperty(id)).append('\n');
        }
        Files.createDirectories(file.getParent());
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 不支持 POSIX 权限，忽略
        }
    }

    /** 掩码：前 4 位 + **** + 后 4 位，长度也一并给出。 */
    public static String mask(String key) {
        if (key == null || key.isEmpty()) {
            return "（未设置）";
        }
        if (key.length() <= 8) {
            return "****（长度 " + key.length() + "）";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4) + "（长度 " + key.length() + "）";
    }
}
