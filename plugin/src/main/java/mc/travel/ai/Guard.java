package mc.travel.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 指令执行的安全闸口：只放行常用管理指令，任何危险指令一律拒绝。
 */
public final class Guard {

    /** 允许 AI 代执行的命令（只认首词，忽略命名空间前缀）。 */
    private static final Set<String> ALLOW = new HashSet<String>(Arrays.asList(
            "say", "tell", "msg", "tellraw", "title", "titlemsg", "subtitle", "actionbar",
            "give", "tp", "teleport", "time", "weather", "gamemode", "effect", "summon",
            "difficulty", "gamerule", "clear", "kill", "me", "broadcast", "seed",
            "setworldspawn", "spawnpoint", "playsound", "particle", "list", "xp", "enchant"));

    /** 明确禁止（即使白名单手滑加进来也挡住）。 */
    private static final Set<String> DENY = new HashSet<String>(Arrays.asList(
            "op", "deop", "stop", "restart", "reload", "whitelist", "ban", "ban-ip", "pardon",
            "kick", "execute", "save-all", "save-off", "save-on", "plugins", "pl", "version",
            "bukkit", "minecraft", "spigot", "paper", "sudo", "rl", "reg", "disableplugins"));

    private Guard() {
    }

    /**
     * 校验一条待执行的指令，返回 null 表示可以执行，否则返回拒绝原因。
     */
    public static String check(String line) {
        String raw = line.trim();
        if (raw.isEmpty()) {
            return "空指令";
        }
        if (raw.length() > 256) {
            return "指令过长";
        }
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            return "指令不能包含换行";
        }
        String body = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] words = body.split("\\s+");
        String head = words[0].toLowerCase(Locale.ROOT);
        int colon = head.indexOf(':');
        if (colon >= 0) {
            head = head.substring(colon + 1);
        }
        if (DENY.contains(head)) {
            return "指令 /" + head + " 属于危险指令，AI 不能代为执行";
        }
        if (!ALLOW.contains(head)) {
            return "指令 /" + head + " 不在允许清单内（只允许 say/tp/give/time/weather/gamemode 等常用管理指令）";
        }
        return null;
    }

    /** 从 AI 回答里抽出 ```mc / ```minecraft 代码块中的指令行。 */
    public static List<String> extractCommands(String answer) {
        List<String> out = new ArrayList<String>();
        if (answer == null || answer.isEmpty()) {
            return out;
        }
        int idx = 0;
        while (true) {
            int start = answer.indexOf("```", idx);
            if (start < 0) {
                break;
            }
            int headStart = start + 3;
            int lineEnd = answer.indexOf('\n', headStart);
            if (lineEnd < 0) {
                break;
            }
            String lang = answer.substring(headStart, lineEnd).trim().toLowerCase(Locale.ROOT);
            int blockEnd = answer.indexOf("```", lineEnd);
            if (blockEnd < 0) {
                break;
            }
            if (lang.equals("mc") || lang.equals("minecraft") || lang.equals("command")
                    || lang.equals("cmd")) {
                String body = answer.substring(lineEnd + 1, blockEnd);
                for (String line : body.split("\n")) {
                    String t = line.trim();
                    if (!t.isEmpty()) {
                        out.add(t);
                    }
                }
            }
            idx = blockEnd + 3;
        }
        return out;
    }

    /** 把回答里的指令块去掉，只留给人看的文字。 */
    public static String stripCommandBlocks(String answer) {
        if (answer == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (true) {
            int start = answer.indexOf("```", idx);
            if (start < 0) {
                sb.append(answer.substring(idx));
                break;
            }
            int headStart = start + 3;
            int lineEnd = answer.indexOf('\n', headStart);
            if (lineEnd < 0) {
                sb.append(answer.substring(idx));
                break;
            }
            String lang = answer.substring(headStart, lineEnd).trim().toLowerCase(Locale.ROOT);
            int blockEnd = answer.indexOf("```", lineEnd);
            if (blockEnd < 0) {
                sb.append(answer.substring(idx));
                break;
            }
            if (lang.equals("mc") || lang.equals("minecraft") || lang.equals("command")
                    || lang.equals("cmd")) {
                sb.append(answer, idx, start);
                idx = blockEnd + 3;
            } else {
                sb.append(answer, idx, blockEnd + 3);
                idx = blockEnd + 3;
            }
        }
        return sb.toString().trim();
    }
}
