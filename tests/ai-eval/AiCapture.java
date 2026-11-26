import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fake AI provider for reproducing defects of the Portal's AI Assistant on the running system.
 * It records the request the Portal sends (what the prompt contains) and answers with a valid feedback after an
 * optional delay (to test the 10 second time-out, AI-05).
 *
 * Run (Java 17, from the repository root):
 *   java tests/ai-eval/AiCapture.java                 listens on 127.0.0.1:9000, answers at once
 *   java tests/ai-eval/AiCapture.java --delay 15      answers after 15 seconds
 * In the Portal: Coordinator -> Settings -> AI provider "Other OpenAI-compatible endpoint",
 * base URL http://127.0.0.1:9000/v1, model "capture", API key "dummy", Save settings.
 * Every request is printed here and appended to tests/ai-eval/capture-log.txt.
 */
public class AiCapture {
    public static void main(String[] a) throws Exception {
        int port = 9000; int delay = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals("--port")) port = Integer.parseInt(a[++i]);
            if (a[i].equals("--delay")) delay = Integer.parseInt(a[++i]);
        }
        final int delayS = delay;
        Path log = Path.of("tests/ai-eval/capture-log.txt");
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        s.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String time = Instant.now().toString();
            StringBuilder sb = new StringBuilder("==== " + time + "  " + ex.getRequestMethod() + " " + ex.getRequestURI() + "\n");
            try {
                Map<?, ?> req = (Map<?, ?>) new Json(body).parse();
                sb.append("model: ").append(req.get("model")).append("   temperature: ").append(req.get("temperature")).append("\n");
                for (Object mo : (List<?>) req.get("messages")) {
                    Map<?, ?> m = (Map<?, ?>) mo;
                    sb.append("--- message (role ").append(m.get("role")).append(") ---\n").append(m.get("content")).append("\n");
                }
            } catch (Exception e) {
                sb.append("(could not read the request as JSON)\n").append(body).append("\n");
            }
            String out = sb.toString();
            System.out.println(out);
            Files.writeString(log, out + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            try { Thread.sleep(delayS * 1000L); } catch (InterruptedException ignored) { }
            String inner = "{\"score\": 70, \"strengths\": [\"Clear motivation.\", \"Relevant course.\", \"Concrete example.\"], "
                    + "\"gaps\": [\"No measurable result.\"], \"improvements\": [\"Add one measurable result.\"]}";
            byte[] r = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + Json.quote(inner) + "}}]}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, r.length);
            ex.getResponseBody().write(r);
            ex.close();
            System.out.println("(answered after " + delayS + " s)\n");
        });
        s.start();
        System.out.println("Fake AI provider on http://127.0.0.1:" + port + "/v1  (delay " + delay + " s). Stop with Ctrl+C.");
    }

    static class Json {
        final String s; int i;
        Json(String s) { this.s = s; }
        Object parse() { ws(); return value(); }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            ws(); char c = s.charAt(i);
            if (c == '{') { i++; Map<String, Object> m = new LinkedHashMap<>(); ws(); if (s.charAt(i) == '}') { i++; return m; }
                while (true) { ws(); String k = str(); ws(); i++; m.put(k, value()); ws(); if (s.charAt(i++) == '}') return m; } }
            if (c == '[') { i++; List<Object> l = new ArrayList<>(); ws(); if (s.charAt(i) == ']') { i++; return l; }
                while (true) { l.add(value()); ws(); if (s.charAt(i++) == ']') return l; } }
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(st, i));
        }
        String str() {
            StringBuilder b = new StringBuilder(); i++;
            while (s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\') { char d = s.charAt(i++);
                    switch (d) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(d); } }
                else b.append(c);
            }
            i++; return b.toString();
        }
        static String quote(String t) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : t.toCharArray()) {
                switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
                    default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
            return b.append('"').toString();
        }
    }
}
