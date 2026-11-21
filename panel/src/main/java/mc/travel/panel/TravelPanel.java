package mc.travel.panel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Travel Panel：127.0.0.1:2000 的本地控制面板 + /txp /txm /txc 三个管理命令。
 * 面板：在线/离线玩家列表（带头像）、实时日志、命令输入、服务器图标上传（必须 64×64 PNG）。
 */
public class TravelPanel extends JavaPlugin implements Listener {

    private static final String MODRINTH = "https://api.modrinth.com/v2";

    private HttpServer http;
    private ExecutorService pool;
    private Path root;
    private Path avatars;
    private int port = 2000;
    private String bind = "127.0.0.1";
    private boolean openBrowser = true;
    private long startedAt = System.currentTimeMillis();

    private final Map<String, Rec> players = new ConcurrentHashMap<String, Rec>();

    /** 面板进度条用的下载状态：/txm、/txc 下载时页面实时显示百分比。 */
    static final class Dl {
        volatile boolean active;
        volatile String name = "";
        volatile String kind = "";
        volatile long done;
        volatile long total;
        volatile String result = "";
    }

    private final Dl dl = new Dl();

    private static final class Rec {
        String name;
        long last;
        boolean online;
    }

    @Override
    public void onEnable() {
        root = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        avatars = getDataFolder().toPath().resolve("avatars");
        try {
            Files.createDirectories(avatars);
        } catch (IOException ignored) {
        }
        saveDefaultConfig();
        port = getConfig().getInt("panel-port", 2000);
        bind = getConfig().getString("panel-bind", "127.0.0.1");
        openBrowser = getConfig().getBoolean("open-browser", true);

        loadPlayers();
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            touch(p.getName(), p.getUniqueId().toString(), true);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        startPanel();
        if (openBrowser) {
            Bukkit.getScheduler().runTaskLater(this, this::openPanel, 40L);
        }
        getLogger().info("Travel Panel 已启动：http://" + bind + ":" + port + "/");
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
        if (pool != null) {
            pool.shutdownNow();
        }
        savePlayers();
    }

    // ---------------------------------------------------------------- 面板

    private void startPanel() {
        try {
            http = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException e) {
            getLogger().warning("面板端口 " + port + " 绑定失败：" + e.getMessage()
                    + "（改 plugins/TravelPanel/config.yml 的 panel-port）");
            return;
        }
        pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "TravelPanel-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(pool);
        http.createContext("/", this::dispatch);
        http.start();
    }

