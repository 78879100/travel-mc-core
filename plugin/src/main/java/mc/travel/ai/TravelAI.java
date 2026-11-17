package mc.travel.ai;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Travel AI：让玩家在聊天框用 /AI 问题 调 AI，服主在后台用 /ai 名字 装对应服务商模块。
 * 非 OP 只能拿到文字回答，OP 才能让 AI 代执行白名单内的服务器指令。
 */
public final class TravelAI extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private static final String PREFIX = "[Travel AI] ";
    private static final UUID CONSOLE_ID = UUID.nameUUIDFromBytes("console".getBytes());

    private Modules modules;
    private KeyStore keys;
    private final Map<String, Provider> providers = new LinkedHashMap<String, Provider>();
    private final Map<UUID, String> pendingKey = new HashMap<UUID, String>();
    private final Map<UUID, Long> lastAsk = new HashMap<UUID, Long>();
    private final Map<UUID, Pending> pendingRun = new HashMap<UUID, Pending>();
    private final Map<UUID, Deque<String>> history = new HashMap<UUID, Deque<String>>();

    private static final class Pending {
        final List<String> commands;
        final long expiresAt;

        Pending(List<String> commands, long expiresAt) {
            this.commands = commands;
            this.expiresAt = expiresAt;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            modules = new Modules(getDataFolder().toPath());
            keys = new KeyStore(getDataFolder().toPath().resolve("keys.properties"));
        } catch (IOException e) {
            getLogger().severe("初始化失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        for (String id : modules.installedOnDisk()) {
            try {
                Provider p = modules.load(modules.jarOf(id));
                applyOverrides(p);
                providers.put(p.id, p);
            } catch (IOException e) {
                getLogger().warning("读取模块 " + id + " 失败: " + e.getMessage());
            }
        }
        for (String id : Modules.BUILTIN) {
            if (providers.containsKey(id)) {
                continue;
            }
            Provider stub = new Provider(id, displayName(id), "", "", "");
            stub.installed = false;
            providers.put(id, stub);
        }
        getCommand("ai").setExecutor(this);
        getCommand("ai").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Travel AI 已加载：已安装模块 " + installed() + "，默认服务商 " + defaultProviderId());
    }

    private String displayName(String id) {
        switch (id.toLowerCase(Locale.ROOT)) {
            case "deepseek":
                return "DeepSeek";
            case "kimi":
                return "Kimi（月之暗面）";
            case "doubao":
                return "豆包（火山方舟）";
            case "gpt":
                return "GPT（OpenAI）";
            default:
                return id;
        }
    }

    private void applyOverrides(Provider p) {
        p.model = configModelOverride(p);
        String base = getConfig().getString("base-url." + p.id, "");
        if (base != null && !base.trim().isEmpty()) {
            p.baseUrl = base.trim();
        }
    }

    private String configModelOverride(Provider p) {
        String v = getConfig().getString("models." + p.id, "");
        return v == null || v.trim().isEmpty() ? p.model : v.trim();
    }

    private String installed() {
        List<String> ids = new ArrayList<String>();
        for (Provider p : providers.values()) {
            if (p.installed) {
                ids.add(p.id);
            }
        }
        return ids.isEmpty() ? "（无）" : String.join(" / ", ids);
    }

    private String defaultProviderId() {
        String configured = getConfig().getString("default-provider", "");
        if (configured != null && !configured.trim().isEmpty()) {
            Provider p = providers.get(configured.trim().toLowerCase(Locale.ROOT));
            if (p != null && p.installed) {
                return p.id;
            }
        }
        for (Provider p : providers.values()) {
            if (p.installed && keys.has(p.id)) {
                return p.id;
            }
        }
        for (Provider p : providers.values()) {
            if (p.installed) {
                return p.id;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 命令

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                help(sender);
                return true;
            case "list":
                list(sender);
                return true;
            case "install":
                install(sender, args.length > 1 ? args[1] : null);
                return true;
            case "key":
                key(sender, args);
                return true;
            case "model":
                model(sender, args);
                return true;
            case "use":
                use(sender, args);
                return true;
            case "base":
                base(sender, args);
                return true;
            case "yes":
                yes(sender);
                return true;
            case "no":
                no(sender);
                return true;
            default:
                break;
        }
        Provider known = providers.get(sub);
        if (known != null && sender instanceof ConsoleCommandSender) {
            install(sender, sub);
            return true;
        }
        ask(sender, join(args, 0));
        return true;
    }

    private void help(CommandSender s) {
        msg(s, "用法：");
        msg(s, "  /AI <问题>              直接问 AI（所有玩家可用）");
        msg(s, "  /ai list                查看已装哪些服务商、Key 状态");
        msg(s, "  /ai install <名字>       安装服务商模块（deepseek / kimi / doubao / gpt）");
        msg(s, "  /ai key <名字>           设置 API Key：游戏内输完这句后，下一条聊天内容会被当作 Key（显示为 ****）");
        msg(s, "  /ai key <名字> <key>     控制台直接给 Key");
        msg(s, "  /ai model <名字> <模型>  换模型名，例如 /ai model gpt gpt-4o-mini");
        msg(s, "  /ai base <名字> <地址>   换接口地址（自建代理时用）");
        msg(s, "  /ai use <名字>           设为默认服务商");
        msg(s, "  /ai yes | /ai no         执行 / 放弃上一条 AI 给出的指令（仅 OP）");
        msg(s, "权限：所有玩家都能问 AI；只有 OP 能让 AI 代执行服务器指令，且只允许白名单内的常用指令。");
        msg(s, "当前默认服务商：" + (defaultProviderId() == null ? "（未安装）" : defaultProviderId()));
    }

    private void list(CommandSender s) {
        msg(s, "已安装 / 可用服务商：");
        for (Provider p : providers.values()) {
            String state;
            if (!p.installed) {
                state = "未安装（后台输入 /ai " + p.id + " 安装）";
            } else if (!keys.has(p.id)) {
                state = "已安装，未填 Key（/ai key " + p.id + "）";
            } else {
                state = "就绪，Key " + KeyStore.mask(keys.get(p.id));
            }
            msg(s, "  - " + p.id + "  " + p.display + "  模型 " + p.model + "  " + state);
        }
    }

    private void install(CommandSender s, String name) {
        if (name == null || name.trim().isEmpty()) {
            msg(s, "要装哪个？可选：" + String.join(" / ", Modules.BUILTIN));
            return;
        }
        String id = name.trim().toLowerCase(Locale.ROOT);
        if (!modules.isBuiltin(id)) {
            msg(s, "没有名为 " + name + " 的服务商，可选：" + String.join(" / ", Modules.BUILTIN));
            return;
        }
        try {
            Provider p = modules.install(id);
            applyOverrides(p);
            providers.put(p.id, p);
            msg(s, "已安装 " + p.display + " 模块：" + modules.jarOf(p.id));
            msg(s, "接口 " + p.baseUrl + "  默认模型 " + p.model);
            msg(s, "下一步填 Key：游戏内 OP 输 /ai key " + p.id + " 后在下一条聊天粘贴；控制台用 /ai key " + p.id + " <你的Key>");
            msg(s, "Key 申请地址：" + p.keyPage);
        } catch (IOException e) {
            msg(s, "安装失败: " + e.getMessage());
        }
    }

    private void key(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "用法：/ai key <名字>（游戏内：下一条聊天内容作为 Key） 或 /ai key <名字> <key>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Provider p = providers.get(id);
        if (p == null || !p.installed) {
            msg(s, "先安装服务商：/ai " + id);
            return;
        }
        if (args.length >= 3) {
            saveKey(s, id, join(args, 2));
            return;
        }
        if (s instanceof Player) {
            pendingKey.put(((Player) s).getUniqueId(), id);
            msg(s, "请在下一条聊天框里粘贴 " + p.display + " 的 API Key（只有你自己看得到，回显是星号，不写日志）");
            msg(s, "输 cancel 取消");
            return;
        }
        msg(s, "控制台请直接用：/ai key " + id + " <你的Key>（控制台命令会写进日志）");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String id = pendingKey.get(player.getUniqueId());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        String text = event.getMessage().trim();
        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("取消")) {
            pendingKey.remove(player.getUniqueId());
            msg(player, "已取消输入 API Key");
            return;
        }
        pendingKey.remove(player.getUniqueId());
        saveKey(player, id, text);
    }

    private void saveKey(CommandSender s, String id, String key) {
        try {
            keys.set(id, key);
            msg(s, "已保存 " + id + " 的 API Key：" + KeyStore.mask(key));
            String def = getConfig().getString("default-provider", "");
            if (def == null || def.trim().isEmpty()) {
                getConfig().set("default-provider", id);
                saveConfig();
                msg(s, "已把默认服务商设为 " + id);
            }
        } catch (IOException e) {
            msg(s, "保存 Key 失败: " + e.getMessage());
        }
    }

    private void model(CommandSender s, String[] args) {
        if (args.length < 3) {
            msg(s, "用法：/ai model <名字> <模型名>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Provider p = providers.get(id);
        if (p == null || !p.installed) {
            msg(s, "先安装服务商：/ai " + id);
            return;
        }
        p.model = join(args, 2);
        getConfig().set("models." + id, p.model);
        saveConfig();
        msg(s, id + " 的模型改为 " + p.model);
    }

    private void use(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "用法：/ai use <名字>，当前默认 " + defaultProviderId());
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Provider p = providers.get(id);
        if (p == null || !p.installed) {
            msg(s, "先安装服务商：/ai " + id);
            return;
        }
        getConfig().set("default-provider", id);
        saveConfig();
        msg(s, "默认服务商改为 " + id + "（" + p.display + "）");
    }

    private void base(CommandSender s, String[] args) {
        if (args.length < 3) {
            msg(s, "用法：/ai base <名字> <接口地址>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Provider p = providers.get(id);
        if (p == null || !p.installed) {
            msg(s, "先安装服务商：/ai " + id);
            return;
        }
        String url = join(args, 2);
        p.baseUrl = url;
        getConfig().set("base-url." + id, url);
        saveConfig();
        msg(s, id + " 的接口地址改为 " + url);
    }

    // ---------------------------------------------------------------- 提问

    private void ask(CommandSender sender, String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            msg(sender, "要问什么？用法：/AI <问题>");
            return;
        }
        String id = defaultProviderId();
        if (id == null) {
            msg(sender, "还没装 AI 服务商。后台执行 /ai deepseek（或 kimi / doubao / gpt）安装对应模块。");
            return;
        }
        Provider p = providers.get(id);
        if (!keys.has(id)) {
            msg(sender, "已安装 " + p.display + "，但还没填 API Key：/ai key " + id);
            return;
        }
        long now = System.currentTimeMillis();
        UUID uid = senderId(sender);
        long cooldown = Math.max(0, getConfig().getInt("cooldown-seconds", 3)) * 1000L;
        Long last = lastAsk.get(uid);
        if (last != null && now - last < cooldown) {
            msg(sender, "慢一点，还有 " + ((cooldown - (now - last)) / 1000 + 1) + " 秒冷却");
            return;
        }
        lastAsk.put(uid, now);
        msg(sender, "正在问 " + p.display + "（模型 " + p.model + "）…");
        final String providerId = id;
        final String prompt = q;
        final UUID senderKey = uid;

        new BukkitRunnable() {
            @Override
            public void run() {
                final String answer;
                try {
                    answer = call(providers.get(providerId), prompt, senderKey);
                } catch (final Exception e) {
                    final String err = e.getMessage();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            msg(sender, "调用失败：" + err);
                        }
                    }.runTask(TravelAI.this);
                    return;
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        deliver(sender, providers.get(providerId), answer, senderKey);
                    }
                }.runTask(TravelAI.this);
            }
        }.runTaskAsynchronously(this);
    }

    private String call(Provider p, String question, UUID senderKey) throws IOException {
        String key = keys.get(p.id);
        if (key.isEmpty()) {
            throw new IOException("没有 API Key");
        }
        String base = p.baseUrl.endsWith("/chat/completions") ? p.baseUrl
                : (p.baseUrl.endsWith("/") ? p.baseUrl + "chat/completions" : p.baseUrl + "/chat/completions");
        String system = getConfig().getString("system-prompt",
                "你是 Minecraft 服务器里的 AI 助手。回答要短、可执行、用中文。"
                        + "如果需要执行服务器指令，只把指令写进 ```mc 代码块，每行一条，不要解释代码块。");
        StringBuilder body = new StringBuilder();
        body.append("{\"model\":").append(Json.quote(p.model));
        body.append(",\"messages\":[");
        body.append("{\"role\":\"system\",\"content\":").append(Json.quote(system)).append('}');
        Deque<String> hist = history.get(senderKey);
        if (hist != null) {
            synchronized (hist) {
                for (String line : hist) {
                    body.append(',').append(line);
                }
            }
        }
        body.append(",{\"role\":\"user\",\"content\":").append(Json.quote(question)).append('}');
        body.append("],\"temperature\":0.3}");
        int timeout = Math.max(5, getConfig().getInt("timeout-seconds", 30));
        Http.Reply reply = Http.postJson(base, key, body.toString(), timeout);
        if (reply.status != 200) {
            String detail = reply.body == null ? "" : reply.body.trim();
            if (detail.length() > 200) {
                detail = detail.substring(0, 200) + "…";
            }
            throw new IOException("HTTP " + reply.status + " " + detail);
        }
        Map<String, Object> obj;
        try {
            obj = Json.parseObject(reply.body);
        } catch (IllegalArgumentException e) {
            throw new IOException("响应不是合法 JSON");
        }
        String content = Json.str(obj, "choices.0.message.content");
        if (content == null) {
            String err = Json.str(obj, "error.message");
            throw new IOException(err == null ? "服务商没有返回内容" : err);
        }
        remember(senderKey, question, content);
        return content;
    }

    private void remember(UUID senderKey, String question, String answer) {
        Deque<String> hist = history.get(senderKey);
        if (hist == null) {
            hist = new ArrayDeque<String>();
            history.put(senderKey, hist);
        }
        synchronized (hist) {
            hist.add("{\"role\":\"user\",\"content\":" + Json.quote(question) + "}");
            hist.add("{\"role\":\"assistant\",\"content\":" + Json.quote(shorten(answer, 800)) + "}");
            int max = Math.max(2, getConfig().getInt("history-pairs", 4) * 2);
            while (hist.size() > max) {
                hist.pollFirst();
            }
        }
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private void deliver(CommandSender sender, Provider p, String answer, UUID senderKey) {
        List<String> commands = Guard.extractCommands(answer);
        String text = Guard.stripCommandBlocks(answer);
        msg(sender, p.display + "：");
        for (String line : wrap(text, 200)) {
            msg(sender, line);
        }
        if (commands.isEmpty()) {
            return;
        }
        if (!canRunCommands(sender)) {
            msg(sender, "AI 给了 " + commands.size() + " 条服务器指令，但你不是 OP，不能执行（只显示文字回答）。");
            return;
        }
        List<String> allowed = new ArrayList<String>();
        for (String c : commands) {
            String reason = Guard.check(c);
            if (reason == null) {
                allowed.add(c);
            } else {
                msg(sender, "  [已拦截] " + c + " —— " + reason);
            }
        }
        if (allowed.isEmpty()) {
            msg(sender, "没有可执行的指令。");
            return;
        }
        pendingRun.put(senderKey, new Pending(allowed, System.currentTimeMillis()
                + Math.max(30, getConfig().getInt("confirm-seconds", 60)) * 1000L));
        msg(sender, "可以执行以下 " + allowed.size() + " 条指令，输入 /ai yes 确认，/ai no 放弃：");
        for (String c : allowed) {
            msg(sender, "  " + c);
        }
    }

    private boolean canRunCommands(CommandSender sender) {
        if (!getConfig().getBoolean("allow-command-execution", true)) {
            return false;
        }
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        return sender.isOp();
    }

    private void yes(CommandSender sender) {
        UUID uid = senderId(sender);
        Pending pending = pendingRun.remove(uid);
        if (pending == null) {
            msg(sender, "没有待执行的 AI 指令。");
            return;
        }
        if (System.currentTimeMillis() > pending.expiresAt) {
            msg(sender, "指令已过期，请重新提问。");
            return;
        }
        if (!canRunCommands(sender)) {
            msg(sender, "你没有执行指令的权限（仅 OP 可用）。");
            return;
        }
        int ok = 0;
        for (String cmd : pending.commands) {
            String reason = Guard.check(cmd);
            if (reason != null) {
                msg(sender, "  [已拦截] " + cmd + " —— " + reason);
                continue;
            }
            boolean done = getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
            msg(sender, (done ? "  [已执行] " : "  [失败] ") + cmd);
            if (done) {
                ok++;
            }
        }
        msg(sender, "执行完成：" + ok + " / " + pending.commands.size() + " 条");
    }

    private void no(CommandSender sender) {
        if (pendingRun.remove(senderId(sender)) != null) {
            msg(sender, "已放弃 AI 给出的指令。");
        } else {
            msg(sender, "没有待执行的 AI 指令。");
        }
    }

    private static UUID senderId(CommandSender sender) {
        return sender instanceof Player ? ((Player) sender).getUniqueId() : CONSOLE_ID;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingKey.remove(id);
        pendingRun.remove(id);
        lastAsk.remove(id);
        history.remove(id);
    }

    private void msg(CommandSender sender, String text) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(PREFIX + text);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            return lines;
        }
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            while (line.length() > width) {
                int cut = line.lastIndexOf(' ', width);
                if (cut <= 0) {
                    cut = width;
                }
                lines.add(line.substring(0, cut));
                line = line.substring(cut).trim();
            }
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : new String[]{"help", "list", "install", "key", "model", "use", "base", "yes", "no"}) {
                if (sub.startsWith(prefix)) {
                    out.add(sub);
                }
            }
            for (String id : providers.keySet()) {
                if (id.startsWith(prefix)) {
                    out.add(id);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("install") || sub.equals("key") || sub.equals("model")
                    || sub.equals("use") || sub.equals("base")) {
                for (String id : providers.keySet()) {
                    if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
    }
}
