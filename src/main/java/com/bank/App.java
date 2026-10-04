package com.bank;

import com.bank.config.DatabaseConfig;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.model.User;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransferService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Smoke test you can run by hand: mvn compile exec:java
 *
 * It walks the full happy path so you can confirm the database, pool and
 * services are wired up before layering a REST API or GUI on top.
 */
public class App {

    public static void main(String[] args) {
        AuthService auth = new AuthService();
        AccountService accounts = new AccountService();
        TransferService transfers = new TransferService();

        try {
            String username = "demo_" + UUID.randomUUID().toString().substring(0, 8);

            long userId = auth.register(username, "password123", "Demo User");
            User user = auth.login(username, "password123");
            System.out.println("Logged in as " + user.getFullName() + " (id " + userId + ")");

            long checking = accounts.openAccount(userId, AccountType.CHECKING, new BigDecimal("1000.00"));
            long savings = accounts.openAccount(userId, AccountType.SAVINGS, new BigDecimal("500.00"));
            System.out.println("Opened checking=" + checking + " savings=" + savings);

            System.out.println("After deposit:  " + accounts.deposit(checking, new BigDecimal("250.00")));
            System.out.println("After withdraw: " + accounts.withdraw(checking, new BigDecimal("100.00")));

            transfers.transfer(checking, savings, new BigDecimal("300.00"));
            System.out.println("Checking now: " + accounts.getBalance(checking));
            System.out.println("Savings now:  " + accounts.getBalance(savings));

            System.out.println("\nRecent activity on checking:");
            List<Transaction> history = accounts.getHistory(checking, 10);
            history.forEach(tx -> System.out.println("  " + tx));

            // Expected to fail: proves the guard works and nothing is written.
            try {
                accounts.withdraw(savings, new BigDecimal("999999.00"));
                System.out.println("PROBLEM: overdraft was allowed");
            } catch (Exception expected) {
                System.out.println("\nOverdraft correctly rejected: " + expected.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DatabaseConfig.shutdown();
        }
    }
}