    private void openPanel() {
        String url = "http://" + bind + ":" + port + "/";
        try {
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("headless");
            }
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                throw new IllegalStateException("browse unsupported");
            }
            desktop.browse(URI.create(url));
            getLogger().info("已自动打开面板：" + url);
        } catch (Throwable t) {
            getLogger().info("面板地址：" + url + "（本机无法自动打开浏览器，请手动访问）");
        }
    }

    private void dispatch(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.equals("/index.html")) {
                send(ex, 200, "text/html; charset=utf-8", html());
            } else if (path.startsWith("/api/avatar/")) {
                avatar(ex, path.substring("/api/avatar/".length()));
            } else if (path.equals("/api/state")) {
                sendJson(ex, stateJson());
            } else if (path.equals("/api/dl")) {
                sendJson(ex, dlJson());
            } else if (path.equals("/api/log")) {
                log(ex);
            } else if (path.equals("/api/icon")) {
                if ("POST".equals(ex.getRequestMethod())) {
                    uploadIcon(ex);
                } else {
                    serveIcon(ex);
                }
            } else if (path.equals("/api/cmd") && "POST".equals(ex.getRequestMethod())) {
                runCmd(ex);
            } else {
                send(ex, 404, "text/plain; charset=utf-8", "not found");
            }
        } catch (Throwable t) {
            getLogger().warning("面板请求出错 " + path + ": " + t);
            try {
                sendJson(ex, 500, "{\"ok\":false,\"error\":" + q(String.valueOf(t)) + "}");
            } catch (IOException ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private String dlJson() {
        Dl d = dl;
        long total = d.total;
        double pct = total > 0 ? Math.min(100.0, d.done * 100.0 / total) : (d.active ? 0 : 100);
        return "{\"ok\":true,\"active\":" + d.active + ",\"name\":" + q(d.name)
                + ",\"kind\":" + q(d.kind) + ",\"done\":" + d.done + ",\"total\":" + total
                + ",\"percent\":" + (Math.round(pct * 10) / 10.0) + ",\"result\":" + q(d.result) + "}";
    }

    private String html() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("web/index.html");
        if (in == null) {
            return "<h1>Travel Panel</h1><p>web/index.html 缺失</p>";
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private String stateJson() {
        StringBuilder sb = new StringBuilder();
        double[] tps = Bukkit.getTPS();
        sb.append("{\"ok\":true");
        sb.append(",\"motd\":").append(q(Bukkit.getMotd()));
        sb.append(",\"version\":").append(q(Bukkit.getVersion()));
        sb.append(",\"online\":").append(Bukkit.getOnlinePlayers().size());
        sb.append(",\"max\":").append(Bukkit.getMaxPlayers());
        sb.append(",\"tps\":").append(Math.round(tps[0] * 100) / 100.0);
        sb.append(",\"uptime\":").append((System.currentTimeMillis() - startedAt) / 1000);
        sb.append(",\"icon\":").append(Files.exists(root.resolve("server-icon.png")));
        sb.append(",\"panelPort\":").append(port);
        sb.append(",\"players\":[");
        boolean first = true;
        for (Map.Entry<String, Rec> e : players.entrySet()) {
            Rec r = e.getValue();
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"name\":").append(q(r.name))
                    .append(",\"uuid\":").append(q(e.getKey()))
                    .append(",\"online\":").append(r.online)
                    .append(",\"last\":").append(r.last).append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private void log(HttpExchange ex) throws IOException {
        String qs = ex.getRequestURI().getQuery();
        long from = 0;
        if (qs != null) {
            for (String part : qs.split("&")) {
                if (part.startsWith("from=")) {
                    try {
                        from = Long.parseLong(part.substring(5));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        File file = root.resolve("logs").resolve("latest.log").toFile();
        if (!file.isFile()) {
            sendJson(ex, "{\"lines\":[],\"next\":0,\"missing\":true}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"lines\":[");
        long next = from;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long len = raf.length();
            if (from > len || from < 0) {
                from = Math.max(0, len - 8192);
            }
            raf.seek(from);
            byte[] buf = new byte[16384];
            int n = raf.read(buf);
            if (n <= 0) {
                sendJson(ex, "{\"lines\":[],\"next\":" + from + "}");
                return;
            }
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            int cut = text.lastIndexOf('\n');
            if (cut < 0) {
                cut = text.length() - 1;
            }
            String block = text.substring(0, cut + 1);
            next = from + block.getBytes(StandardCharsets.UTF_8).length;
            boolean first = true;
            for (String line : block.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(q(strip(line)));
            }
        } finally {
            if (raf != null) {
                raf.close();
            }
        }
        sb.append("],\"next\":").append(next).append('}');
        sendJson(ex, sb.toString());
    }

    private static String strip(String line) {
        // 去掉 Paper 的 ANSI 颜色转义，面板里用自己的一套颜色
        return line.replaceAll("\u001B\\[[0-9;]*m", "").replace("\r", "");
    }

    private void runCmd(HttpExchange ex) throws IOException {
        String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
        String cmd = "";
        for (String part : body.split("&")) {
            if (part.startsWith("cmd=")) {
                cmd = java.net.URLDecoder.decode(part.substring(4), "UTF-8");
            }
        }
        if (cmd.trim().isEmpty()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"空命令\"}");
            return;
        }
        final String c = cmd.trim();
        Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c));
        getLogger().info("[面板] 执行命令: " + c);
        sendJson(ex, "{\"ok\":true,\"cmd\":" + q(c) + "}");
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    /** 浏览器上传的是 multipart/form-data，真正图片从那串 boundary 里抠出来；直接贴原始 PNG 也认。 */
    private static byte[] extractPng(byte[] raw) {
        int start = 0;
        if (!(raw.length >= 8 && raw[0] == PNG_MAGIC[0] && raw[1] == 'P' && raw[2] == 'N' && raw[3] == 'G')) {
            start = indexOf(raw, PNG_MAGIC);
            if (start < 0) {
                return null;
            }
        }
        int end = -1;
        for (int i = start + 8; i + 4 <= raw.length; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '-' && raw[i + 3] == '-') {
                end = i;
                break;
            }
        }
        if (end < 0) {
            end = raw.length;
        }
        byte[] png = new byte[end - start];
        System.arraycopy(raw, start, png, 0, png.length);
        return png;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private void uploadIcon(HttpExchange ex) throws IOException {
        byte[] raw = readAll(ex.getRequestBody());
        if (raw.length == 0 || raw.length > 4 * 1024 * 1024) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"文件为空或超过 4 MB\"}");
            return;
        }
        byte[] data = extractPng(raw);
        if (data == null || data.length < 8) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"不是 PNG 文件（需要 .png，64×64）\"}");
            return;
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new java.io.ByteArrayInputStream(data));
        } catch (IOException e) {
            img = null;
        }
        if (img == null) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"PNG 解析失败\"}");
            return;
        }
        if (img.getWidth() != 64 || img.getHeight() != 64) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"图片必须是 64×64，当前是 "
                    + img.getWidth() + "×" + img.getHeight() + "\"}");
            return;
        }
        Files.write(root.resolve("server-icon.png"), data);
        getLogger().info("服务器图标已更新：server-icon.png（64×64，重启后客户端生效）");
        Bukkit.getScheduler().runTask(this, () ->
                Bukkit.broadcastMessage("§a[Travel Panel] §f服务器图标已更新为 64×64 PNG，重启服务器后生效"));
        sendJson(ex, "{\"ok\":true,\"size\":\"64x64\"}");
    }

    private void serveIcon(HttpExchange ex) throws IOException {
        Path p = root.resolve("server-icon.png");
        if (Files.exists(p)) {
            exch(ex, 200, "image/png", Files.readAllBytes(p));
        } else {
            send(ex, 404, "text/plain; charset=utf-8", "no icon");
        }
    }

    private void avatar(HttpExchange ex, String name) throws IOException {
        name = java.net.URLDecoder.decode(name, "UTF-8").replace(".png", "");
        if (name.isEmpty()) {
            send(ex, 404, "text/plain; charset=utf-8", "no name");
            return;
        }
        Path cache = avatars.resolve(safe(name) + ".png");
        if (Files.exists(cache)) {
            exch(ex, 200, "image/png", Files.readAllBytes(cache));
            return;
        }
        byte[] remote = fetchAvatar(name);
        byte[] out = remote != null ? remote : fallbackAvatar(name);
        try {
            Files.write(cache, out);
        } catch (IOException ignored) {
        }
        exch(ex, 200, "image/png", out);
    }

    private byte[] fetchAvatar(String name) {
        String[] urls = {
                "https://mc-heads.net/avatar/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/64",
                "https://minotar.net/helm/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/64.png"
        };
        for (String u : urls) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "TravelPanel/1.0");
                if (c.getResponseCode() != 200) {
                    continue;
                }
                byte[] data = readAll(c.getInputStream());
                if (data.length > 100) {
                    return data;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 离线/被墙时的兜底头像：按名字哈希生成 8×8 像素的方块脸，放大到 64×64。 */
    private byte[] fallbackAvatar(String name) throws IOException {
        int hash = name.hashCode();
        int[][] palette = {
                {198, 134, 100}, {172, 106, 78}, {224, 166, 128}, {140, 84, 60}
        };
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int by = 0; by < 8; by++) {
            for (int bx = 0; bx < 8; bx++) {
                int v = (hash >>> ((bx + by) % 16)) & 0xF;
                int[] base = palette[v % palette.length];
                int shade = 8 - by / 3;
                int rgb = clamp(base[0] + v + shade) << 16
                        | clamp(base[1] + v + shade) << 8
                        | clamp(base[2] + v + shade);
                for (int y = by * 8; y < by * 8 + 8; y++) {
                    for (int x = bx * 8; x < bx * 8 + 8; x++) {
                        img.setRGB(x, y, rgb);
                    }
                }
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ---------------------------------------------------------------- 命令

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("txp")) {
            return txp(sender, args);
        }
        if (name.equals("txm")) {
            return download(sender, args, true);
        }
        if (name.equals("txc")) {
            return download(sender, args, false);
        }
        return false;
    }

    private boolean txp(CommandSender sender, String[] args) {
        Path core = root.resolve("travelmc.properties");
        Path server = root.resolve("server.properties");
        if (!Files.exists(core) && !Files.exists(server)) {
            sender.sendMessage("§c找不到 travelmc.properties / server.properties（它们由 TravelMC-Core 生成）");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e用法：/txp <配置名> <t=开 / f=关 / 数字>");
            sender.sendMessage("§7（server.properties 的项写 server.properties，核心项写 travelmc.properties）");
            listKeys(sender, server, "server.properties");
            listKeys(sender, core, "travelmc.properties");
            return true;
        }
        String key = args[0];
        String inServer = readKey(server, key);
        String inCore = readKey(core, key);
        if (args.length == 1) {
            if (inServer == null && inCore == null) {
                sender.sendMessage("§c没有这个配置项：" + key);
            } else if (inServer != null) {
                sender.sendMessage("§a" + key + " §7= §f" + inServer + " §8（server.properties）");
            } else {
                sender.sendMessage("§a" + key + " §7= §f" + inCore + " §8（travelmc.properties）");
            }
            return true;
        }
        String raw = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        String value;
        if (raw.equalsIgnoreCase("t") || raw.equalsIgnoreCase("true") || raw.equals("开")) {
            value = "true";
        } else if (raw.equalsIgnoreCase("f") || raw.equalsIgnoreCase("false") || raw.equals("关")) {
            value = "false";
        } else {
            value = raw;
        }
        // 优先写真正生效的那个文件：server.properties 有的项写那里，否则写核心配置
        Path file = inServer != null ? server : core;
        boolean known = inServer != null || inCore != null;
        try {
            writeKey(file, key, value);
        } catch (IOException e) {
            sender.sendMessage("§c写入失败：" + e.getMessage());
            return true;
        }
        String fileName = file.getFileName().toString();
        if (!known) {
            sender.sendMessage("§e[警告] §f" + key + " 原来不在配置里，已追加到 " + fileName
                    + "（拼写不对的话不会生效）");
        }
        Bukkit.broadcastMessage("§e[Travel Panel] §f配置已更新：§b" + key + "§f = §a" + value
                + "§7（" + fileName + "），3 秒后自动重启服务器");
        getLogger().info("[面板] /txp " + key + " = " + value + " → " + fileName + "，准备重启");
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.shutdown(), 60L);
        return true;
    }

    private void listKeys(CommandSender sender, Path file, String label) {
        if (!Files.exists(file)) {
            return;
        }
        sender.sendMessage("§8# " + label);
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#") && t.contains("=")) {
                    sender.sendMessage("§7  " + t);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String readKey(Path file, String key) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("#") || !t.contains("=")) {
                    continue;
                }
                String k = t.substring(0, t.indexOf('=')).trim();
                if (k.equals(key)) {
                    return t.substring(t.indexOf('=') + 1).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static void writeKey(Path file, String key, String value) throws IOException {
        java.util.List<String> lines = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new java.util.ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("#") || !t.contains("=")) {
                continue;
            }
            String k = t.substring(0, t.indexOf('=')).trim();
            if (k.equals(key)) {
                lines.set(i, key + "=" + value);
                Files.write(file, lines, StandardCharsets.UTF_8);
                return;
            }
        }
        lines.add(key + "=" + value);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private boolean download(CommandSender sender, String[] args, final boolean mod) {
        if (args.length == 0) {
            sender.sendMessage("§e用法：" + (mod ? "/txm <模组名>" : "/txc <插件名>"));
            return true;
        }
        final String query = String.join(" ", args).trim();
        final CommandSender target = sender;
        sender.sendMessage("§7正在 Modrinth 搜索 " + (mod ? "模组" : "插件") + "：§f" + query + " §7…");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String msg = fetchFromModrinth(query, mod);
                Bukkit.getScheduler().runTask(this, () -> target.sendMessage(msg));
            } catch (Exception e) {
                String err = e.getMessage() == null ? e.toString() : e.getMessage();
                dl.active = false;
                dl.result = err;
                Bukkit.getScheduler().runTask(this, () -> target.sendMessage("§c下载失败：" + err));
                getLogger().warning("Modrinth 下载失败: " + e);
            }
        });
        return true;
    }

    /** 搜索 + 取最新版本主文件 + 下载到 mods/ 或 plugins/。返回给玩家的提示。 */
    private String fetchFromModrinth(String query, boolean mod) throws IOException {
        // Modrinth 的 facets 现在会被忽略（实测带 project_type/categories 反而搜不到 spark），
        // 所以先不带过滤搜索，用名字打分排序，再逐个用 loaders 过滤版本，
        // 第一个真正有可用 jar 的候选才算命中。
        String searchUrl = MODRINTH + "/search?limit=10&query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        Object root1 = Json.parse(httpGet(searchUrl));
        Object hits = Json.at(root1, "hits");
        if (!(hits instanceof List) || ((List<?>) hits).isEmpty()) {
            return "§cModrinth 上没有找到“" + query + "”。26.3 是很新的版本，作者可能还没上传，"
                    + "也可以手动把 jar 放进 " + (mod ? "mods/" : "plugins/");
        }
        final String q = query.toLowerCase().replace(" ", "");
        List<?> list = (List<?>) hits;
        List<Object[]> ranked = new java.util.ArrayList<Object[]>();
        for (Object h : list) {
            String s1 = Json.str(h, "slug", "").toLowerCase();
            String t1 = Json.str(h, "title", "").toLowerCase().replace(" ", "");
            int score;
            if (s1.equals(query.toLowerCase()) || t1.equals(q)) {
                score = 100;
            } else if (s1.replace(" ", "").equals(q)) {
                score = 85;
            } else if (s1.startsWith(q) || t1.startsWith(q)) {
                score = 70;
            } else if (s1.contains(q) || t1.contains(q)) {
                score = 55;
            } else {
                score = 0;
            }
            if (score > 0) {
                ranked.add(new Object[] {h, Integer.valueOf(score)});
            }
        }
        Collections.sort(ranked, (a, b) -> ((Integer) b[1]).compareTo((Integer) a[1]));
        if (ranked.isEmpty()) {
            return "§cModrinth 上没有找到名字接近“" + query + "”的项目";
        }
        String loaderHint = mod ? "NeoForge" : "Paper/Bukkit";
        String triedNames = "";
        int tries = Math.min(4, ranked.size());
        for (int i = 0; i < tries; i++) {
            Object h = ranked.get(i)[0];
            String slug = Json.str(h, "slug", null);
            String title = Json.str(h, "title", slug);
            if (slug == null) {
                continue;
            }
            String versionsUrl = MODRINTH + "/project/" + URLEncoder.encode(slug, StandardCharsets.UTF_8)
                    + "/version?loaders=" + URLEncoder.encode(mod ? "[\"neoforge\"]"
                    : "[\"paper\",\"bukkit\",\"spigot\",\"purpur\",\"folia\"]", StandardCharsets.UTF_8);
            Object versions;
            try {
                versions = Json.parse(httpGet(versionsUrl));
            } catch (IOException e) {
                continue;
            }
            if (!(versions instanceof List) || ((List<?>) versions).isEmpty()) {
                triedNames += (triedNames.isEmpty() ? "" : "、") + title;
                continue;
            }
            for (Object v : (List<?>) versions) {
                Object files = Json.at(v, "files");
                if (!(files instanceof List)) {
                    continue;
                }
                for (Object f : (List<?>) files) {
                    String url = Json.str(f, "url", null);
                    String filename = Json.str(f, "filename", null);
                    boolean primary = Boolean.TRUE.equals(Json.at(f, "primary"));
                    if (url == null || filename == null || !filename.toLowerCase().endsWith(".jar")) {
                        continue;
                    }
                    if (!primary && ((List<?>) files).size() > 1) {
                        continue;
                    }
                    Path dir = root.resolve(mod ? "mods" : "plugins");
                    Files.createDirectories(dir);
                    Path target = dir.resolve(filename);
                    dl.kind = mod ? "模组" : "插件";
                    dl.active = true;
                    dl.name = filename;
                    dl.total = 0;
                    dl.done = 0;
                    dl.result = "";
                    try {
                        downloadFile(url, target);
                    } finally {
                        dl.active = false;
                    }
                    dl.result = "已完成 " + filename;
                    long size = Files.size(target);
                    String versionName = Json.str(v, "version_number", "");
                    return "§a已下载 §f" + title + " §7" + versionName + " §8→ §f"
                            + (mod ? "mods/" : "plugins/") + filename
                            + " §8(" + (size / 1024) + " KB)§7，重启服务器后生效";
                }
            }
            triedNames += (triedNames.isEmpty() ? "" : "、") + title;
        }
        return "§c找到的项目都没有适用于 " + loaderHint + " 的 jar（试过：" + triedNames
                + "）。26.3 太新，作者可能还没适配，可手动放 jar 到 " + (mod ? "mods/" : "plugins/");
    }

    private void downloadFile(String url, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "TravelPanel/1.0");
        if (c.getResponseCode() != 200) {
            throw new IOException("HTTP " + c.getResponseCode() + " 下载 " + url);
        }
        dl.name = target.getFileName().toString();
        dl.done = 0;
        dl.total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream();
             OutputStream out = Files.newOutputStream(tmp,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                dl.done += n;
            }
        }
        if (!safeMove(tmp, target)) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 删不掉没关系
            }
            throw new IOException("目标文件被占用，无法写入 " + target.getFileName()
                    + "（Windows 上常见于服务器正在使用该文件；关掉服务器或换个版本名再试）");
        }
        long expect = c.getContentLengthLong();
        if (expect > 0 && Files.size(target) != expect) {
            throw new IOException("文件不完整：" + Files.size(target) + " / " + expect);
        }
    }

    /** Windows 上目标被正在运行的服务器锁住时，先把它改名腾位，再搬新文件。 */
    private static boolean safeMove(Path tmp, Path target) {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException first) {
            Path old = null;
            try {
                if (Files.exists(target)) {
                    old = target.resolveSibling(target.getFileName().toString().replace(".jar", "")
                            + ".old-" + System.currentTimeMillis() + ".jar");
                    Files.move(target, old);
                }
                Files.move(tmp, target);
                if (old != null) {
                    try {
                        Files.deleteIfExists(old);
                    } catch (IOException ignored) {
                        // 旧 jar 被锁删不掉，留着也不影响新 jar
                    }
                }
                return true;
            } catch (IOException second) {
                getLoggerStatic().warning("写入 " + target.getFileName() + " 失败：" + second.getMessage());
                return false;
            }
        }
    }

    private static java.util.logging.Logger getLoggerStatic() {
        return java.util.logging.Logger.getLogger("TravelPanel");
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "TravelMC-TravelPanel/1.0 (github.com/78879100/travel-mc-core)");
        c.setRequestProperty("Accept", "application/json");
        if (c.getResponseCode() != 200) {
            throw new IOException("Modrinth HTTP " + c.getResponseCode());
        }
        return new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 玩家

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        touch(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        touch(e.getPlayer().getName(), e.getPlayer().getUniqueId().toString(), false);
    }

    private void touch(String name, String uuid, boolean online) {
        Rec r = players.get(uuid);
        if (r == null) {
            r = new Rec();
            r.name = name;
            players.put(uuid, r);
        }
        r.name = name;
        r.online = online;
        r.last = System.currentTimeMillis() / 1000;
        savePlayers();
    }

    private void loadPlayers() {
        Path f = getDataFolder().toPath().resolve("players.tsv");
        if (!Files.exists(f)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t");
                if (p.length < 3) {
                    continue;
                }
                Rec r = new Rec();
                r.name = p[1];
                try {
                    r.last = Long.parseLong(p[2]);
                } catch (NumberFormatException ignored) {
                }
                players.put(p[0], r);
            }
        } catch (IOException ignored) {
        }
    }

    private void savePlayers() {
        try {
            Path f = getDataFolder().toPath().resolve("players.tsv");
            List<String> lines = new ArrayList<String>();
            for (Map.Entry<String, Rec> e : players.entrySet()) {
                lines.add(e.getKey() + "\t" + e.getValue().name + "\t" + e.getValue().last);
            }
            Files.write(f, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- 工具

    private static String safe(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '_' || c == '-' ? c : '_');
        }
        return sb.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                bos.write(b, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void sendJson(HttpExchange ex, String json) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", json);
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        send(ex, code, "application/json; charset=utf-8", json);
    }

    private static void exch(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    /** JSON 字符串转义。 */
    static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
