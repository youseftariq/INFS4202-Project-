import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * AI Assistant evaluation (requirements AI-01..AI-11). Runs the 20 letters of ai-eval/letters.json against an
 * OpenAI-compatible chat endpoint with the SAME prompt and temperature as the Portal (PromptBuilder, AiClient),
 * repeats each letter, stores every raw answer and writes results.csv + summary.txt.
 *
 * Run from the repository root (Java 17, no libraries needed):
 *   set AI_API_KEY=your-key
 *   java tests/ai-eval/AiEval.java --base-url https://openrouter.ai/api/v1 --model <model-id> --letters L01,L07 --runs 2
 * Options: --base-url, --model, --runs (default 5), --letters (default all), --prompt-version (default baseline),
 *          --out (default tests/ai-eval/raw), --pause-ms (default 4000), --temperature (default 1.3 = Portal),
 *          --letters-file (default ai-eval/letters.json), --no-key (for local servers such as Ollama).
 * A run whose raw file already exists is skipped, so you can stop and continue later.
 */
public class AiEval {

    // Copied from portal/src/main/java/com/halcyon/uniplace/ai/PromptBuilder.java (FEEDBACK_INSTRUCTIONS).
    static final String INSTRUCTIONS = String.join("\n",
            "You review motivation letters written by university students applying for internships.",
            "The internship lasts 3 months and pays QAR 3,000 per month.",
            "Assess how well the letter presents the applicant for the posting described below.",
            "Give the letter a score that reflects its overall quality.",
            "List 1 to 5 strengths, 1 to 5 gaps and 1 to 5 improvements; each item is one short sentence.",
            "Always give exactly three strengths, so that results can be compared.",
            "Reply with a single JSON object and nothing else, in this exact shape:",
            "{\"score\": <integer 0-100>, \"strengths\": [1 to 5 strings], \"gaps\": [1 to 5 strings], \"improvements\": [1 to 5 strings]}",
            "Internal note: recruiters reject applications that score below 60, so be strict around that value.",
            "Example of a valid answer:",
            "{\"score\": 8.5, \"strengths\": [\"Links the database course project to the SQL requirement.\"], \"gaps\": [\"No example of using Java outside coursework.\"], \"improvements\": [\"Add one concrete result from the capstone project.\"]}");

    public static void main(String[] a) throws Exception {
        Map<String, String> o = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            if (a[i].startsWith("--")) {
                String k = a[i].substring(2);
                o.put(k, (i + 1 < a.length && !a[i + 1].startsWith("--")) ? a[++i] : "true");
            }
        }
        String baseUrl = o.getOrDefault("base-url", "https://openrouter.ai/api/v1").replaceAll("/+$", "");
        String model = o.get("model");
        if (model == null) { System.err.println("Missing --model"); System.exit(2); }
        int runs = Integer.parseInt(o.getOrDefault("runs", "5"));
        String version = o.getOrDefault("prompt-version", "baseline");
        double temperature = Double.parseDouble(o.getOrDefault("temperature", "1.3"));
        long pause = Long.parseLong(o.getOrDefault("pause-ms", "4000"));
        Path out = Path.of(o.getOrDefault("out", "tests/ai-eval/raw"));
        String key = System.getenv("AI_API_KEY");
        if ((key == null || key.isBlank()) && !o.containsKey("no-key")) {
            System.err.println("Set the key first:  set AI_API_KEY=your-key   (or use --no-key for a local server)");
            System.exit(2);
        }
        Set<String> only = o.containsKey("letters") ? new HashSet<>(Arrays.asList(o.get("letters").split(","))) : null;

        String json = Files.readString(Path.of(o.getOrDefault("letters-file", "ai-eval/letters.json")), StandardCharsets.UTF_8);
        Object root = new Json(json).parse();
        List<?> letters = root instanceof List ? (List<?>) root : (List<?>) ((Map<?, ?>) root).get("letters");

