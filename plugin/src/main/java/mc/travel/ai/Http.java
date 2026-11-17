package mc.travel.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 纯 JDK 的 HTTP 调用：够用来发一条聊天补全请求，不需要任何第三方库。
 */
public final class Http {

    /** 响应体与状态码。 */
    public static final class Reply {
        public final int status;
        public final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private Http() {
    }

    public static Reply postJson(String url, String bearer, String jsonBody, int timeoutSeconds) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(timeoutSeconds * 1000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "TravelAI/1.0 (Minecraft server plugin)");
            if (bearer != null && !bearer.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            OutputStream out = conn.getOutputStream();
            try {
                out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = read(in);
            return new Reply(code, body);
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
