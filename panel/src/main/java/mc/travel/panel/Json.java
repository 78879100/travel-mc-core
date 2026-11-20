package mc.travel.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 解析器（只用于解析 Modrinth 返回），零依赖。 */
final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        return v;
    }

    /** 按 a.b.0.c 取值，取不到返回 null。 */
    @SuppressWarnings("unchecked")
    static Object at(Object root, String path) {
        Object cur = root;
        for (String key : path.split("\\\".")) {
            if (cur == null) {
                return null;
            }
            if (cur instanceof Map) {
                cur = ((Map<String, Object>) cur).get(key);
            } else if (cur instanceof List) {
                try {
                    int idx = Integer.parseInt(key);
                    List<Object> list = (List<Object>) cur;
                    cur = idx >= 0 && idx < list.size() ? list.get(idx) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return cur;
    }

    static String str(Object root, String path, String def) {
        Object v = at(root, path);
        return v == null ? def : String.valueOf(v);
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
    }

    private Object value() {
        ws();
        if (i >= s.length()) {
            return null;
        }
        char c = s.charAt(i);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (c == 't' && s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (c == 'f' && s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (c == 'n' && s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        i++; // {
        ws();
        if (i < s.length() && s.charAt(i) == '}') {
            i++;
            return map;
        }
        while (i < s.length()) {
            ws();
            String key = string();
            ws();
            if (i < s.length() && s.charAt(i) == ':') {
                i++;
            }
            Object v = value();
            map.put(key, v);
            ws();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                break;
            }
            break;
        }
        return map;
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        i++; // [
        ws();
        if (i < s.length() && s.charAt(i) == ']') {
            i++;
            return list;
        }
        while (i < s.length()) {
            list.add(value());
            ws();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                break;
            }
            break;
        }
        return list;
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        if (i < s.length() && s.charAt(i) == '"') {
            i++;
        }
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                if (e == 'n') {
                    sb.append('\n');
                } else if (e == 't') {
                    sb.append('\t');
                } else if (e == 'r') {
                    sb.append('\r');
                } else if (e == 'u' && i + 4 <= s.length()) {
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                } else {
                    sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                i++;
            } else {
                break;
            }
        }
        if (start == i) {
            i++;
            return null;
        }
        String num = s.substring(start, i);
        try {
            return Double.valueOf(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
