package com.bank.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Serves the built-in frontend (src/main/resources/public) from the classpath,
 * so the API and the UI run from the same server on the same port - no CORS
 * configuration needed.
 */
class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "application/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "ico", "image/x-icon"
    );

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        // Prevent escaping the public/ resource root via "..".
        String safePath = path.replace("..", "");
        String resourcePath = "public" + safePath;

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                String notFound = "404 - not found: " + safePath;
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes());
                }
                return;
            }

            byte[] bytes = in.readAllBytes();
            String extension = safePath.contains(".")
                    ? safePath.substring(safePath.lastIndexOf('.') + 1)
                    : "";
            String contentType = CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
