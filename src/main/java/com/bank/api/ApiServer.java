package com.bank.api;

import com.bank.config.DatabaseConfig;
import com.bank.exception.AuthenticationException;
import com.bank.exception.BankingException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.User;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransferService;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Thin REST layer over the existing service classes.
 *
 * Deliberately built on the JDK's own HttpServer rather than a framework:
 * every route here is a direct call into AccountService / TransferService /
 * AuthService, so the concurrency and transaction logic underneath is
 * unchanged - this file only translates HTTP <-> those calls.
 *
 * There is no session/token layer. Login returns the user id and the
 * frontend keeps it client-side. That is a known simplification - see the
 * README "Known limitations" section.
 */
public class ApiServer {

    private static final AuthService AUTH = new AuthService();
    private static final AccountService ACCOUNTS = new AccountService();
    private static final TransferService TRANSFERS = new TransferService();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/register", ApiServer::handleRegister);
        server.createContext("/api/login", ApiServer::handleLogin);
        server.createContext("/api/accounts", ApiServer::handleAccounts);
        server.createContext("/api/deposit", ApiServer::handleDeposit);
        server.createContext("/api/withdraw", ApiServer::handleWithdraw);
        server.createContext("/api/transfer", ApiServer::handleTransfer);
        server.createContext("/api/history", ApiServer::handleHistory);
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("Banking API + frontend running on http://localhost:" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(DatabaseConfig::shutdown));
    }

    private static void handleRegister(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        try {
            JSONObject body = HttpUtil.readJsonBody(exchange);
            long userId = AUTH.register(
                    body.getString("username"),
                    body.getString("password"),
                    body.getString("fullName"));
            HttpUtil.sendJson(exchange, 201, new JSONObject().put("userId", userId));
        } catch (BankingException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private static void handleLogin(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        try {
            JSONObject body = HttpUtil.readJsonBody(exchange);
            User user = AUTH.login(body.getString("username"), body.getString("password"));
            HttpUtil.sendJson(exchange, 200, JsonMapper.toJson(user));
        } catch (AuthenticationException e) {
            HttpUtil.sendError(exchange, 401, e.getMessage());
        } catch (BankingException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    /** GET /api/accounts?userId=1  or  POST /api/accounts {userId, type, openingBalance} */
    private static void handleAccounts(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            if ("GET".equals(exchange.getRequestMethod())) {
                long userId = Long.parseLong(queryParam(exchange, "userId"));
                List<Account> accounts = ACCOUNTS.listAccounts(userId);
                HttpUtil.sendJson(exchange, 200, JsonMapper.toJson(accounts));
            } else if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject body = HttpUtil.readJsonBody(exchange);
                long accountId = ACCOUNTS.openAccount(
                        body.getLong("userId"),
                        AccountType.valueOf(body.getString("type").toUpperCase()),
                        new BigDecimal(body.get("openingBalance").toString()));
                HttpUtil.sendJson(exchange, 201, new JSONObject().put("accountId", accountId));
            } else {
                HttpUtil.sendMethodNotAllowed(exchange);
            }
        } catch (BankingException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private static void handleDeposit(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        handleMutation(exchange, (accountId, amount) -> ACCOUNTS.deposit(accountId, amount));
    }

    private static void handleWithdraw(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        handleMutation(exchange, (accountId, amount) -> ACCOUNTS.withdraw(accountId, amount));
    }

    @FunctionalInterface
    private interface BalanceMutation {
        BigDecimal apply(long accountId, BigDecimal amount) throws BankingException;
    }

    private static void handleMutation(com.sun.net.httpserver.HttpExchange exchange, BalanceMutation mutation)
            throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        try {
            JSONObject body = HttpUtil.readJsonBody(exchange);
            long accountId = body.getLong("accountId");
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            BigDecimal newBalance = mutation.apply(accountId, amount);
            HttpUtil.sendJson(exchange, 200, new JSONObject().put("balance", newBalance));
        } catch (BankingException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private static void handleTransfer(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        try {
            JSONObject body = HttpUtil.readJsonBody(exchange);
            TRANSFERS.transfer(
                    body.getLong("fromAccountId"),
                    body.getLong("toAccountId"),
                    new BigDecimal(body.get("amount").toString()));
            HttpUtil.sendJson(exchange, 200, new JSONObject().put("status", "ok"));
        } catch (BankingException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private static void handleHistory(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        try {
            long accountId = Long.parseLong(queryParam(exchange, "accountId"));
            String limitParam = queryParam(exchange, "limit");
            int limit = limitParam == null ? 20 : Integer.parseInt(limitParam);

            List<Transaction> history = ACCOUNTS.getHistory(accountId, limit);
            HttpUtil.sendJson(exchange, 200, JsonMapper.toJsonList(history));
        } catch (BankingException e) {
            HttpUtil.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 400, "Invalid request: " + e.getMessage());
        }
    }

    private static String queryParam(com.sun.net.httpserver.HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
}
