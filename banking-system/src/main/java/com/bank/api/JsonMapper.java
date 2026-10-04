package com.bank.api;

import com.bank.model.Account;
import com.bank.model.Transaction;
import com.bank.model.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Converts domain objects to the JSON shapes the frontend expects. */
final class JsonMapper {

    private JsonMapper() {
    }

    static JSONObject toJson(User user) {
        return new JSONObject()
                .put("id", user.getId())
                .put("username", user.getUsername())
                .put("fullName", user.getFullName());
    }

    static JSONObject toJson(Account account) {
        return new JSONObject()
                .put("id", account.getId())
                .put("userId", account.getUserId())
                .put("type", account.getType().name())
                .put("balance", account.getBalance());
    }

    static JSONArray toJson(List<Account> accounts) {
        JSONArray array = new JSONArray();
        accounts.forEach(a -> array.put(toJson(a)));
        return array;
    }

    static JSONObject toJson(Transaction tx) {
        JSONObject json = new JSONObject()
                .put("id", tx.getId())
                .put("accountId", tx.getAccountId())
                .put("operationType", tx.getOperationType().name())
                .put("amount", tx.getAmount())
                .put("balanceAfter", tx.getBalanceAfter())
                .put("createdAt", tx.getCreatedAt().toString());
        if (tx.getRelatedAccountId() != null) {
            json.put("relatedAccountId", tx.getRelatedAccountId());
        }
        return json;
    }

    static JSONArray toJsonList(List<Transaction> transactions) {
        JSONArray array = new JSONArray();
        transactions.forEach(t -> array.put(toJson(t)));
        return array;
    }

    static JSONObject error(String message) {
        return new JSONObject().put("error", message);
    }
}
