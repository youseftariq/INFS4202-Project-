package uniplace.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Gives the tests an Engine to call. Uses -Dengine.url when it is set; otherwise starts
 * engine/uniplace-engine.jar on a free port and stops it when the JVM exits.
 */
final class EngineServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static String baseUrl;

    private EngineServer() { }

    static synchronized String baseUrl() {
        if (baseUrl == null) {
            String given = System.getProperty("engine.url");
            baseUrl = given != null && !given.isBlank() ? given : start();
        }
        return baseUrl;
    }

    /** Posts a JSON body and returns the status code and the parsed body. */
    static Response post(String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> r = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = r.body().isBlank() ? JSON.nullNode() : JSON.readTree(r.body());
            return new Response(r.statusCode(), json, r.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Engine call failed: " + path, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    record Response(int status, JsonNode json, String raw) { }

    private static String start() {
        String jar = System.getProperty("engine.jar", "../../engine/uniplace-engine.jar");
        File jarFile = Path.of(jar).toAbsolutePath().normalize().toFile();
        if (!jarFile.isFile()) {
            throw new IllegalStateException("Engine jar not found: " + jarFile);
        }
        int port = freePort();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        try {
            Process p = new ProcessBuilder(java, "-jar", jarFile.getPath(), String.valueOf(port))
                    .redirectErrorStream(true)
                    .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "uniplace-engine-test.log"))
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
            String url = "http://127.0.0.1:" + port + "/api/v1";
            waitForHealth(url, p);
            return url;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the Engine", ex);
        }
    }

    private static void waitForHealth(String url, Process p) {
        long until = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < until) {
            if (!p.isAlive()) {
                throw new IllegalStateException("The Engine stopped while starting; see uniplace-engine-test.log in the temp folder");
            }
            try {
                HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url + "/health"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // not up yet
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        throw new IllegalStateException("The Engine did not answer /health within 30 seconds");
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