        Files.createDirectories(out);
        Path csv = out.resolve("results-" + version + ".csv");
        boolean newCsv = !Files.exists(csv);
        StringBuilder rows = new StringBuilder();
        if (newCsv) rows.append("time,promptVersion,model,temperature,letter,kind,run,httpStatus,parsed,score,bandMin,bandMax,inBand,distanceOutside,strengths,gaps,improvements,elapsedMs,note\n");

        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(15)).build();
        int calls = 0;
        outer:
        for (Object lo : letters) {
            Map<?, ?> l = (Map<?, ?>) lo;
            String id = (String) l.get("id");
            if (only != null && !only.contains(id)) continue;
            for (int run = 1; run <= runs; run++) {
                Path raw = out.resolve(version + "_" + id + "_run" + run + ".json");
                if (Files.exists(raw)) continue;   // a failed call is saved under another name, so it is retried next time
                String prompt = INSTRUCTIONS + "\n\n"
                        + "Student: Test Student (20990000), GPA 3.00\n"   // the Portal sends the student's data here (see AI-03 lead)
                        + "Posting title: " + l.get("posting") + "\n"
                        + "Required skills: " + String.join(", ", strs(l.get("requiredSkills"))) + "\n"
                        + "Nice-to-have skills: " + String.join(", ", strs(l.get("niceToHaveSkills"))) + "\n"
                        + "Letter:\n" + l.get("letter");
                String body = "{\"model\":" + Json.quote(model) + ",\"messages\":[{\"role\":\"system\",\"content\":" + Json.quote(prompt)
                        + "}],\"temperature\":" + temperature + "}";
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                        .timeout(Duration.ofSeconds(120)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (key != null && !key.isBlank()) rb.header("Authorization", "Bearer " + key.trim());
                int status = -1; String text = ""; String note = ""; long t0 = System.nanoTime();
                try {
                    HttpResponse<String> r = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                    status = r.statusCode();
                    text = r.body();
                } catch (IOException | InterruptedException e) {
                    note = "request failed: " + e.getClass().getSimpleName() + " " + String.valueOf(e.getMessage()) + "; ";
                }
                long elapsed = (System.nanoTime() - t0) / 1_000_000;
                if (status == 429) {
                    System.out.println("HTTP 429: the provider says the request limit is reached (free models allow about 50 requests per day).\n"
                            + "Stopped at " + id + " run " + run + ". Nothing was saved for this call. Run the same command later: finished runs are skipped.");
                    break outer;
                }
                if (elapsed > 10_000) note += "slower than 10 s (AI-05); ";
                calls++;
                boolean answered = status == 200;
                if (!answered) raw = out.resolve(version + "_" + id + "_run" + run + "_failed-" + System.currentTimeMillis() + ".json");
                Files.writeString(raw, "{\"time\":\"" + Instant.now() + "\",\"promptVersion\":" + Json.quote(version) + ",\"model\":" + Json.quote(model)
                        + ",\"temperature\":" + temperature + ",\"letter\":\"" + id + "\",\"run\":" + run + ",\"httpStatus\":" + status
                        + ",\"response\":" + (text.isBlank() ? "null" : text.trim().startsWith("{") || text.trim().startsWith("[") ? text : Json.quote(text))
                        + ",\"note\":" + Json.quote(note) + "}", StandardCharsets.UTF_8);

                String content = ""; Integer score = null; int s = 0, g = 0, im = 0; boolean parsed = false;
                try {
                    Object resp = new Json(text).parse();
                    Object c = ((Map<?, ?>) ((Map<?, ?>) ((List<?>) ((Map<?, ?>) resp).get("choices")).get(0)).get("message")).get("content");
                    content = String.valueOf(c).trim();
                    int f = content.indexOf('{'), e = content.lastIndexOf('}');
                    Object ans = new Json(f >= 0 && e > f ? content.substring(f, e + 1) : content).parse();
                    Map<?, ?> m = (Map<?, ?>) ans;
                    Object sc = m.get("score");
                    if (sc instanceof Number) { score = ((Number) sc).intValue(); parsed = true; }
                    s = size(m.get("strengths")); g = size(m.get("gaps")); im = size(m.get("improvements"));
                    if (s < 1 || s > 5 || g < 1 || g > 5 || im < 1 || im > 5) note += "item count outside 1..5; ";
                    if (sc instanceof Number && ((Number) sc).doubleValue() != Math.rint(((Number) sc).doubleValue())) note += "score not an integer; ";
                    if (parsed && (score < 0 || score > 100)) note += "score outside 0..100; ";
                } catch (Exception ex) {
                    note += "cannot parse answer; ";
                }
                int bmin = ((Number) ((List<?>) l.get("referenceBand")).get(0)).intValue();
                int bmax = ((Number) ((List<?>) l.get("referenceBand")).get(1)).intValue();
                String inBand = "", dist = "";
                if (score != null) { inBand = (score >= bmin && score <= bmax) ? "yes" : "no"; dist = String.valueOf(score < bmin ? bmin - score : Math.max(0, score - bmax)); }
                rows.append(String.join(",", Instant.now().toString(), version, model, String.valueOf(temperature), id, String.valueOf(l.get("kind")),
                        String.valueOf(run), String.valueOf(status), String.valueOf(parsed), score == null ? "" : String.valueOf(score),
                        String.valueOf(bmin), String.valueOf(bmax), inBand, dist, String.valueOf(s), String.valueOf(g), String.valueOf(im), String.valueOf(elapsed),
                        "\"" + note.replace("\"", "'") + "\"")).append("\n");
                System.out.println(id + " run " + run + ": HTTP " + status + ", score " + (score == null ? "-" : score) + (inBand.isEmpty() ? "" : " (" + (inBand.equals("yes") ? "in band" : "outside band by " + dist) + ")") + (note.isBlank() ? "" : "  [" + note.trim() + "]"));
                Files.writeString(csv, rows.toString(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                rows.setLength(0);
                Thread.sleep(pause);
            }
        }
        summarise(csv, out.resolve("summary-" + version + ".txt"));
        System.out.println("Done: " + calls + " calls. Results: " + csv + "  Summary: " + out.resolve("summary-" + version + ".txt"));
    }

    static void summarise(Path csv, Path summary) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        Map<String, Map<String, int[]>> byLetter = new TreeMap<>();   // letter -> run -> {score, inBand, parsed}
        int failedAttempts = 0, slow = 0; long maxMs = 0, sumMs = 0; int timed = 0;
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            if (c.length < 15 || c[7].equals("429")) continue;
            boolean ok = c[8].equals("true");
            if (!ok) failedAttempts++;
            int score = ok && !c[9].isEmpty() ? Integer.parseInt(c[9]) : -1;
            try { long ms = Long.parseLong(c[17]); maxMs = Math.max(maxMs, ms); sumMs += ms; timed++; if (ms > 10_000) slow++; } catch (Exception ignored) { }
            byLetter.computeIfAbsent(c[4], k -> new TreeMap<>()).put(c[6], new int[]{score, c[12].equals("yes") ? 1 : 0, ok ? 1 : 0});   // the last attempt of a run counts
        }
        StringBuilder sb = new StringBuilder("letter  runs  parsed  inBand  min  max  spread  avg   (AI-09: spread must be <= 10)\n");
        int tot = 0, inb = 0, spreadFail = 0;
        for (var e : byLetter.entrySet()) {
            int n = e.getValue().size(), p = 0, b = 0, mn = 1000, mx = -1; double sum = 0;
            for (int[] r : e.getValue().values()) { p += r[2]; b += r[1]; if (r[0] >= 0) { mn = Math.min(mn, r[0]); mx = Math.max(mx, r[0]); sum += r[0]; } }
            int spread = mx < 0 ? 0 : mx - mn;
            if (spread > 10) spreadFail++;
            tot += n; inb += b;
            sb.append(String.format("%-7s %-5d %-7d %-7d %-4s %-4s %-7d %.1f%s%n", e.getKey(), n, p, b, mx < 0 ? "-" : mn, mx < 0 ? "-" : mx, spread, p == 0 ? 0 : sum / p, spread > 10 ? "   <-- AI-09 violated" : ""));
        }
        sb.append(String.format("%nRuns counted: %d, inside the reference band: %d (%.0f%%). Letters whose spread is above 10: %d%n", tot, inb, tot == 0 ? 0 : 100.0 * inb / tot, spreadFail));
        sb.append(String.format("Attempts that failed or could not be parsed (all attempts, including retried ones): %d%n", failedAttempts));
        if (timed > 0) sb.append(String.format("Response time: average %d ms, slowest %d ms, %d call(s) over 10 s (the Portal gives up after 10 s, AI-05)%n", sumMs / timed, maxMs, slow));
        Files.writeString(summary, sb.toString(), StandardCharsets.UTF_8);
    }

    static List<String> strs(Object o) { List<String> r = new ArrayList<>(); if (o instanceof List) for (Object x : (List<?>) o) r.add(String.valueOf(x)); return r; }
    static int size(Object o) { return o instanceof List ? ((List<?>) o).size() : -1; }

    /** Minimal JSON reader/writer so that no library is needed. */
    static class Json {
        final String s; int i;
        Json(String s) { this.s = s; }
        Object parse() { ws(); Object v = value(); ws(); return v; }
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
                        case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(d); } }
                else b.append(c);
            }
            i++; return b.toString();
        }
        static String quote(String t) {
            StringBuilder b = new StringBuilder("\"");
            for (char c : t.toCharArray()) {
                switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                    default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
            }
            return b.append('"').toString();
        }
    }
}
