package com.dafeiyu.hudleft;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** cccodroid 网关的只读接口 + 回答请求。令牌从共享目录读（ccc 那边拷过来的）。 */
final class Gateway {

    static final String BASE = "http://127.0.0.1:8791";
    private static final String TOKEN_FILE =
            "/storage/emulated/0/Android/media/com.dafeiyu.hudleft/gateway-token";

    private static String token() {
        try (FileInputStream in = new FileInputStream(TOKEN_FILE)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    static JSONObject get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(2000);
        c.setReadTimeout(3000);
        c.setRequestProperty("X-CC-Token", token());
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            return new JSONObject(body);
        } catch (Exception e) {
            throw e;
        } finally {
            c.disconnect();
        }
    }

    static boolean answerQuestion(String id, String runId, String question, String choice) {
        try {
            JSONObject answers = new JSONObject();
            answers.put(question, choice);
            JSONObject body = new JSONObject();
            body.put("id", id);
            if (runId != null) body.put("runId", runId);
            body.put("behavior", "allow");
            body.put("answers", answers);
            return post(body);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean post(JSONObject body) {
        return post("/api/perm/answer", body);
    }

    private static boolean post(String path, JSONObject body) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-CC-Token", token());
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean answer(String id, String runId, boolean allow) {
        return answer(id, runId, allow, false);
    }

    static boolean answer(String id, String runId, boolean allow, boolean always) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/api/perm/answer").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("X-CC-Token", token());
            JSONObject body = new JSONObject();
            body.put("id", id);
            if (runId != null) body.put("runId", runId);
            body.put("behavior", allow ? "allow" : "deny");
            if (always) body.put("always", true);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
