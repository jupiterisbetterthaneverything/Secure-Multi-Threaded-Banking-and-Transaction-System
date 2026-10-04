package com.bank;

import com.bank.config.DatabaseConfig;
import com.bank.dao.TransactionDAO;
import com.bank.exception.BankingException;
import com.bank.model.AccountType;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.service.TransferService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evidence for this project.
 *
 * Each test drives real concurrent load through the service layer and asserts
 * that the money adds up exactly. Comment out the FOR UPDATE in
 * AccountDAO#findByIdForUpdate and the first test will start failing with a
 * short balance - that is the lost-update race, made visible.
 */
class ConcurrencyTest {

    private static final AuthService AUTH = new AuthService();
    private static final AccountService ACCOUNTS = new AccountService();
    private static final TransferService TRANSFERS = new TransferService();

    private static final int THREADS = 20;
    private static final int OPS_PER_THREAD = 50;

    private long newAccount(BigDecimal opening) throws BankingException {
        String username = "t_" + UUID.randomUUID().toString().substring(0, 12);
        long userId = AUTH.register(username, "password123", "Test User");
        return ACCOUNTS.openAccount(userId, AccountType.CHECKING, opening);
    }

    @Test
    @Timeout(120)
    @DisplayName("1000 concurrent deposits land exactly, with no lost updates")
    void concurrentDepositsAreExact() throws Exception {
        long accountId = newAccount(BigDecimal.ZERO);
        BigDecimal each = new BigDecimal("1.00");
        int totalOps = THREADS * OPS_PER_THREAD;

        runConcurrently(THREADS, OPS_PER_THREAD, () -> ACCOUNTS.deposit(accountId, each));

        BigDecimal expected = each.multiply(BigDecimal.valueOf(totalOps));
        assertEquals(0, expected.compareTo(ACCOUNTS.getBalance(accountId)),
                "Balance drifted - a read-modify-write was lost");

        // The ledger must agree with the balance, not just the balance alone.
        try (Connection conn = DatabaseConfig.getConnection()) {
            assertEquals(totalOps, new TransactionDAO().countByAccountId(conn, accountId),
                    "Ledger row count does not match the number of successful operations");
        }
    }

    @Test
    @Timeout(120)
    @DisplayName("Interleaved deposits and withdrawals net out to zero drift")
    void mixedOperationsNetOut() throws Exception {
        BigDecimal opening = new BigDecimal("10000.00");
        long accountId = newAccount(opening);
        BigDecimal each = new BigDecimal("5.00");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            boolean depositor = (t % 2 == 0);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        if (depositor) {
                            ACCOUNTS.deposit(accountId, each);
                        } else {
                            ACCOUNTS.withdraw(accountId, each);
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(100, TimeUnit.SECONDS), "Threads did not finish - possible deadlock");
        pool.shutdown();

        assertEquals(0, failures.get(), "Some operations threw unexpectedly");
        // Equal numbers of deposits and withdrawals of the same size.
        assertEquals(0, opening.compareTo(ACCOUNTS.getBalance(accountId)),
                "Deposits and withdrawals did not cancel out");
    }

    @Test
    @Timeout(120)
    @DisplayName("Opposing transfers do not deadlock and conserve total money")
    void opposingTransfersDoNotDeadlock() throws Exception {
        BigDecimal opening = new BigDecimal("5000.00");
        long accountA = newAccount(opening);
        long accountB = newAccount(opening);
        BigDecimal each = new BigDecimal("10.00");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            // Half push A -> B, half push B -> A. Without ordered locking this
            // is the textbook deadlock setup.
            long from = (t % 2 == 0) ? accountA : accountB;
            long to = (t % 2 == 0) ? accountB : accountA;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        TRANSFERS.transfer(from, to, each);
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(100, TimeUnit.SECONDS),
                "Transfers did not complete in time - deadlock or livelock");
        pool.shutdown();

        assertEquals(0, failures.get(), "Some transfers threw unexpectedly");

        BigDecimal total = ACCOUNTS.getBalance(accountA).add(ACCOUNTS.getBalance(accountB));
        assertEquals(0, opening.multiply(BigDecimal.valueOf(2)).compareTo(total),
                "Money was created or destroyed during transfers");
    }

    @Test
    @Timeout(120)
    @DisplayName("Concurrent withdrawals never push the balance below zero")
    void noOverdraftUnderContention() throws Exception {
        // 100 units available, 400 withdrawal attempts of 1 unit each.
        long accountId = newAccount(new BigDecimal("100.00"));
        BigDecimal each = new BigDecimal("1.00");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger succeeded = new AtomicInteger();

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        try {
                            ACCOUNTS.withdraw(accountId, each);
                            succeeded.incrementAndGet();
                        } catch (BankingException expected) {
                            // Insufficient funds once the balance runs out.
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(100, TimeUnit.SECONDS), "Threads did not finish");
        pool.shutdown();

        assertEquals(100, succeeded.get(), "Exactly 100 withdrawals should have succeeded");
        assertEquals(0, BigDecimal.ZERO.compareTo(ACCOUNTS.getBalance(accountId)),
                "Balance should be exactly zero, never negative");
    }

    private void runConcurrently(int threads, int opsPerThread, ThrowingRunnable op)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Exception> errors = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        op.run();
                    }
                } catch (Exception e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(100, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdown();

        synchronized (errors) {
            assertTrue(errors.isEmpty(), "Operations failed: " + errors);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @AfterAll
    static void tearDown() {
        DatabaseConfig.shutdown();
    }
}
