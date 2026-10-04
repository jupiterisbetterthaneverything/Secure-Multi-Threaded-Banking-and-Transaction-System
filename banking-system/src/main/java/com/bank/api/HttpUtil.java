package com.bank.api;

import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Small wrapper around HttpExchange so handlers don't repeat this plumbing. */
final class HttpUtil {

    private HttpUtil() {
    }

    static JSONObject readJsonBody(HttpExchange exchange) throws IOException {
        try (var in = exchange.getRequestBody()) {
            return new JSONObject(new JSONTokener(in));
        }
    }

    static void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, JsonMapper.error(message));
    }

    static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "Method not allowed");
    }
}
