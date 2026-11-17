package mc.travel.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析 / 生成：只够处理聊天补全的请求与响应，不引入任何第三方库。
 */
public final class Json {

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos < p.src.length()) {
            throw new IllegalArgumentException("JSON 尾部有多余内容: " + p.src.substring(p.pos));
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("期望 JSON 对象");
        }
        return (Map<String, Object>) v;
    }

    /** 按 "a.b.c" 逐层取字符串，取不到返回 null。 */
    public static String str(Map<String, Object> obj, String path) {
        Object cur = obj;
        for (String key : path.split("\\.")) {
            if (cur instanceof Map) {
                cur = ((Map<?, ?>) cur).get(key);
            } else if (cur instanceof List) {
                List<?> list = (List<?>) cur;
                int i;
                try {
                    i = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (i >= list.size()) {
                    return null;
                }
                cur = list.get(i);
            } else {
                return null;
            }
        }
        if (cur == null || cur instanceof Map || cur instanceof List) {
            return null;
        }
        return String.valueOf(cur);
    }

    private Object value() {
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;
        skipWs();
        if (src.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            if (src.charAt(pos) != ':') {
                throw new IllegalArgumentException("JSON 对象缺少冒号 @" + pos);
            }
            pos++;
            skipWs();
            map.put(key, value());
            skipWs();
            char c = src.charAt(pos);
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return map;
            }
            throw new IllegalArgumentException("JSON 对象缺少逗号 @" + pos);
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        pos++;
        skipWs();
        if (src.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(value());
            skipWs();
            char c = src.charAt(pos);
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return list;
            }
            throw new IllegalArgumentException("JSON 数组缺少逗号 @" + pos);
        }
    }

    private String string() {
        if (src.charAt(pos) != '"') {
            throw new IllegalArgumentException("JSON 字符串应以引号开头 @" + pos);
        }
        pos++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = src.charAt(pos++);
                switch (e) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        sb.append(e);
                }
                continue;
            }
            sb.append(c);
        }
    }

    private Object number() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        String num = src.substring(start, pos);
        if (num.isEmpty()) {
            throw new IllegalArgumentException("JSON 数字非法 @" + start);
        }
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
            return Double.valueOf(num);
        }
        return Long.valueOf(num);
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) {
            throw new IllegalArgumentException("JSON 字面量错误 @" + pos);
        }
        pos += word.length();
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    public static String quote(String raw) {
        if (raw == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
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
        sb.append('"');
        return sb.toString();
    }
}
